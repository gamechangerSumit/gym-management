package app.gym.member;

import app.gym.common.ApiException;

/**
 * Normalises phone numbers to a canonical "+<country><number>" form so the unique-phone rule cannot be bypassed with
 * spaces, dashes or a missing country code. Ten-digit numbers get the gym's default country code (India: 91).
 */
public final class PhoneNormalizer {
    private PhoneNormalizer() {}

    public static String normalize(String raw, String defaultCountryCode) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String s = raw.trim();
        if (!s.matches("^\\+?[0-9 ()\\-.]+$")) {
            throw invalid();
        }
        boolean plus = s.startsWith("+");
        String digits = s.replaceAll("\\D", "");
        String cc = defaultCountryCode == null ? "91" : defaultCountryCode;

        if (!plus && digits.startsWith("00")) {
            digits = digits.substring(2);
            plus = true;
        }
        String result;
        if (plus) {
            result = digits;
        } else if (digits.length() == 10) {
            result = cc + digits;
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            result = cc + digits.substring(1);
        } else if (digits.length() == cc.length() + 10 && digits.startsWith(cc)) {
            result = digits;
        } else {
            throw invalid();
        }
        if (result.startsWith("0") || result.length() < 8 || result.length() > 15) {
            throw invalid();
        }
        return "+" + result;
    }

    private static ApiException invalid() {
        return ApiException.badRequest("INVALID_PHONE",
                "Enter a valid phone number, e.g. 98765 43210 or +91 98765 43210.");
    }
}
