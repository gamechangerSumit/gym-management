package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.auth.Role;
import app.gym.auth.StaffPermissions;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends app.gym.support.AbstractIT {

    private MvcResult login(String loginId, String password) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(JSON)
                        .content(mapper.writeValueAsString(map("loginId", loginId, "password", password))))
                .andReturn();
    }

    @Test
    void loginSucceedsAndIssuesAnHttpOnlyLaxSessionCookie() throws Exception {
        MvcResult result = login("owner", "OwnerPass123");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"authenticated\":true").contains("\"role\":\"OWNER\"");
        Cookie session = result.getResponse().getCookie("GYMSESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(h -> h.startsWith("GYMSESSION=") && h.contains("SameSite=Lax") && h.contains("HttpOnly"));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameGenericError() throws Exception {
        MvcResult wrong = login("owner", "nope-nope-1");
        MvcResult unknown = login("nobody", "nope-nope-1");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        String a = wrong.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", "");
        String b = unknown.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", "");
        assertThat(a).isEqualTo(b).contains("INVALID_CREDENTIALS");
    }

    @Test
    void accountLocksAfterRepeatedFailuresAndUnlocksLater() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("owner", "wrong-pass-1").getResponse().getStatus()).isEqualTo(401);
        }
        // Correct password is refused while locked, with the same generic error.
        MvcResult locked = login("owner", "OwnerPass123");
        assertThat(locked.getResponse().getStatus()).isEqualTo(401);
        assertThat(locked.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");

        clock.advance(Duration.ofMinutes(16));
        assertThat(login("owner", "OwnerPass123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        login("owner", "bad-pass-1");
        login("owner", "bad-pass-2");
        assertThat(login("owner", "OwnerPass123").getResponse().getStatus()).isEqualTo(200);
        login("owner", "bad-pass-3");
        login("owner", "bad-pass-4");
        // Two failures since the last success: still below the limit of 3, so login works.
        assertThat(login("owner", "OwnerPass123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void sessionCookieAuthenticatesLaterRequestsAndLogoutEndsIt() throws Exception {
        Cookie session = login("owner", "OwnerPass123").getResponse().getCookie("GYMSESSION");
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/session").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.loginId").value("owner"));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/members").cookie(session)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout").cookie(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/members").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestsGet401WithJsonBody() throws Exception {
        get("/api/v1/members", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        get("/api/v1/me", null).andExpect(status().isUnauthorized());
        get("/api/v1/auth/session", null).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void staffCannotReachOwnerOnlyEndpoints() throws Exception {
        Who staff = createStaff("reception", StaffPermissions.all());
        get("/api/v1/staff", staff).andExpect(status().isForbidden());
        get("/api/v1/settings", staff).andExpect(status().isForbidden());
        get("/api/v1/audit", staff).andExpect(status().isForbidden());
        post("/api/v1/plans", map("name", "X", "durationDays", 30, "pricePaise", 100), staff).andExpect(status().isForbidden());
        post("/api/v1/users/" + staff.id() + "/reset-link", null, staff).andExpect(status().isForbidden());
        // ...while the same staff can read plans.
        get("/api/v1/plans", staff).andExpect(status().isOk());
    }

    @Test
    void disabledAccountLosesAccessImmediately() throws Exception {
        Who staff = createStaff("reception", StaffPermissions.none());
        get("/api/v1/members", staff).andExpect(status().isOk());
        users.setStatus(staff.id(), "DISABLED");
        get("/api/v1/members", staff).andExpect(status().isUnauthorized());
    }

    @Test
    void mustChangePasswordBlocksEverythingElseUntilChanged() throws Exception {
        long id = users.insert("newstaff", encoder.encode("TempPass123"), Role.STAFF, "ACTIVE", true, false);
        Who staff = new Who(id, "newstaff", Role.STAFF);
        get("/api/v1/members", staff).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        get("/api/v1/auth/session", staff).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));

        post("/api/v1/auth/change-password", map("currentPassword", "WrongOne123", "newPassword", "BrandNew123"), staff)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        post("/api/v1/auth/change-password", map("currentPassword", "TempPass123", "newPassword", "short"), staff)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        post("/api/v1/auth/change-password", map("currentPassword", "TempPass123", "newPassword", "BrandNew123"), staff)
                .andExpect(status().isNoContent());

        get("/api/v1/members", staff).andExpect(status().isOk());
        assertThat(login("newstaff", "BrandNew123").getResponse().getStatus()).isEqualTo(200);
        assertThat(login("newstaff", "TempPass123").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void ownerCreatesStaffWhoMustChangeTheirTemporaryPassword() throws Exception {
        post("/api/v1/staff", map("loginId", "desk1", "temporaryPassword", "Temp12345",
                "permissions", map("canRecordPayments", true, "canManageMemberships", false,
                        "canEditMembers", true, "canViewReports", false)), owner)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.permissions.canRecordPayments").value(true))
                .andExpect(jsonPath("$.permissions.canViewReports").value(false));
        post("/api/v1/staff", map("loginId", "DESK1", "temporaryPassword", "Temp12345",
                "permissions", map("canRecordPayments", false, "canManageMemberships", false,
                        "canEditMembers", false, "canViewReports", false)), owner)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOGIN_TAKEN"));
    }

    @Test
    void permissionChangesTakeEffectOnTheNextRequest() throws Exception {
        Who staff = createStaff("reception", StaffPermissions.none());
        post("/api/v1/members", map("fullName", "Pat One", "phone", "9111111111"), staff).andExpect(status().isForbidden());
        put("/api/v1/staff/" + staff.id() + "/permissions",
                map("canRecordPayments", false, "canManageMemberships", false, "canEditMembers", true, "canViewReports", false),
                owner).andExpect(status().isOk());
        post("/api/v1/members", map("fullName", "Pat One", "phone", "9111111111"), staff).andExpect(status().isCreated());
    }

    @Test
    void passwordResetRequestEndpointRevealsNothing() throws Exception {
        Who staff = createStaff("reception", StaffPermissions.none());
        MvcResult known = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/password-reset/request").with(csrf()).contentType(JSON)
                .content("{\"loginId\":\"reception\"}")).andReturn();
        MvcResult unknown = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/password-reset/request").with(csrf()).contentType(JSON)
                .content("{\"loginId\":\"ghost\"}")).andReturn();
        assertThat(known.getResponse().getStatus()).isEqualTo(202);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(202);
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        long requests = jdbc.sql("SELECT count(*) FROM audit_log WHERE action = 'PASSWORD_RESET_REQUESTED'")
                .query(Long.class).single();
        assertThat(requests).isEqualTo(1);
    }
}
