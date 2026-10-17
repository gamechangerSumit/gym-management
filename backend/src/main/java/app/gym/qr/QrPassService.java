package app.gym.qr;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Crypto;
import app.gym.common.Db;
import app.gym.qr.QrDtos.PassDto;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One ACTIVE pass per member (enforced by a partial unique index). Tokens are 256-bit SecureRandom values;
 * lookup is by SHA-256 hash, and an AES-GCM encrypted copy lets the member's own page redisplay the pass.
 * Raw tokens are never logged or audited.
 */
@Service
public class QrPassService {

    public record PassRef(long id, long memberId, String status) {}

    private final JdbcClient jdbc;
    private final QrCrypto crypto;
    private final AuditService audit;

    public QrPassService(JdbcClient jdbc, QrCrypto crypto, AuditService audit) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.audit = audit;
    }

    public Optional<PassRef> findByToken(String rawToken) {
        return jdbc.sql("SELECT id, member_id, status FROM member_qr_pass WHERE token_hash = :h")
                .param("h", Crypto.sha256Hex(rawToken))
                .query((rs, i) -> new PassRef(rs.getLong(1), rs.getLong(2), rs.getString(3)))
                .optional();
    }

    /** Creates a pass if the member has no ACTIVE one (used at member creation and on first request). */
    @Transactional
    public PassDto ensureActivePass(long memberId) {
        Optional<PassDto> existing = activePass(memberId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return insertPass(memberId);
    }

    /** Returns the ACTIVE pass with its token; if the encrypted copy is unreadable the pass is rotated. */
    @Transactional
    public PassDto currentPass(long memberId) {
        return ensureActivePass(memberId);
    }

    /** Revokes the current pass and issues a new one atomically; the old token stops working immediately. */
    @Transactional
    public PassDto reissue(long memberId) {
        lockMember(memberId);
        revokeActive(memberId);
        PassDto pass = insertPass(memberId);
        audit.record("QR_REISSUED", "MEMBER", memberId, Map.of("passId", pass.passId()));
        return pass;
    }

    @Transactional
    public void revoke(long passId) {
        PassRef pass = jdbc.sql("SELECT id, member_id, status FROM member_qr_pass WHERE id = :id")
                .param("id", passId)
                .query((rs, i) -> new PassRef(rs.getLong(1), rs.getLong(2), rs.getString(3)))
                .optional().orElseThrow(() -> ApiException.notFound("QR pass not found."));
        if (!"ACTIVE".equals(pass.status())) {
            return;
        }
        jdbc.sql("UPDATE member_qr_pass SET status = 'REVOKED', token_enc = NULL, revoked_at = now(), revoked_by = :by "
                        + "WHERE id = :id")
                .param("by", CurrentUser.idOrNull()).param("id", passId).update();
        audit.record("QR_REVOKED", "MEMBER", pass.memberId(), Map.of("passId", passId));
    }

    public long memberOfPass(long passId) {
        return jdbc.sql("SELECT member_id FROM member_qr_pass WHERE id = :id").param("id", passId)
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("QR pass not found."));
    }

    /** Status of the member's pass without exposing the token. */
    public Optional<PassRef> activeRef(long memberId) {
        return jdbc.sql("SELECT id, member_id, status FROM member_qr_pass WHERE member_id = :m AND status = 'ACTIVE'")
                .param("m", memberId)
                .query((rs, i) -> new PassRef(rs.getLong(1), rs.getLong(2), rs.getString(3)))
                .optional();
    }

    // ---------------------------------------------------------------- helpers

    private void lockMember(long memberId) {
        jdbc.sql("SELECT id FROM member_profile WHERE id = :id FOR UPDATE").param("id", memberId)
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Member not found."));
    }

    private void revokeActive(long memberId) {
        jdbc.sql("UPDATE member_qr_pass SET status = 'REVOKED', token_enc = NULL, revoked_at = now(), revoked_by = :by "
                        + "WHERE member_id = :m AND status = 'ACTIVE'")
                .param("by", CurrentUser.idOrNull()).param("m", memberId).update();
    }

    private Optional<PassDto> activePass(long memberId) {
        lockMember(memberId);
        Optional<Object[]> row = jdbc.sql("SELECT id, token_enc, created_at FROM member_qr_pass "
                        + "WHERE member_id = :m AND status = 'ACTIVE'")
                .param("m", memberId)
                .query((rs, i) -> new Object[]{rs.getLong(1), rs.getString(2), Db.instant(rs, "created_at")})
                .optional();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        String token = row.get()[1] == null ? null : crypto.decrypt((String) row.get()[1]);
        if (token == null) {
            // Cannot redisplay (e.g. encryption key changed): rotate so the member gets a working pass.
            revokeActive(memberId);
            return Optional.empty();
        }
        return Optional.of(PassDto.of((Long) row.get()[0], token, "ACTIVE", (Instant) row.get()[2]));
    }

    private PassDto insertPass(long memberId) {
        String token = Crypto.randomToken(32);
        Map<String, Object> row = jdbc.sql("INSERT INTO member_qr_pass (member_id, token_hash, token_enc) "
                        + "VALUES (:m, :h, :e) RETURNING id, created_at")
                .param("m", memberId)
                .param("h", Crypto.sha256Hex(token))
                .param("e", crypto.encrypt(token))
                .query().singleRow();
        long id = ((Number) row.get("id")).longValue();
        Instant created = ((java.sql.Timestamp) row.get("created_at")).toInstant();
        return PassDto.of(id, token, "ACTIVE", created);
    }
}
