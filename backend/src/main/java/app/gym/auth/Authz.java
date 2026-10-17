package app.gym.auth;

/** SpEL expressions for @PreAuthorize (compile-time constants). */
public final class Authz {

    public static final String OWNER = "hasRole('OWNER')";
    public static final String STAFF_UP = "hasAnyRole('OWNER','STAFF')";
    public static final String MEMBER = "hasRole('MEMBER')";
    public static final String EDIT_MEMBERS = "@perm.has(authentication,'EDIT_MEMBERS')";
    public static final String MANAGE_MEMBERSHIPS = "@perm.has(authentication,'MANAGE_MEMBERSHIPS')";
    public static final String RECORD_PAYMENTS = "@perm.has(authentication,'RECORD_PAYMENTS')";
    public static final String VIEW_REPORTS = "@perm.has(authentication,'VIEW_REPORTS')";

    private Authz() {
    }
}
