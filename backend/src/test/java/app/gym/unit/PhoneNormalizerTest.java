package app.gym.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.gym.common.ApiException;
import app.gym.member.PhoneNormalizer;
import org.junit.jupiter.api.Test;

class PhoneNormalizerTest {

    @Test
    void tenDigitNumbersGetDefaultCountryCode() {
        assertThat(PhoneNormalizer.normalize("9876543210", "91")).isEqualTo("+919876543210");
    }

    @Test
    void formattingIsRemoved() {
        assertThat(PhoneNormalizer.normalize("98765 43210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNormalizer.normalize("(987) 654-3210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNormalizer.normalize("+91 98765-43210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNormalizer.normalize("0 98765 43210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNormalizer.normalize("919876543210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNormalizer.normalize("00919876543210", "91")).isEqualTo("+919876543210");
    }

    @Test
    void differentFormatsOfTheSameNumberAreEqual() {
        assertThat(PhoneNormalizer.normalize("98765 43210", "91"))
                .isEqualTo(PhoneNormalizer.normalize("+919876543210", "91"));
    }

    @Test
    void otherCountriesKeepTheirCode() {
        assertThat(PhoneNormalizer.normalize("+44 7911 123456", "91")).isEqualTo("+447911123456");
    }

    @Test
    void invalidNumbersAreRejected() {
        for (String bad : new String[]{"", "   ", "abc", "12345", "98765abc10", "+0123456789", "+1234567",
                "1234567890123456789", "98765 43210 9"}) {
            assertThatThrownBy(() -> PhoneNormalizer.normalize(bad, "91"))
                    .as("input '%s'", bad)
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> PhoneNormalizer.normalize(null, "91")).isInstanceOf(ApiException.class);
    }
}
