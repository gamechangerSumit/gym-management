package app.gym.qr;

import app.gym.audit.AuditService;
import app.gym.member.MemberService;
import app.gym.qr.QrDtos.PassDto;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reception-side pass handling. Members use /me/qr instead. */
@RestController
@RequestMapping("/api/v1")
public class QrController {

    private final QrPassService passes;
    private final MemberService members;
    private final AuditService audit;

    public QrController(QrPassService passes, MemberService members, AuditService audit) {
        this.passes = passes;
        this.members = members;
        this.audit = audit;
    }

    /** Shows (or first issues) the member's pass so reception can display or print it. Every view is audited. */
    @GetMapping("/members/{memberId}/qr")
    @PreAuthorize("@perm.staffOrOwner()")
    public PassDto view(@PathVariable long memberId) {
        members.basic(memberId);
        PassDto pass = passes.currentPass(memberId);
        audit.record("QR_VIEWED", "MEMBER", memberId, Map.of("passId", pass.passId()));
        return pass;
    }

    @PostMapping("/qr/{passId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.staffOrOwner()")
    public void revoke(@PathVariable long passId) {
        passes.revoke(passId);
    }

    @PostMapping("/qr/{passId}/reissue")
    @PreAuthorize("@perm.staffOrOwner()")
    public PassDto reissue(@PathVariable long passId) {
        long memberId = memberOfPass(passId);
        return passes.reissue(memberId);
    }

    private long memberOfPass(long passId) {
        return passes.memberOfPass(passId);
    }
}
