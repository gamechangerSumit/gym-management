package app.gym.settings;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * "Today" always means today in the gym's timezone (default Asia/Kolkata). Built on the injected Clock so tests can
 * move time. Membership start/end dates are DATEs in this timezone, end date inclusive.
 */
@Component
public class GymClock {

    private final Clock clock;
    private final GymSettingsService settings;

    public GymClock(Clock clock, GymSettingsService settings) {
        this.clock = clock;
        this.settings = settings;
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone() {
        return settings.get().zone();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone());
    }

    public LocalDate localDate(Instant instant) {
        return LocalDate.ofInstant(instant, zone());
    }

    /** First instant of a local calendar day in the gym timezone (inclusive lower bound for date-range queries). */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone()).toInstant();
    }
}
