package app.gym.auth;

import app.gym.common.ApiException;
import app.gym.common.Crypto;
import app.gym.common.Db;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Invite and password-reset tokens: 256-bit random, stored only as SHA-256 hash, single use, expiring.
 * Delivery is manual (the owner shares the link) because the MVP has no email/SMS provider.
 */
@Service
public class OneTimeTokenService {

    public static final String INVITE = "INVITE";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    public record Issued(String token, Instant expiresAt) {}

    private final JdbcClient jdbc;
    private final Clock clock;

    public OneTimeTokenService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Issues a new token and invalidates any earlier unused token of the same purpose for this user. */
    public Issued issue(long userId, String purpose, Duration ttl) {
        Instant now = clock.instant();
        jdbc.sql("UPDATE one_time_token SET used_at = :now WHERE user_id = :u AND purpose = :p AND used_at IS NULL")
                .param("now", Db.ts(now))
                .param("u", userId)
                .param("p", purpose)
                .update();
        String raw = Crypto.randomToken(32);
        Instant expires = now.plus(ttl);
        jdbc.sql("INSERT INTO one_time_token (user_id, purpose, token_hash, expires_at) VALUES (:u, :p, :h, :e)")
                .param("u", userId)
                .param("p", purpose)
                .param("h", Crypto.sha256Hex(raw))
                .param("e", Db.ts(expires))
                .update();
        return new Issued(raw, expires);
    }

    /** Atomically marks the token used and returns its user id; fails if unknown, expired, used, or wrong purpose. */
    public long consume(String rawToken, String purpose) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            throw invalid();
        }
        return jdbc.sql("UPDATE one_time_token SET used_at = :now WHERE token_hash = :h AND purpose = :p "
                        + "AND used_at IS NULL AND expires_at > :now RETURNING user_id")
                .param("now", Db.ts(clock.instant()))
                .param("h", Crypto.sha256Hex(rawToken.trim()))
                .param("p", purpose)
                .query(Long.class)
                .optional()
                .orElseThrow(OneTimeTokenService::invalid);
    }

    private static ApiException invalid() {
        return ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired. Ask the gym owner for a new one.");
    }
}
