package app.gym.portal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Member-facing DTOs. They are separate types from the staff DTOs on purpose: staff notes, recorder identity and
 * other internal fields do not exist here, so they cannot leak by accident.
 */
public final class MeDtos {
    private MeDtos() {}

    public record MeProfile(String memberCode, String fullName, String phone, String email, LocalDate joinDate,
                            String emergencyContact, String status, boolean canEditEmail) {}

    /** Only editable fields. Unknown fields (name, phone, ...) are rejected by strict JSON binding. */
    public record MePatchRequest(@Email @Size(max = 160) String email, @Size(max = 200) String emergencyContact) {}

    public record MeMembership(boolean hasMembership, String planName, LocalDate startDate, LocalDate endDate,
                               String status, Long daysRemaining, long visitsThisMonth, long visitsThisWeek,
                               int weeklyVisitGoal, int currentStreakWeeks, long duesPaise) {
        public MeMembership(boolean hasMembership, String planName, LocalDate startDate, LocalDate endDate,
                            String status, Long daysRemaining, long visitsThisMonth, long duesPaise) {
            this(hasMembership, planName, startDate, endDate, status, daysRemaining, visitsThisMonth, 0, 3, 0, duesPaise);
        }
    }

    public record MonthVisits(String month, long visits) {}

    public record MeAttendanceItem(long id, Instant checkedInAt, LocalDate date, String source) {}

    public record MeAttendancePage(List<MeAttendanceItem> items, int page, int size, long totalItems, int totalPages,
                                   long visitsThisMonth, List<MonthVisits> monthly) {}

    public record MePayment(long id, String receiptNo, long amountPaise, Instant paidAt, String method, String status) {}

    public record MeDues(long billedPaise, long paidPaise, long duesPaise) {}

    public record MeQr(String token, String payload, Instant issuedAt) {}
}
