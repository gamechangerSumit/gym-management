package app.gym.unit;

import static org.assertj.core.api.Assertions.assertThat;

import app.gym.membership.StatusCalculator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatusCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 30);

    private String status(String stored, LocalDate today, int soon) {
        return StatusCalculator.compute(stored, START, END, today, soon);
    }

    @Test
    void endDateIsInclusive() {
        assertThat(StatusCalculator.endDate(LocalDate.of(2026, 1, 1), 30)).isEqualTo(LocalDate.of(2026, 1, 30));
        assertThat(StatusCalculator.endDate(LocalDate.of(2026, 1, 1), 1)).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void lastDayIsStillValidAndDayAfterIsExpired() {
        assertThat(status("ACTIVE", END, 0)).isEqualTo("EXPIRING_SOON");       // last day, valid
        assertThat(StatusCalculator.isValidToday("ACTIVE", START, END, END)).isTrue();
        assertThat(status("ACTIVE", END.plusDays(1), 7)).isEqualTo("EXPIRED"); // day after
        assertThat(StatusCalculator.isValidToday("ACTIVE", START, END, END.plusDays(1))).isFalse();
    }

    @Test
    void firstDayIsValidAndDayBeforeIsScheduled() {
        assertThat(status("ACTIVE", START, 7)).isEqualTo("ACTIVE");
        assertThat(status("ACTIVE", START.minusDays(1), 7)).isEqualTo("SCHEDULED");
        assertThat(StatusCalculator.isValidToday("ACTIVE", START, END, START.minusDays(1))).isFalse();
    }

    @Test
    void expiringSoonThreshold() {
        // threshold 7: exactly 7 days left is "soon", 8 is not
        assertThat(status("ACTIVE", END.minusDays(7), 7)).isEqualTo("EXPIRING_SOON");
        assertThat(status("ACTIVE", END.minusDays(8), 7)).isEqualTo("ACTIVE");
        assertThat(status("ACTIVE", END.minusDays(1), 0)).isEqualTo("ACTIVE");
        assertThat(status("ACTIVE", END, 0)).isEqualTo("EXPIRING_SOON");
    }

    @Test
    void holdAndCancelOverrideDates() {
        assertThat(status("ON_HOLD", START.plusDays(5), 7)).isEqualTo("ON_HOLD");
        assertThat(status("CANCELLED", START.plusDays(5), 7)).isEqualTo("CANCELLED");
        assertThat(status("CANCELLED", END.plusDays(40), 7)).isEqualTo("CANCELLED");
        assertThat(StatusCalculator.isValidToday("ON_HOLD", START, END, START.plusDays(1))).isFalse();
    }

    @Test
    void daysRemaining() {
        assertThat(StatusCalculator.daysRemaining(END, END)).isZero();
        assertThat(StatusCalculator.daysRemaining(END.minusDays(3), END)).isEqualTo(3);
        assertThat(StatusCalculator.daysRemaining(END.plusDays(2), END)).isEqualTo(-2);
    }
}
