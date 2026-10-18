package app.gym.portal;

import app.gym.attendance.AttendanceService;
import app.gym.attendance.AttendanceDtos.ScanRequest;
import app.gym.attendance.AttendanceDtos.ScanResponse;
import app.gym.attendance.GymCheckInQrService;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.payment.PaymentDtos.ReceiptDto;
import app.gym.portal.MeDtos.MeAttendancePage;
import app.gym.portal.MeDtos.MeDues;
import app.gym.portal.MeDtos.MeMembership;
import app.gym.portal.MeDtos.MePatchRequest;
import app.gym.portal.MeDtos.MePayment;
import app.gym.portal.MeDtos.MeProfile;
import app.gym.portal.MeDtos.MeQr;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "My Gym": no endpoint here takes a memberId; identity is the session. */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("@perm.member()")
public class MeController {

    private final MePortalService me;
    private final GymCheckInQrService gymQr;
    private final AttendanceService attendance;

    public MeController(MePortalService me, GymCheckInQrService gymQr, AttendanceService attendance) {
        this.me = me;
        this.gymQr = gymQr;
        this.attendance = attendance;
    }

    /** A member can only check in their own session-linked profile. */
    @PostMapping("/check-in")
    public ScanResponse checkIn(@Valid @RequestBody ScanRequest req) {
        gymQr.validate(req.token());
        return attendance.checkInFromGymQr(me.memberId());
    }

    @GetMapping
    public MeProfile profile() {
        return me.profile();
    }

    @PatchMapping
    public MeProfile patch(@Valid @RequestBody MePatchRequest req) {
        return me.patch(req);
    }

    @GetMapping("/membership")
    public MeMembership membership() {
        return me.membership();
    }

    @GetMapping("/attendance")
    public MeAttendancePage attendance(@RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        return me.attendance(Paging.of(page, size));
    }

    @GetMapping("/payments")
    public PageResponse<MePayment> payments(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return me.payments(Paging.of(page, size));
    }

    @GetMapping("/payments/{paymentId}/receipt")
    public ReceiptDto receipt(@PathVariable long paymentId) {
        return me.receipt(paymentId);
    }

    @GetMapping("/dues")
    public MeDues dues() {
        return me.duesView();
    }

    @GetMapping("/qr")
    public MeQr qr() {
        return me.qr();
    }

    @PostMapping("/qr/reissue")
    public MeQr reissueQr() {
        return me.reissueQr();
    }
}
