package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.attendance.AttendanceDtos.ScanResponse;
import app.gym.attendance.AttendanceService;
import app.gym.attendance.ScanResult;
import app.gym.auth.StaffPermissions;
import app.gym.common.Crypto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QrAttendanceIT extends app.gym.support.AbstractIT {

    @Autowired AttendanceService attendanceService;

    private long member;
    private Who desk;

    private void setUp() throws Exception {
        member = createMember("Qr Person", "9500000001");
        long plan = createPlan("Monthly", 30, 150000);
        assign(member, plan, "2026-10-01"); // valid through 2026-10-30
        desk = createStaff("desk", StaffPermissions.none());
    }

    private String token() throws Exception {
        return read(get("/api/v1/members/" + member + "/qr", desk).andExpect(status().isOk()), "$.token");
    }

    private String scan(String token) throws Exception {
        return read(post("/api/v1/attendance/scan", map("token", token), desk).andExpect(status().isOk()), "$.result");
    }

    private long records() {
        return jdbc.sql("SELECT count(*) FROM attendance_record").query(Long.class).single();
    }

    @Test
    void validScanRecordsAttendanceWithStaffIdAndSource() throws Exception {
        setUp();
        String payload = read(get("/api/v1/members/" + member + "/qr", desk), "$.payload");
        assertThat(payload).startsWith("GYMPASS:");
        post("/api/v1/attendance/scan", map("token", payload), desk)
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("CHECKED_IN"))
                .andExpect(jsonPath("$.memberCode").value("M00001")).andExpect(jsonPath("$.membershipEndDate").value("2026-10-30"));
        var row = jdbc.sql("SELECT source, recorded_by, qr_pass_id, checkin_date::text AS d FROM attendance_record")
                .query((rs, i) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getObject(3), rs.getString(4)}).single();
        assertThat(row[0]).isEqualTo("QR");
        assertThat(row[1]).isEqualTo(desk.id());
        assertThat(row[2]).isNotNull();
        assertThat(row[3]).isEqualTo("2026-10-15");
    }

    @Test
    void rescanWithinWindowIsADuplicateAndWritesNothing() throws Exception {
        setUp();
        String t = token();
        assertThat(scan(t)).isEqualTo("CHECKED_IN");
        assertThat(scan(t)).isEqualTo("DUPLICATE_RECENT");
        clock.advance(Duration.ofMinutes(9));
        assertThat(scan(t)).isEqualTo("DUPLICATE_RECENT");
        assertThat(records()).isEqualTo(1);
        clock.advance(Duration.ofMinutes(2)); // 11 minutes after the first check-in
        assertThat(scan(t)).isEqualTo("CHECKED_IN");
        assertThat(records()).isEqualTo(2);
    }

    @Test
    void duplicateWindowComesFromSettings() throws Exception {
        setUp();
        jdbc.sql("UPDATE gym_settings SET duplicate_window_minutes = 0").update();
        String t = token();
        assertThat(scan(t)).isEqualTo("CHECKED_IN");
        assertThat(scan(t)).isEqualTo("CHECKED_IN");
        assertThat(records()).isEqualTo(2);
    }

    @Test
    void unknownMalformedAndOldTokensAreRejected() throws Exception {
        setUp();
        assertThat(scan(Crypto.randomToken(32))).isEqualTo("INVALID_TOKEN");
        assertThat(scan("GYMPASS:" + Crypto.randomToken(32))).isEqualTo("INVALID_TOKEN");
        assertThat(scan("x")).isEqualTo("INVALID_TOKEN");
        assertThat(scan("GYMPASS:")).isEqualTo("INVALID_TOKEN");
        assertThat(records()).isZero();
    }

    @Test
    void reissueInvalidatesTheOldTokenImmediately() throws Exception {
        setUp();
        String oldToken = token();
        long passId = ((Number) read(get("/api/v1/members/" + member + "/qr", desk), "$.passId")).longValue();
        String newToken = read(post("/api/v1/qr/" + passId + "/reissue", null, desk).andExpect(status().isOk()), "$.token");
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(scan(oldToken)).isEqualTo("PASS_REVOKED");
        assertThat(scan(newToken)).isEqualTo("CHECKED_IN");
        assertThat(jdbc.sql("SELECT count(*) FROM member_qr_pass WHERE status = 'ACTIVE'").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM member_qr_pass WHERE status = 'REVOKED' AND token_enc IS NULL").query(Long.class).single()).isEqualTo(1);
        get("/api/v1/audit?entityType=MEMBER", owner).andExpect(jsonPath("$.items[?(@.action=='QR_REISSUED')]").isNotEmpty());
    }

    @Test
    void revokeStopsThePassUntilANewOneIsIssued() throws Exception {
        setUp();
        String t = token();
        long passId = ((Number) read(get("/api/v1/members/" + member + "/qr", desk), "$.passId")).longValue();
        post("/api/v1/qr/" + passId + "/revoke", null, desk).andExpect(status().isNoContent());
        assertThat(scan(t)).isEqualTo("PASS_REVOKED");
        String fresh = token(); // viewing issues a new pass when none is active
        assertThat(fresh).isNotEqualTo(t);
        assertThat(scan(fresh)).isEqualTo("CHECKED_IN");
    }

    @Test
    void expiredMembershipReturnsExpiredAndRecordsNothing() throws Exception {
        setUp();
        String t = token();
        clock.set(Instant.parse("2026-10-30T04:00:00Z"));
        assertThat(scan(t)).isEqualTo("CHECKED_IN");          // last day is inclusive
        clock.set(Instant.parse("2026-10-31T04:00:00Z"));
        assertThat(scan(t)).isEqualTo("EXPIRED");
        assertThat(records()).isEqualTo(1);
    }

    @Test
    void otherOutcomes() throws Exception {
        setUp();
        long noPlanMember = createMember("No Plan", "9500000002");
        String noPlanToken = read(get("/api/v1/members/" + noPlanMember + "/qr", desk), "$.token");
        assertThat(scan(noPlanToken)).isEqualTo("NO_ACTIVE_MEMBERSHIP");

        long ms = jdbc.sql("SELECT id FROM membership").query(Long.class).single();
        post("/api/v1/memberships/" + ms + "/hold", map("reason", "away"), owner).andExpect(status().isOk());
        assertThat(scan(token())).isEqualTo("ON_HOLD");
        post("/api/v1/memberships/" + ms + "/resume", null, owner);

        String t = token();
        post("/api/v1/members/" + member + "/deactivate", map("reason", "left"), owner).andExpect(status().isOk());
        assertThat(scan(t)).isEqualTo("MEMBER_INACTIVE");
        assertThat(records()).isZero();
    }

    @Test
    void manualCheckInUsesTheSameRules() throws Exception {
        setUp();
        post("/api/v1/attendance/manual", map("memberId", member, "reason", "MEMBER_NO_QR"), desk)
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("CHECKED_IN"));
        post("/api/v1/attendance/manual", map("memberId", member, "reason", "MEMBER_NO_QR"), desk).andExpect(jsonPath("$.result").value("DUPLICATE_RECENT"));
        post("/api/v1/attendance/manual", map("memberId", 424242, "reason", "MEMBER_NO_QR"), desk).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT source FROM attendance_record").query(String.class).single()).isEqualTo("MANUAL");
        get("/api/v1/attendance", desk).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].source").value("MANUAL"));
    }

    @Test
    void memberRoleCannotUseScanEndpoints() throws Exception {
        setUp();
        Who memberUser = createMemberUser("qr.person", member);
        String t = token();
        post("/api/v1/attendance/scan", map("token", t), memberUser).andExpect(status().isForbidden());
        post("/api/v1/attendance/manual", map("memberId", member, "reason", "MEMBER_NO_QR"), memberUser).andExpect(status().isForbidden());
        get("/api/v1/attendance", memberUser).andExpect(status().isForbidden());
        assertThat(records()).isZero();
    }

    @Test
    void concurrentScansOfTheSamePassCreateExactlyOneRecord() throws Exception {
        setUp();
        String t = token();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<ScanResult>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> attendanceService.scan(t).result()));
            }
            int checkedIn = 0;
            for (Future<ScanResult> f : futures) {
                if (f.get() == ScanResult.CHECKED_IN) checkedIn++;
            }
            assertThat(checkedIn).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(records()).isEqualTo(1);
    }

    @Test
    void rawTokensAreNeverStoredInPlainTextOrAudited() throws Exception {
        setUp();
        String t = token();
        scan(t);
        assertThat(jdbc.sql("SELECT count(*) FROM member_qr_pass WHERE token_hash = :t OR token_enc LIKE '%' || :t || '%'")
                .param("t", t).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM audit_log WHERE details::text LIKE '%' || :t || '%'")
                .param("t", t).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT token_hash FROM member_qr_pass").query(String.class).single()).isEqualTo(Crypto.sha256Hex(t));
    }

    @Test
    void everyMemberGetsExactlyOneActivePassAtCreation() throws Exception {
        setUp();
        createMember("Second Person", "9500000003");
        assertThat(jdbc.sql("SELECT count(*) FROM member_qr_pass WHERE status = 'ACTIVE'").query(Long.class).single()).isEqualTo(2);
    }
}
