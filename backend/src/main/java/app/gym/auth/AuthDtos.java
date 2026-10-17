package app.gym.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Size(max = 80) String loginId,
                               @NotBlank @Size(max = 200) String password) {}

    public record ActivateRequest(@NotBlank @Size(max = 200) String token,
                                  @NotBlank @Size(max = 200) String newPassword) {}

    public record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
                                        @NotBlank @Size(max = 200) String newPassword) {}

    public record ResetRequest(@NotBlank @Size(max = 80) String loginId) {}

    public record ResetConfirmRequest(@NotBlank @Size(max = 200) String token,
                                      @NotBlank @Size(max = 200) String newPassword) {}

    public record GymInfo(String name, String timezone) {}

    public record SessionInfo(boolean authenticated, Long userId, String loginId, String role,
                              boolean mustChangePassword, Long memberId, StaffPermissions permissions,
                              GymInfo gym, boolean demo) {}

    public record LinkResponse(String loginId, String url, java.time.Instant expiresAt) {}

    public record MessageResponse(String message) {}
}
