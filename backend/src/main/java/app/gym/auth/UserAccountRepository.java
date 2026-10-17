package app.gym.auth;

import app.gym.common.Db;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

    private static final String COLUMNS = "id, login_id, password_hash, role, status, must_change_password, "
            + "failed_attempts, locked_until, last_login_at, is_demo, created_at";

    static final RowMapper<UserRow> MAPPER = (rs, i) -> new UserRow(
            rs.getLong("id"),
            rs.getString("login_id"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getString("status"),
            rs.getBoolean("must_change_password"),
            rs.getInt("failed_attempts"),
            Db.instant(rs, "locked_until"),
            Db.instant(rs, "last_login_at"),
            rs.getBoolean("is_demo"),
            Db.instant(rs, "created_at"));

    private final JdbcClient jdbc;

    public UserAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByLoginId(String loginId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM user_account WHERE lower(login_id) = lower(:login)")
                .param("login", loginId)
                .query(MAPPER).optional();
    }

    public Optional<UserRow> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM user_account WHERE id = :id")
                .param("id", id)
                .query(MAPPER).optional();
    }

    public long insert(String loginId, String passwordHash, Role role, String status,
                       boolean mustChangePassword, boolean demo) {
        return jdbc.sql("INSERT INTO user_account (login_id, password_hash, role, status, must_change_password, is_demo) "
                        + "VALUES (:login, :hash, :role, :status, :must, :demo) RETURNING id")
                .param("login", loginId)
                .param("hash", passwordHash)
                .param("role", role.name())
                .param("status", status)
                .param("must", mustChangePassword)
                .param("demo", demo)
                .query(Long.class).single();
    }

    public boolean existsOwner() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM user_account WHERE role = 'OWNER')")
                .query(Boolean.class).single());
    }

    public void recordFailure(long id, int attempts, Instant lockedUntil) {
        jdbc.sql("UPDATE user_account SET failed_attempts = :a, locked_until = :l, updated_at = now() WHERE id = :id")
                .param("a", attempts)
                .param("l", Db.ts(lockedUntil))
                .param("id", id)
                .update();
    }

    public void recordSuccess(long id, Instant at) {
        jdbc.sql("UPDATE user_account SET failed_attempts = 0, locked_until = NULL, last_login_at = :at, "
                        + "updated_at = now() WHERE id = :id")
                .param("at", Db.ts(at))
                .param("id", id)
                .update();
    }

    /** Sets a new password hash, clears lockout state, and optionally activates an invited account. */
    public void setPassword(long id, String hash, boolean mustChange, boolean activate) {
        jdbc.sql("UPDATE user_account SET password_hash = :h, must_change_password = :m, failed_attempts = 0, "
                        + "locked_until = NULL, status = CASE WHEN :act THEN 'ACTIVE' ELSE status END, "
                        + "updated_at = now() WHERE id = :id")
                .param("h", hash)
                .param("m", mustChange)
                .param("act", activate)
                .param("id", id)
                .update();
    }

    public void setStatus(long id, String status) {
        jdbc.sql("UPDATE user_account SET status = :s, updated_at = now() WHERE id = :id")
                .param("s", status)
                .param("id", id)
                .update();
    }

    public StaffPermissions permissions(long userId) {
        return jdbc.sql("SELECT can_record_payments, can_manage_memberships, can_edit_members, can_view_reports "
                        + "FROM staff_permission WHERE user_id = :id")
                .param("id", userId)
                .query((rs, i) -> new StaffPermissions(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)))
                .optional()
                .orElse(StaffPermissions.none());
    }

    public void savePermissions(long userId, StaffPermissions p) {
        jdbc.sql("INSERT INTO staff_permission (user_id, can_record_payments, can_manage_memberships, can_edit_members, can_view_reports) "
                        + "VALUES (:u, :a, :b, :c, :d) ON CONFLICT (user_id) DO UPDATE SET "
                        + "can_record_payments = EXCLUDED.can_record_payments, "
                        + "can_manage_memberships = EXCLUDED.can_manage_memberships, "
                        + "can_edit_members = EXCLUDED.can_edit_members, "
                        + "can_view_reports = EXCLUDED.can_view_reports")
                .param("u", userId)
                .param("a", p.canRecordPayments())
                .param("b", p.canManageMemberships())
                .param("c", p.canEditMembers())
                .param("d", p.canViewReports())
                .update();
    }

    /** Ends every server-side session of this account (e.g. after a password reset or when disabling it). */
    public void endSessions(String loginId) {
        jdbc.sql("DELETE FROM spring_session WHERE principal_name = :p").param("p", loginId).update();
    }

    public void endOtherSessions(String loginId, String keepSessionId) {
        jdbc.sql("DELETE FROM spring_session WHERE principal_name = :p AND session_id <> :s")
                .param("p", loginId)
                .param("s", keepSessionId)
                .update();
    }

    public boolean anyDemoAccount() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM user_account WHERE is_demo)")
                .query(Boolean.class).single());
    }
}
