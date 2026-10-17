package app.gym.member;

import app.gym.auth.AuthDtos.LinkResponse;
import app.gym.auth.InvitationService;
import app.gym.auth.Perm;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.member.MemberDtos.CreateMemberRequest;
import app.gym.member.MemberDtos.DeactivateRequest;
import app.gym.member.MemberDtos.InviteRequest;
import app.gym.member.MemberDtos.MemberDetail;
import app.gym.member.MemberDtos.MemberSummary;
import app.gym.member.MemberDtos.UpdateMemberRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService members;
    private final InvitationService invitations;
    private final Perm perm;

    public MemberController(MemberService members, InvitationService invitations, Perm perm) {
        this.members = members;
        this.invitations = invitations;
        this.perm = perm;
    }

    @GetMapping
    @PreAuthorize("@perm.staffOrOwner()")
    public PageResponse<MemberSummary> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return members.search(q, status, Paging.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.staffOrOwner()")
    public MemberDetail get(@PathVariable long id) {
        // Dues are finance data: only for the owner or staff who can record payments / manage memberships.
        boolean dues = perm.recordPayments() || perm.manageMemberships();
        return members.detail(id, dues);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.editMembers()")
    public MemberDetail create(@Valid @RequestBody CreateMemberRequest req) {
        return members.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.editMembers()")
    public MemberDetail update(@PathVariable long id, @Valid @RequestBody UpdateMemberRequest req) {
        return members.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("@perm.editMembers()")
    public MemberDetail deactivate(@PathVariable long id, @Valid @RequestBody(required = false) DeactivateRequest req) {
        return members.deactivate(id, req == null ? null : req.reason());
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("@perm.editMembers()")
    public MemberDetail reactivate(@PathVariable long id) {
        return members.reactivate(id);
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize("@perm.owner()")
    public LinkResponse invite(@PathVariable long id, @Valid @RequestBody(required = false) InviteRequest req) {
        return invitations.inviteMember(id, req == null ? null : req.loginId());
    }
}
