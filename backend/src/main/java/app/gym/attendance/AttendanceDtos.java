package app.gym.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class AttendanceDtos {
    private AttendanceDtos() {}

    public record ScanRequest(@NotBlank @Size(max = 200) String token) {}

    public record ManualRequest(@NotNull Long memberId) {}

    public record ScanResponse(ScanResult result, String message, Long memberId, String memberCode, String memberName,
                               LocalDate membershipEndDate, Instant checkedInAt) {}

    public record AttendanceDto(long id, long memberId, String memberCode, String memberName, Instant checkedInAt,
                                LocalDate date, String source, String recordedBy) {}
}
