package app.gym.dashboard;

import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record Collections(LocalDate from, LocalDate to, long totalPaise, long paymentCount, String rule) {}

    /** collections is null for staff without finance access (the dashboard is "limited, no finance" for staff). */
    public record Summary(long activeMembers, long expiringSoon, long expired, long todaysCheckIns,
                          Collections collections, int expiringSoonDays, LocalDate asOf,
                          long activeMembers7d, long openNoShowCases, long membersReturned7d,
                          long renewalsDue7d, long renewalCollectedMonthPaise) {}

    /** Every item carries the rule that produced it, so the owner can see why it is listed. */
    public record ActionItem(String type, long memberId, String memberCode, String memberName, String phone,
                             String detail, String rule) {}

    public record ActionList(LocalDate asOf, List<ActionItem> items, List<String> rules) {}

    public record HealthFactor(String key, String name, int weight, Integer value, String detail, String rule) {}

    public record HealthScore(Integer score, String label, String disclaimer, List<HealthFactor> factors) {}

    public record LeakageIndicator(String key, String title, long count, Long amountPaise, String rule) {}

    public record Leakage(LocalDate asOf, List<LeakageIndicator> indicators) {}

    public record FollowupItem(long membershipId, long memberId, String memberCode, String memberName, String phone,
                               LocalDate endDate, String planName, String contactStatus, String note,
                               java.time.Instant updatedAt) {}

    public record FollowupRequest(@jakarta.validation.constraints.NotNull Long membershipId,
                                  @jakarta.validation.constraints.NotBlank String contactStatus,
                                  @jakarta.validation.constraints.Size(max = 300) String note) {}
}
