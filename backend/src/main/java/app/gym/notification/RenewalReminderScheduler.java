package app.gym.notification;

import app.gym.settings.GymClock;
import java.time.LocalDate;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "app.automation.enabled", havingValue = "true", matchIfMissing = true)
public class RenewalReminderScheduler {
    private static final Logger log=LoggerFactory.getLogger(RenewalReminderScheduler.class);
    private final RenewalReminderService reminders;
    private final GymClock clock;
    private LocalDate lastSuccessfulRun;

    public RenewalReminderScheduler(RenewalReminderService reminders,GymClock clock){
        this.reminders=reminders;this.clock=clock;
    }

    @Scheduled(fixedDelay=60_000)
    public synchronized void enqueueDaily(){
        LocalDate today=clock.today();
        if(today.equals(lastSuccessfulRun)||clock.now().atZone(clock.zone()).toLocalTime().isBefore(LocalTime.of(6,0)))return;
        try{
            int queued=reminders.enqueueDueReminders();
            lastSuccessfulRun=today;
            log.info("Renewal reminder scan completed for {} ({} new reminders)",today,queued);
        }catch(RuntimeException ex){
            log.error("Renewal reminder scan failed for {}; retrying next scheduler tick",today,ex);
        }
    }
}
