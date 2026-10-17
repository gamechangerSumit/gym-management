package app.gym.membership;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class MembershipDtos {
    private MembershipDtos() {}

    public record AssignRequest(@NotNull Long planId, LocalDate startDate,
                                @Min(0) @Max(10_000_000_000L) Long pricePaise) {}

    public record RenewRequest(Long planId, LocalDate startDate,
                               @Min(0) @Max(10_000_000_000L) Long pricePaise) {}

    public record ReasonRequest(@NotBlank @Size(max = 300) String reason) {}

    /**
     * status is derived (SCHEDULED / ACTIVE / EXPIRING_SOON / EXPIRED / ON_HOLD / CANCELLED);
     * storedStatus is the lifecycle flag (ACTIVE / ON_HOLD / CANCELLED).
     */
    public record MembershipDto(long id, long memberId, long planId, String planName, LocalDate startDate,
                                LocalDate endDate, long pricePaise, String storedStatus, String status,
                                Long renewedFromId, long daysRemaining, String holdReason, String cancelReason,
                                Instant createdAt) {}
}
