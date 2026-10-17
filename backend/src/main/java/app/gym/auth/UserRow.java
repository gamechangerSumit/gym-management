package app.gym.auth;

import java.time.Instant;

public record UserRow(long id, String loginId, String passwordHash, Role role, String status,
                      boolean mustChangePassword, int failedAttempts, Instant lockedUntil,
                      Instant lastLoginAt, boolean demo, Instant createdAt) {
}
