package app.gym.plan;

import app.gym.auth.Perm;
import app.gym.plan.PlanDtos.PlanDto;
import app.gym.plan.PlanDtos.PlanRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService plans;
    private final Perm perm;

    public PlanController(PlanService plans, Perm perm) {
        this.plans = plans;
        this.perm = perm;
    }

    /** Owner and staff can read plans (staff need them to assign memberships); only the owner sees inactive ones. */
    @GetMapping
    @PreAuthorize("@perm.staffOrOwner()")
    public List<PlanDto> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return plans.list(includeInactive && perm.owner());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.owner()")
    public PlanDto create(@Valid @RequestBody PlanRequest req) {
        return plans.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.owner()")
    public PlanDto update(@PathVariable long id, @Valid @RequestBody PlanRequest req) {
        return plans.update(id, req);
    }
}
