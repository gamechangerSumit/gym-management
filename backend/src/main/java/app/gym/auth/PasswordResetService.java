package app.gym.auth;

import app.gym.audit.AuditService;
import app.gym.auth.AuthDtos.LinkResponse;
import app.gym.common.ApiException;
import app.gym.config.AppProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset without email/SMS (documented MVP limitation): a person asks, the owner sees the request in the
 * audit log and generates a single-use link to hand over manually.
 */
@Service
public class PasswordResetService {

    private final UserAccountRepository users;
    private final OneTimeTokenService tokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;

    public PasswordResetService(UserAccountRepository users, OneTimeTokenService tokens, PasswordEncoder encoder,
                                AuditService audit, AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
    }

    /** Public request: records that a reset was asked for (if the account exists) and reveals nothing either way. */
    public void request(String loginId) {
        users.findByLoginId(loginId.trim())
                .filter(u -> "ACTIVE".equals(u.status()) && u.role() != Role.OWNER)
                .ifPresent(u -> audit.recordAs(null, "PASSWORD_RESET_REQUESTED", "USER", u.id(),
                        Map.of("loginId", u.loginId())));
    }

    /** Owner action: issue a single-use reset link for a staff or member account. */
    @Transactional
    public LinkResponse createLink(long targetUserId) {
        UserRow target = users.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        if (target.role() == Role.OWNER) {
            throw ApiException.badRequest("NOT_ALLOWED", "Owner passwords are changed from the account settings.");
        }
        if ("INVITED".equals(target.status())) {
            throw ApiException.conflict("NOT_ACTIVATED", "This account has not been activated yet. Re-send the invite instead.");
        }
        OneTimeTokenService.Issued issued = tokens.issue(target.id(), OneTimeTokenService.PASSWORD_RESET,
                Duration.ofHours(props.token().resetTtlHours()));
        audit.record("PASSWORD_RESET_LINK_CREATED", "USER", target.id(), Map.of("loginId", target.loginId()));
        return new LinkResponse(target.loginId(), baseUrl() + "/reset-password?token=" + issued.token(), issued.expiresAt());
    }

    @Transactional
    public void confirm(String token, String newPassword) {
        long userId = tokens.consume(token, OneTimeTokenService.PASSWORD_RESET);
        UserRow user = users.findById(userId).orElseThrow(() ->
                ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired. Ask the gym owner for a new one."));
        PasswordPolicy.validate(newPassword, user.loginId());
        users.setPassword(userId, encoder.encode(newPassword), false, false);
        users.endSessions(user.loginId());
        audit.recordAs(userId, "PASSWORD_RESET_COMPLETED", "USER", userId, Map.of());
    }

    private String baseUrl() {
        String base = props.publicBaseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
