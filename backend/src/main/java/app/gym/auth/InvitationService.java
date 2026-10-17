package app.gym.auth;

import app.gym.audit.AuditService;
import app.gym.auth.AuthDtos.LinkResponse;
import app.gym.common.ApiException;
import app.gym.config.AppProperties;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner invites a member to My Gym: creates an INVITED account and a single-use activation link. */
@Service
public class InvitationService {

    static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9._@+-]{3,80}$");

    private final JdbcClient jdbc;
    private final UserAccountRepository users;
    private final OneTimeTokenService tokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;

    public InvitationService(JdbcClient jdbc, UserAccountRepository users, OneTimeTokenService tokens,
                             PasswordEncoder encoder, AuditService audit, AppProperties props) {
        this.jdbc = jdbc;
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
    }

    private record MemberRef(long id, String phone, String status, Long userId) {}

    @Transactional
    public LinkResponse inviteMember(long memberId, String requestedLoginId) {
        MemberRef member = jdbc.sql("SELECT id, phone, status, user_id FROM member_profile WHERE id = :id FOR UPDATE")
                .param("id", memberId)
                .query((rs, i) -> new MemberRef(rs.getLong("id"), rs.getString("phone"), rs.getString("status"),
                        app.gym.common.Db.longOrNull(rs, "user_id")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Member not found."));
        if (!"ACTIVE".equals(member.status())) {
            throw ApiException.conflict("MEMBER_INACTIVE", "Reactivate the member before inviting them.");
        }

        long userId;
        String loginId;
        if (member.userId() != null) {
            UserRow existing = users.findById(member.userId()).orElseThrow();
            if (!"INVITED".equals(existing.status())) {
                throw ApiException.conflict("ACCOUNT_EXISTS", "This member already has an active account.");
            }
            userId = existing.id();
            loginId = existing.loginId();
        } else {
            loginId = (requestedLoginId == null || requestedLoginId.isBlank())
                    ? member.phone().replace("+", "")
                    : requestedLoginId.trim();
            if (!LOGIN_PATTERN.matcher(loginId).matches()) {
                throw ApiException.badRequest("INVALID_LOGIN",
                        "Login ID must be 3-80 characters: letters, digits and . _ @ + - only.");
            }
            if (users.findByLoginId(loginId).isPresent()) {
                throw ApiException.conflict("LOGIN_TAKEN", "That login ID is already in use. Choose another.");
            }
            userId = users.insert(loginId, null, Role.MEMBER, "INVITED", false, false);
            jdbc.sql("UPDATE member_profile SET user_id = :u, updated_at = now() WHERE id = :m")
                    .param("u", userId).param("m", memberId).update();
        }

        OneTimeTokenService.Issued issued = tokens.issue(userId, OneTimeTokenService.INVITE,
                Duration.ofHours(props.token().inviteTtlHours()));
        audit.record("MEMBER_INVITED", "MEMBER", memberId, Map.of("loginId", loginId));
        return new LinkResponse(loginId, baseUrl() + "/activate?token=" + issued.token(), issued.expiresAt());
    }

    @Transactional
    public void activate(String token, String newPassword) {
        long userId = tokens.consume(token, OneTimeTokenService.INVITE);
        UserRow user = users.findById(userId).orElseThrow(() ->
                ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired. Ask the gym owner for a new one."));
        if (!"INVITED".equals(user.status())) {
            throw ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired. Ask the gym owner for a new one.");
        }
        PasswordPolicy.validate(newPassword, user.loginId());
        users.setPassword(userId, encoder.encode(newPassword), false, true);
        audit.recordAs(userId, "ACCOUNT_ACTIVATED", "USER", userId, Map.of());
    }

    private String baseUrl() {
        String base = props.publicBaseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
