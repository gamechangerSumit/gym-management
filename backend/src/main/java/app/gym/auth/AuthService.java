package app.gym.auth;

import app.gym.audit.AuditService;
import app.gym.auth.AuthDtos.GymInfo;
import app.gym.auth.AuthDtos.SessionInfo;
import app.gym.common.ApiException;
import app.gym.config.AppProperties;
import app.gym.settings.GymSettings;
import app.gym.settings.GymSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final RateLimiter limiter;
    private final AppProperties props;
    private final Clock clock;
    private final AuditService audit;
    private final GymSettingsService settings;
    private final JdbcClient jdbc;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    /** Used to equalise timing when the account does not exist, so response time does not reveal valid logins. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder encoder, RateLimiter limiter, AppProperties props,
                       Clock clock, AuditService audit, GymSettingsService settings, JdbcClient jdbc) {
        this.users = users;
        this.encoder = encoder;
        this.limiter = limiter;
        this.props = props;
        this.clock = clock;
        this.audit = audit;
        this.settings = settings;
        this.jdbc = jdbc;
        this.dummyHash = encoder.encode("dummy-password-for-timing-" + System.nanoTime());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid login ID or password.");
    }

    /** Deliberately not @Transactional: failed-attempt counters must persist even though we then throw. */
    public SessionInfo login(String loginId, String password, HttpServletRequest request, HttpServletResponse response) {
        String login = loginId.trim();
        if (!limiter.tryAccount(login)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many attempts. Please wait a few minutes and try again.");
        }
        Instant now = clock.instant();
        Optional<UserRow> found = users.findByLoginId(login);
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        UserRow user = found.get();
        boolean usable = "ACTIVE".equals(user.status()) && user.passwordHash() != null;
        boolean locked = user.lockedUntil() != null && user.lockedUntil().isAfter(now);
        if (!usable || locked) {
            encoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (!encoder.matches(password, user.passwordHash())) {
            int attempts = user.failedAttempts() + 1;
            if (attempts >= props.security().maxFailedAttempts()) {
                Instant until = now.plus(Duration.ofMinutes(props.security().lockMinutes()));
                users.recordFailure(user.id(), 0, until);
                audit.recordAs(null, "ACCOUNT_LOCKED", "USER", user.id(), Map.of("lockedUntil", until.toString()));
                log.warn("Account locked after repeated failures userId={}", user.id());
            } else {
                users.recordFailure(user.id(), attempts, null);
            }
            throw invalidCredentials();
        }

        users.recordSuccess(user.id(), now);
        AppUser principal = new AppUser(user.id(), user.loginId(), user.role());
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        // Session fixation protection: rotate the id of any pre-existing session.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return describe(principal);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword, HttpServletRequest request) {
        AppUser principal = CurrentUser.require();
        UserRow user = users.findById(principal.id()).orElseThrow(AuthService::invalidCredentials);
        if (user.passwordHash() == null || !encoder.matches(currentPassword, user.passwordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Your current password is not correct.");
        }
        PasswordPolicy.validate(newPassword, user.loginId());
        if (encoder.matches(newPassword, user.passwordHash())) {
            throw ApiException.badRequest("WEAK_PASSWORD", "The new password must be different from the current one.");
        }
        users.setPassword(user.id(), encoder.encode(newPassword), false, false);
        HttpSession session = request.getSession(false);
        if (session != null) {
            users.endOtherSessions(user.loginId(), session.getId());
        }
        audit.record("PASSWORD_CHANGED", "USER", user.id(), Map.of());
    }

    /** Builds the session description for the SPA; for anonymous callers only public gym info is returned. */
    public SessionInfo describeCurrent() {
        return CurrentUser.find().map(this::describe).orElseGet(this::anonymous);
    }

    public SessionInfo anonymous() {
        GymSettings gs = settings.get();
        return new SessionInfo(false, null, null, null, false, null, null,
                new GymInfo(gs.gymName(), gs.zone().getId()), users.anyDemoAccount());
    }

    public SessionInfo describe(AppUser principal) {
        UserRow row = users.findById(principal.id()).orElseThrow(AuthService::invalidCredentials);
        StaffPermissions perms = switch (principal.role()) {
            case OWNER -> StaffPermissions.all();
            case STAFF -> users.permissions(principal.id());
            case MEMBER -> StaffPermissions.none();
        };
        Long memberId = null;
        if (principal.role() == Role.MEMBER) {
            memberId = jdbc.sql("SELECT id FROM member_profile WHERE user_id = :u")
                    .param("u", principal.id()).query(Long.class).optional().orElse(null);
        }
        GymSettings gs = settings.get();
        return new SessionInfo(true, principal.id(), principal.loginId(), principal.role().name(),
                row.mustChangePassword(), memberId, perms, new GymInfo(gs.gymName(), gs.zone().getId()),
                users.anyDemoAccount());
    }
}
