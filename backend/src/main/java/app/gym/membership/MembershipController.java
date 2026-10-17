package app.gym.membership;

import app.gym.membership.MembershipDtos.AssignRequest;
import app.gym.membership.MembershipDtos.MembershipDto;
import app.gym.membership.MembershipDtos.ReasonRequest;
import app.gym.membership.MembershipDtos.RenewRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MembershipController {

    private final MembershipService memberships;

    public MembershipController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @GetMapping("/members/{memberId}/memberships")
    @PreAuthorize("@perm.staffOrOwner()")
    public List<MembershipDto> list(@PathVariable long memberId) {
        return memberships.listForMember(memberId);
    }

    @PostMapping("/members/{memberId}/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.manageMemberships()")
    public MembershipDto assign(@PathVariable long memberId, @Valid @RequestBody AssignRequest req) {
        return memberships.assign(memberId, req);
    }

    @PostMapping("/memberships/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.manageMemberships()")
    public MembershipDto renew(@PathVariable long id, @Valid @RequestBody(required = false) RenewRequest req) {
        return memberships.renew(id, req == null ? new RenewRequest(null, null, null) : req);
    }

    @PostMapping("/memberships/{id}/hold")
    @PreAuthorize("@perm.manageMemberships()")
    public MembershipDto hold(@PathVariable long id, @Valid @RequestBody ReasonRequest req) {
        return memberships.hold(id, req.reason());
    }

    @PostMapping("/memberships/{id}/resume")
    @PreAuthorize("@perm.manageMemberships()")
    public MembershipDto resume(@PathVariable long id) {
        return memberships.resume(id);
    }

    @PostMapping("/memberships/{id}/cancel")
    @PreAuthorize("@perm.owner()")
    public MembershipDto cancel(@PathVariable long id, @Valid @RequestBody ReasonRequest req) {
        return memberships.cancel(id, req.reason());
    }
}
