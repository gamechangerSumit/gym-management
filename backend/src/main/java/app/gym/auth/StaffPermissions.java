package app.gym.auth;

public record StaffPermissions(boolean canRecordPayments, boolean canManageMemberships,
                               boolean canEditMembers, boolean canViewReports) {

    public static StaffPermissions all() {
        return new StaffPermissions(true, true, true, true);
    }

    public static StaffPermissions none() {
        return new StaffPermissions(false, false, false, false);
    }
}
