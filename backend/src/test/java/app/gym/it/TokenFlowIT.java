package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.auth.StaffPermissions;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.ResultActions;

class TokenFlowIT extends app.gym.support.AbstractIT {

    private ResultActions anon(String url, Object body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).with(csrf()).contentType(JSON).content(mapper.writeValueAsString(body)));
    }

    private static String tokenOf(String url) {
        return url.substring(url.indexOf("token=") + 6);
    }

    private String invite(long memberId) throws Exception {
        String url = read(post("/api/v1/members/" + memberId + "/invite", null, owner).andExpect(status().isOk()), "$.url");
        return tokenOf(url);
    }

    @Test
    void inviteActivateThenLogin() throws Exception {
        long memberId = createMember("Invite Me", "9222222222");
        String token = invite(memberId);

        anon("/api/v1/auth/activate", map("token", token, "newPassword", "Activated123")).andExpect(status().isNoContent());
        anon("/api/v1/auth/login", map("loginId", "919222222222", "password", "Activated123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberId").value(memberId));
    }

    @Test
    void invitationTokenIsSingleUse() throws Exception {
        String token = invite(createMember("Invite Me", "9222222222"));
        anon("/api/v1/auth/activate", map("token", token, "newPassword", "Activated123")).andExpect(status().isNoContent());
        anon("/api/v1/auth/activate", map("token", token, "newPassword", "Another1234"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredInvitationIsRejected() throws Exception {
        String token = invite(createMember("Invite Me", "9222222222"));
        clock.advance(Duration.ofHours(73));
        anon("/api/v1/auth/activate", map("token", token, "newPassword", "Activated123"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        anon("/api/v1/auth/activate", map("token", "not-a-real-token", "newPassword", "Activated123"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void weakPasswordDoesNotBurnTheToken() throws Exception {
        String token = invite(createMember("Invite Me", "9222222222"));
        anon("/api/v1/auth/activate", map("token", token, "newPassword", "short"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        anon("/api/v1/auth/activate", map("token", token, "newPassword", "Activated123")).andExpect(status().isNoContent());
    }

    @Test
    void reinvitingInvalidatesTheEarlierLink() throws Exception {
        long memberId = createMember("Invite Me", "9222222222");
        String first = invite(memberId);
        String second = invite(memberId);
        anon("/api/v1/auth/activate", map("token", first, "newPassword", "Activated123"))
                .andExpect(status().isBadRequest());
        anon("/api/v1/auth/activate", map("token", second, "newPassword", "Activated123")).andExpect(status().isNoContent());
        // An active account cannot be invited again.
        post("/api/v1/members/" + memberId + "/invite", null, owner).andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwnerCanInvite() throws Exception {
        long memberId = createMember("Invite Me", "9222222222");
        Who staff = createStaff("reception", StaffPermissions.all());
        post("/api/v1/members/" + memberId + "/invite", null, staff).andExpect(status().isForbidden());
    }

    @Test
    void tokensAreStoredOnlyAsHashes() throws Exception {
        String token = invite(createMember("Invite Me", "9222222222"));
        String stored = jdbc.sql("SELECT token_hash FROM one_time_token").query(String.class).single();
        assertThat(stored).isNotEqualTo(token).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(token.length()).isGreaterThanOrEqualTo(40);
    }

    @Test
    void passwordResetLinkWorksOnceAndExpires() throws Exception {
        Who staff = createStaff("reception", StaffPermissions.none());
        String token = tokenOf(read(post("/api/v1/users/" + staff.id() + "/reset-link", null, owner)
                .andExpect(status().isOk()), "$.url"));

        anon("/api/v1/auth/password-reset/confirm", map("token", token, "newPassword", "Resetted123")).andExpect(status().isNoContent());
        anon("/api/v1/auth/login", map("loginId", "reception", "password", "StaffPass123")).andExpect(status().isUnauthorized());
        anon("/api/v1/auth/login", map("loginId", "reception", "password", "Resetted123")).andExpect(status().isOk());
        anon("/api/v1/auth/password-reset/confirm", map("token", token, "newPassword", "Resetted456"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        String token2 = tokenOf(read(post("/api/v1/users/" + staff.id() + "/reset-link", null, owner), "$.url"));
        clock.advance(Duration.ofHours(3));
        anon("/api/v1/auth/password-reset/confirm", map("token", token2, "newPassword", "Resetted789"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void anInvitationTokenCannotBeUsedForPasswordReset() throws Exception {
        String token = invite(createMember("Invite Me", "9222222222"));
        anon("/api/v1/auth/password-reset/confirm", map("token", token, "newPassword", "Resetted123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerAccountsCannotGetResetLinks() throws Exception {
        post("/api/v1/users/" + owner.id() + "/reset-link", null, owner).andExpect(status().isBadRequest());
    }
}
