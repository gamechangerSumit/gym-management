package app.gym.auth;

/** Owner-controlled staff permission flags; the column name is a fixed whitelist (never user input). */
public enum Permission {
    RECORD_PAYMENTS("can_record_payments"),
    MANAGE_MEMBERSHIPS("can_manage_memberships"),
    EDIT_MEMBERS("can_edit_members"),
    VIEW_REPORTS("can_view_reports");

    private final String column;

    Permission(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
