package app.gym.attendance;

import app.gym.settings.GymClock;
import app.gym.settings.GymSettingsService;
import java.time.LocalDate;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the retention scan once per gym-local day after 06:00.
 * The scan is idempotent, so a restart or retry cannot create duplicate open cases.
 */
@Component
public class NoShowScheduler {
    private static final Logger log = LoggerFactory.getLogger(NoShowScheduler.class);
    private static final LocalTime RUN_AFTER = LocalTime.of(6, 0);

    private final NoShowService noShows;
    private final GymClock clock;
    private final GymSettingsService settings;
    private LocalDate lastSuccessfulRun;

    public NoShowScheduler(NoShowService noShows, GymClock clock, GymSettingsService settings) {
        this.noShows = noShows;
        this.clock = clock;
        this.settings = settings;
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void runDailyScan() {
        LocalDate today = clock.today();
        if (today.equals(lastSuccessfulRun) ||
                clock.now().atZone(clock.zone()).toLocalTime().isBefore(RUN_AFTER)) {
            return;
        }
        try {
            int changed = noShows.scan(settings.get().noShowThresholdDays());
            lastSuccessfulRun = today;
            log.info("Daily no-show scan completed for {} with {} affected case rows", today, changed);
        } catch (RuntimeException ex) {
            log.error("Daily no-show scan failed for {}; it will retry on the next scheduler tick", today, ex);
        }
    }
}
