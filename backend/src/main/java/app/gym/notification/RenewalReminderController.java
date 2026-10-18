package app.gym.notification;

import app.gym.notification.RenewalReminderService.Reminder;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reminders")
@PreAuthorize("@perm.staffOrOwner()")
public class RenewalReminderController {
    private final RenewalReminderService service;
    public RenewalReminderController(RenewalReminderService service){this.service=service;}

    @GetMapping
    public List<Reminder> queue(){return service.queue();}

    @PostMapping("/{id}/mark-sent")
    @PreAuthorize("@perm.manageMemberships()")
    public void markSent(@PathVariable long id){service.markSent(id);}

    @PostMapping("/scan")
    @PreAuthorize("@perm.owner()")
    public int scan(){return service.enqueueDueReminders();}
}
