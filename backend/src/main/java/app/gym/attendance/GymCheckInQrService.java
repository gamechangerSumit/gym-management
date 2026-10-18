package app.gym.attendance;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Crypto;
import app.gym.settings.GymClock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short-lived gym QR challenges. The QR can be shared by many members during its brief validity window. */
@Service
public class GymCheckInQrService {
    public static final String PREFIX = "GYMCHECKIN:";
    private static final long VALIDITY_SECONDS = 30;

    public record GymQrDto(String payload, Instant expiresAt) {}

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final AuditService audit;

    public GymCheckInQrService(JdbcClient jdbc, GymClock clock, AuditService audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public GymQrDto issue() {
        Instant now = clock.now();
        jdbc.sql("DELETE FROM gym_checkin_qr WHERE expires_at <= :now")
                .param("now", now).update();
        String token = Crypto.randomToken(32);
        Instant expiresAt = now.plusSeconds(VALIDITY_SECONDS);
        jdbc.sql("INSERT INTO gym_checkin_qr(token_hash, expires_at, created_by) VALUES (:hash, :expires, :by)")
                .param("hash", Crypto.sha256Hex(token))
                .param("expires", expiresAt)
                .param("by", CurrentUser.idOrNull())
                .update();
        audit.record("GYM_CHECKIN_QR_ISSUED", "GYM", 1L, Map.of("validitySeconds", VALIDITY_SECONDS));
        return new GymQrDto(PREFIX + token, expiresAt);
    }

    @Transactional(readOnly = true)
    public void validate(String payload) {
        if (payload == null || !payload.startsWith(PREFIX)) {
            throw invalid();
        }
        String token = payload.substring(PREFIX.length()).trim();
        if (token.isEmpty() || token.length() > 200) {
            throw invalid();
        }
        boolean valid = Boolean.TRUE.equals(jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM gym_checkin_qr WHERE token_hash=:hash AND expires_at > :now)")
                .param("hash", Crypto.sha256Hex(token))
                .param("now", clock.now())
                .query(Boolean.class).single());
        if (!valid) throw invalid();
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "GYM_QR_EXPIRED",
                "This gym QR code has expired. Scan the latest code displayed at reception.");
    }
}
