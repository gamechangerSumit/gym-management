package app.gym.dashboard;

import app.gym.dashboard.DashboardDtos.ActionList;
import app.gym.dashboard.DashboardDtos.FollowupItem;
import app.gym.dashboard.DashboardDtos.FollowupRequest;
import app.gym.dashboard.DashboardDtos.HealthScore;
import app.gym.dashboard.DashboardDtos.Leakage;
import app.gym.dashboard.DashboardDtos.Summary;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService dashboard;
    private final HealthScoreService health;
    private final FollowupService followups;

    public DashboardController(DashboardService dashboard, HealthScoreService health, FollowupService followups) {
        this.dashboard = dashboard;
        this.health = health;
        this.followups = followups;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("@perm.staffOrOwner()")
    public Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboard.summary(from, to);
    }

    @GetMapping("/dashboard/action-list")
    @PreAuthorize("@perm.staffOrOwner()")
    public ActionList actionList() {
        return dashboard.actionList();
    }

    @GetMapping("/dashboard/health")
    @PreAuthorize("@perm.owner()")
    public HealthScore health() {
        return health.compute();
    }

    @GetMapping("/dashboard/leakage")
    @PreAuthorize("@perm.owner()")
    public Leakage leakage() {
        return health.leakage();
    }

    @GetMapping("/followups")
    @PreAuthorize("@perm.manageMemberships()")
    public List<FollowupItem> followups(@RequestParam(required = false) String status) {
        return followups.list(status);
    }

    @PutMapping("/followups")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.manageMemberships()")
    public void updateFollowup(@Valid @RequestBody FollowupRequest req) {
        followups.update(req.membershipId(), req.contactStatus(), req.note());
    }
}
