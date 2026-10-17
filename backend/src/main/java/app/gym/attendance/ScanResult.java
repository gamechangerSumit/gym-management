package app.gym.attendance;

/** Explicit outcome codes returned by the scan and manual check-in endpoints. */
public enum ScanResult {
    CHECKED_IN,
    DUPLICATE_RECENT,
    EXPIRED,
    NO_ACTIVE_MEMBERSHIP,
    ON_HOLD,
    MEMBER_INACTIVE,
    INVALID_TOKEN,
    PASS_REVOKED
}
