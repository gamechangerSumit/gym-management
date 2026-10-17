package app.gym.auth;

import app.gym.common.ApiException;

/** Minimal, predictable password rules (BCrypt only uses the first 72 bytes, so longer input is rejected). */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {}

    public static void validate(String password, String loginId) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_LENGTH) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password is too long (maximum " + MAX_LENGTH + " bytes).");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must contain at least one letter and one digit.");
        }
        if (loginId != null && password.equalsIgnoreCase(loginId)) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must not be the same as the login ID.");
        }
    }
}
