package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.auth.StaffPermissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Two seeded demo members prove isolation: every /me endpoint returns only the caller's data, no identity can be
 * supplied by the client, and every back-office endpoint refuses the MEMBER role.
 */
class MemberIsolationIT extends app.gym.support.AbstractIT {

    private long memberA, memberB, planId, paymentA, paymentB, passB;
    private Who userA, userB;
    private String tokenA, tokenB;

    private void setUp() throws Exception {
        planId = createPlan("Monthly", 30, 150000);
        memberA = createMember("Alice Alpha", "9600000001");
        memberB = createMember("Bob Bravo", "9600000002");
        put("/api/v1/members/" + memberA, map("fullName", "Alice Alpha", "phone", "9600000001", "staffNotes", "SECRET-STAFF-NOTE-A"), owner);
        assign(memberA, planId, "2026-10-01");
        assign(memberB, planId, "2026-10-05");
        paymentA = id(post("/api/v1/payments", map("memberId", memberA, "amountPaise", 150000, "method", "CASH"), owner));
        paymentB = id(post("/api/v1/payments", map("memberId", memberB, "amountPaise", 70000, "method", "UPI"), owner));
        userA = createMemberUser("alice", memberA);
        userB = createMemberUser("bob", memberB);
        tokenA = read(get("/api/v1/members/" + memberA + "/qr", owner), "$.token");
        tokenB = read(get("/api/v1/members/" + memberB + "/qr", owner), "$.token");
        passB = ((Number) read(get("/api/v1/members/" + memberB + "/qr", owner), "$.passId")).longValue();
        post("/api/v1/attendance/scan", map("token", tokenA), owner);
        post("/api/v1/attendance/scan", map("token", tokenB), owner);
    }

    @Test
    void meEndpointsReturnOnlyTheCallersOwnData() throws Exception {
        setUp();
        get("/api/v1/me", userA).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCode").value("M00001")).andExpect(jsonPath("$.fullName").value("Alice Alpha"))
                .andExpect(jsonPath("$.phone").value("+919600000001"));
        get("/api/v1/me", userB).andExpect(jsonPath("$.memberCode").value("M00002"));

        get("/api/v1/me/membership", userA).andExpect(jsonPath("$.hasMembership").value(true))
                .andExpect(jsonPath("$.endDate").value("2026-10-30")).andExpect(jsonPath("$.duesPaise").value(0))
                .andExpect(jsonPath("$.visitsThisMonth").value(1));
        get("/api/v1/me/membership", userB).andExpect(jsonPath("$.endDate").value("2026-11-03"))
                .andExpect(jsonPath("$.duesPaise").value(80000));

        get("/api/v1/me/payments", userA).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(paymentA)).andExpect(jsonPath("$.items[0].amountPaise").value(150000));
        get("/api/v1/me/payments", userB).andExpect(jsonPath("$.items[0].id").value(paymentB));
        get("/api/v1/me/dues", userB).andExpect(jsonPath("$.duesPaise").value(80000));
        get("/api/v1/me/attendance", userA).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.monthly.length()").value(6));

        String qrA = read(get("/api/v1/me/qr", userA), "$.token");
        String qrB = read(get("/api/v1/me/qr", userB), "$.token");
        assertThat(qrA).isEqualTo(tokenA).isNotEqualTo(qrB);
        assertThat(qrB).isEqualTo(tokenB);
    }

    @Test
    void memberCannotReadAnotherMembersReceiptOrChangeIdentityViaParameters() throws Exception {
        setUp();
        get("/api/v1/me/payments/" + paymentA + "/receipt", userA).andExpect(status().isOk());
        get("/api/v1/me/payments/" + paymentB + "/receipt", userA).andExpect(status().isNotFound());
        get("/api/v1/me/payments/" + paymentA + "/receipt", userB).andExpect(status().isNotFound());
        // Identity parameters are ignored: the session decides who the caller is.
        get("/api/v1/me?memberId=" + memberB, userA).andExpect(jsonPath("$.memberCode").value("M00001"));
        get("/api/v1/me/payments?memberId=" + memberB + "&member_id=" + memberB, userA)
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].id").value(paymentA));
        get("/api/v1/me/attendance?memberId=" + memberB, userA).andExpect(jsonPath("$.totalItems").value(1));
        get("/api/v1/me/qr?memberId=" + memberB, userA).andExpect(jsonPath("$.token").value(tokenA));
    }

    @Test
    void memberRoleIsRefusedByEveryBackOfficeEndpoint() throws Exception {
        setUp();
        long ms = jdbc.sql("SELECT id FROM membership WHERE member_id = :m").param("m", memberB).query(Long.class).single();
        List<Object[]> calls = new ArrayList<>();
        String b = "/api/v1/members/" + memberB;
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/members", null});
        calls.add(new Object[]{HttpMethod.GET, b, null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/members", map("fullName", "Evil Person", "phone", "9777777777")});
        calls.add(new Object[]{HttpMethod.PUT, b, map("fullName", "Hacked", "phone", "9600000002")});
        calls.add(new Object[]{HttpMethod.POST, b + "/deactivate", map("reason", "x")});
        calls.add(new Object[]{HttpMethod.POST, b + "/reactivate", null});
        calls.add(new Object[]{HttpMethod.POST, b + "/invite", null});
        calls.add(new Object[]{HttpMethod.GET, b + "/memberships", null});
        calls.add(new Object[]{HttpMethod.POST, b + "/memberships", map("planId", planId)});
        calls.add(new Object[]{HttpMethod.GET, b + "/dues", null});
        calls.add(new Object[]{HttpMethod.GET, b + "/qr", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/qr/" + passB + "/revoke", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/qr/" + passB + "/reissue", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/memberships/" + ms + "/renew", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/memberships/" + ms + "/hold", map("reason", "x")});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/memberships/" + ms + "/resume", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/memberships/" + ms + "/cancel", map("reason", "x")});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/payments", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/payments?memberId=" + memberB, null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/payments", map("memberId", memberB, "amountPaise", 5, "method", "CASH")});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/payments/" + paymentB + "/void", map("reason", "x")});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/payments/" + paymentB + "/receipt", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/payments/" + paymentB + "/receipt.html", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/attendance/scan", map("token", tokenB)});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/attendance/manual", map("memberId", memberB)});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/attendance", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/plans", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/plans", map("name", "X", "durationDays", 1, "pricePaise", 1)});
        calls.add(new Object[]{HttpMethod.PUT, "/api/v1/plans/" + planId, map("name", "X", "durationDays", 1, "pricePaise", 1)});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/dashboard/summary", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/dashboard/action-list", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/dashboard/health", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/dashboard/leakage", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/followups", null});
        calls.add(new Object[]{HttpMethod.PUT, "/api/v1/followups", map("membershipId", ms, "contactStatus", "CONTACTED")});
        for (String r : new String[]{"attendance", "expiry", "collections", "outstanding"}) {
            calls.add(new Object[]{HttpMethod.GET, "/api/v1/reports/" + r, null});
            calls.add(new Object[]{HttpMethod.GET, "/api/v1/reports/" + r + "/export", null});
        }
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/staff", null});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/staff", map("loginId", "evil", "temporaryPassword", "Evil12345",
                "permissions", map("canRecordPayments", true, "canManageMemberships", true, "canEditMembers", true, "canViewReports", true))});
        calls.add(new Object[]{HttpMethod.POST, "/api/v1/users/" + userB.id() + "/reset-link", null});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/settings", null});
        calls.add(new Object[]{HttpMethod.PUT, "/api/v1/settings", map("gymName", "X", "timezone", "UTC", "duplicateWindowMinutes", 1,
                "dropOffDays", 1, "expiringSoonDays", 1, "memberCanEditEmail", true)});
        calls.add(new Object[]{HttpMethod.GET, "/api/v1/audit", null});

        for (Object[] c : calls) {
            ResultActions r = call((HttpMethod) c[0], (String) c[1], c[2], userA);
            int status = r.andReturn().getResponse().getStatus();
            assertThat(status).as("%s %s as MEMBER", c[0], c[1]).isEqualTo(403);
        }
        // Nothing changed as a result.
        assertThat(jdbc.sql("SELECT count(*) FROM member_profile").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT full_name FROM member_profile WHERE id = :id").param("id", memberB).query(String.class).single())
                .isEqualTo("Bob Bravo");
    }

    @Test
    void staffAndAnonymousCannotUseMeEndpoints() throws Exception {
        setUp();
        Who staff = createStaff("desk", StaffPermissions.all());
        get("/api/v1/me", staff).andExpect(status().isForbidden());
        get("/api/v1/me", owner).andExpect(status().isForbidden());
        get("/api/v1/me/qr", staff).andExpect(status().isForbidden());
        get("/api/v1/me", null).andExpect(status().isUnauthorized());
        get("/api/v1/me/payments", null).andExpect(status().isUnauthorized());
    }

    @Test
    void staffNotesNeverAppearInAnyMemberResponse() throws Exception {
        setUp();
        get("/api/v1/members/" + memberA, owner).andExpect(jsonPath("$.staffNotes").value("SECRET-STAFF-NOTE-A"));
        for (String url : new String[]{"/api/v1/me", "/api/v1/me/membership", "/api/v1/me/attendance", "/api/v1/me/payments",
                "/api/v1/me/dues", "/api/v1/me/qr", "/api/v1/auth/session", "/api/v1/me/payments/" + paymentA + "/receipt"}) {
            String body = get(url, userA).andReturn().getResponse().getContentAsString();
            assertThat(body).as(url).doesNotContain("SECRET-STAFF-NOTE").doesNotContain("staffNotes").doesNotContain("staff_notes");
        }
    }

    @Test
    void memberCanOnlyEditAllowedFieldsAndOnlyWhenTheGymAllows() throws Exception {
        setUp();
        call(HttpMethod.PATCH, "/api/v1/me", map("fullName", "Hacker"), userA).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/api/v1/me", map("phone", "9999999999"), userA).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/api/v1/me", map("memberCode", "M99999"), userA).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/api/v1/me", map("staffNotes", "x"), userA).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/api/v1/me", map("email", "new@example.com"), userA)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FIELD_NOT_EDITABLE"));
        call(HttpMethod.PATCH, "/api/v1/me", map("emergencyContact", "Mom 12345"), userA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.emergencyContact").value("Mom 12345"));

        jdbc.sql("UPDATE gym_settings SET member_can_edit_email = TRUE").update();
        call(HttpMethod.PATCH, "/api/v1/me", map("email", "new@example.com"), userA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com"));
        call(HttpMethod.PATCH, "/api/v1/me", map("email", "not-an-email"), userA).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/api/v1/me", map(), userA).andExpect(status().isBadRequest());

        // B is untouched.
        get("/api/v1/me", userB).andExpect(jsonPath("$.email").doesNotExist());
        assertThat(jdbc.sql("SELECT full_name FROM member_profile WHERE id = :id").param("id", memberA).query(String.class).single())
                .isEqualTo("Alice Alpha");
    }

    @Test
    void emptyStatesAreWellFormed() throws Exception {
        long c = createMember("Cara Charlie", "9600000003");
        Who userC = createMemberUser("cara", c);
        get("/api/v1/me/membership", userC).andExpect(jsonPath("$.hasMembership").value(false))
                .andExpect(jsonPath("$.visitsThisMonth").value(0)).andExpect(jsonPath("$.duesPaise").value(0));
        get("/api/v1/me/payments", userC).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalItems").value(0));
        get("/api/v1/me/attendance", userC).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.monthly.length()").value(6)).andExpect(jsonPath("$.monthly[5].visits").value(0));
        get("/api/v1/me/dues", userC).andExpect(jsonPath("$.duesPaise").value(0));
        get("/api/v1/me/qr", userC).andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
    }

    @Test
    void reissuedPassReplacesTheOldOneForTheMember() throws Exception {
        setUp();
        String fresh = read(post("/api/v1/me/qr/reissue", null, userA).andExpect(status().isOk()), "$.token");
        assertThat(fresh).isNotEqualTo(tokenA);
        assertThat(read(post("/api/v1/attendance/scan", map("token", tokenA), owner), "$.result").toString()).isEqualTo("PASS_REVOKED");
        // B's pass is unaffected by A's reissue.
        assertThat(read(get("/api/v1/me/qr", userB), "$.token").toString()).isEqualTo(tokenB);
    }

    @Test
    void deactivatedMembersLoseAccessImmediately() throws Exception {
        setUp();
        get("/api/v1/me", userA).andExpect(status().isOk());
        post("/api/v1/members/" + memberA + "/deactivate", map("reason", "left"), owner).andExpect(status().isOk());
        get("/api/v1/me", userA).andExpect(status().isUnauthorized());
    }
}
