package app.gym.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Server-side authorization helper used from @PreAuthorize("@perm.xxx()").
 * OWNER passes every check; STAFF is checked against staff_permission on every call (so revocations apply
 * immediately); MEMBER never passes.
 */
@Component("perm")
public class Perm {

    private final JdbcClient jdbc;

    public Perm(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean owner() {
        return CurrentUser.find().map(u -> u.role() == Role.OWNER).orElse(false);
    }

    public boolean staffOrOwner() {
        return CurrentUser.find().map(u -> u.role() == Role.OWNER || u.role() == Role.STAFF).orElse(false);
    }

    public boolean member() {
        return CurrentUser.find().map(u -> u.role() == Role.MEMBER).orElse(false);
    }

    public boolean recordPayments() { return has(Permission.RECORD_PAYMENTS); }
    public boolean manageMemberships() { return has(Permission.MANAGE_MEMBERSHIPS); }
    public boolean editMembers() { return has(Permission.EDIT_MEMBERS); }
    public boolean viewReports() { return has(Permission.VIEW_REPORTS); }

    /** True for the owner, or for staff who hold the flag. */
    public boolean has(Permission permission) {
        AppUser user = CurrentUser.find().orElse(null);
        if (user == null) {
            return false;
        }
        if (user.role() == Role.OWNER) {
            return true;
        }
        if (user.role() != Role.STAFF) {
            return false;
        }
        return jdbc.sql("SELECT " + permission.column() + " FROM staff_permission WHERE user_id = :id")
                .param("id", user.id())
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }
}
