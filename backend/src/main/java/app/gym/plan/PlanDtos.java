package app.gym.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PlanDtos {
    private PlanDtos() {}

    public record PlanRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(1) @Max(3660) int durationDays,
            @Min(0) @Max(10_000_000_000L) long pricePaise,
            @Size(max = 500) String description,
            Boolean active) {}

    public record PlanDto(long id, String name, int durationDays, long pricePaise, String description, boolean active) {}
}
