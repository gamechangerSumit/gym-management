package app.gym.unit;

import static org.assertj.core.api.Assertions.assertThat;

import app.gym.common.LogScrubbingConverter;
import app.gym.common.Money;
import app.gym.report.Csv;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MiscUnitTest {

    @Test
    void moneyUsesIndianGrouping() {
        assertThat(Money.formatInr(0)).isEqualTo("₹0.00");
        assertThat(Money.formatInr(99)).isEqualTo("₹0.99");
        assertThat(Money.formatInr(100000)).isEqualTo("₹1,000.00");
        assertThat(Money.formatInr(123456789)).isEqualTo("₹12,34,567.89");
        assertThat(Money.formatInr(10000000)).isEqualTo("₹1,00,000.00");
        assertThat(Money.formatInr(-5050)).isEqualTo("-₹50.50");
    }

    @Test
    void logScrubberMasksCredentials() {
        assertThat(LogScrubbingConverter.scrub("login password=Secret123 ok")).doesNotContain("Secret123");
        assertThat(LogScrubbingConverter.scrub("{\"password\":\"Secret123\",\"x\":1}")).doesNotContain("Secret123");
        assertThat(LogScrubbingConverter.scrub("token: abcDEF123_-xyz")).doesNotContain("abcDEF123");
        assertThat(LogScrubbingConverter.scrub("Authorization: Bearer abc.def.ghi")).doesNotContain("abc.def.ghi");
        assertThat(LogScrubbingConverter.scrub("nothing sensitive here")).isEqualTo("nothing sensitive here");
        assertThat(LogScrubbingConverter.scrub(null)).isNull();
    }

    @Test
    void csvEscapesAndNeutralisesFormulas() {
        assertThat(Csv.line(Arrays.asList("a", "b,c", "d\"e", null, 12))).isEqualTo("a,\"b,c\",\"d\"\"e\",,12\r\n");
        assertThat(Csv.line(Arrays.asList("=SUM(A1)", "+1", "-2", "@x"))).isEqualTo("'=SUM(A1),'+1,'-2,'@x\r\n");
        assertThat(Csv.line(Arrays.asList(-5L))).isEqualTo("-5\r\n");
    }
}
