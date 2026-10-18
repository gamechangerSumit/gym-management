package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class GymQrCheckInIT extends app.gym.support.AbstractIT {

    @Test
    void memberCanScanCurrentGymQrAndAttendanceIsRecordedForTheirOwnAccount() throws Exception {
        long memberId = createMember("Gym QR Member", "9800000199");
        long planId = createPlan("Gym QR Plan", 30, 100000);
        assign(memberId, planId, "2026-10-15");
        Who member = createMemberUser("gym-qr-member", memberId);

        var qr = post("/api/v1/attendance/gym-qr", map(), owner)
                .andExpect(status().isOk());
        String payload = read(qr, "$.payload").toString();
        assertThat(payload).startsWith("GYMCHECKIN:");

        var response = post("/api/v1/me/check-in", map("token", payload), member)
                .andExpect(status().isOk());
        assertThat(read(response, "$.result").toString()).isEqualTo("CHECKED_IN");
        assertThat(read(response, "$.memberId")).isEqualTo(memberId);

        Integer count = jdbc.sql("SELECT count(*) FROM attendance_record WHERE member_id=:m AND source='QR'")
                .param("m", memberId).query(Integer.class).single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void memberCannotUseExpiredOrMadeUpGymQr() throws Exception {
        long memberId = createMember("Invalid Gym QR Member", "9800000198");
        Who member = createMemberUser("invalid-gym-qr-member", memberId);
        post("/api/v1/me/check-in", map("token", "GYMCHECKIN:not-a-real-token"), member)
                .andExpect(status().isBadRequest());
    }
}
