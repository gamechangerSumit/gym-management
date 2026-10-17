package app.gym.membership;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Derives the display status of a membership from its stored lifecycle flag and its dates.
 * The end date is INCLUSIVE: a membership ending on D is still valid on D and EXPIRED from D+1.
 */
public final class StatusCalculator {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRING_SOON = "EXPIRING_SOON";
    public static final String EXPIRED = "EXPIRED";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String CANCELLED = "CANCELLED";

    private StatusCalculator() {}

    /** @param stored the stored flag: ACTIVE, ON_HOLD or CANCELLED. */
    public static String compute(String stored, LocalDate start, LocalDate end, LocalDate today, int expiringSoonDays) {
        if (CANCELLED.equals(stored)) {
            return CANCELLED;
        }
        if (ON_HOLD.equals(stored)) {
            return ON_HOLD;
        }
        if (today.isBefore(start)) {
            return SCHEDULED;
        }
        if (today.isAfter(end)) {
            return EXPIRED;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, end); // 0 on the last valid day
        return daysLeft <= expiringSoonDays ? EXPIRING_SOON : ACTIVE;
    }

    /** True when a check-in is allowed today: not held/cancelled and today is within [start, end]. */
    public static boolean isValidToday(String stored, LocalDate start, LocalDate end, LocalDate today) {
        return ACTIVE.equals(stored) && !today.isBefore(start) && !today.isAfter(end);
    }

    /** End date = start + duration - 1 (inclusive). A 30-day plan starting Jan 1 ends Jan 30. */
    public static LocalDate endDate(LocalDate start, int durationDays) {
        return start.plusDays(durationDays - 1L);
    }

    /** Days remaining including today's validity: 0 on the last day, negative once expired. */
    public static long daysRemaining(LocalDate today, LocalDate end) {
        return ChronoUnit.DAYS.between(today, end);
    }
}
