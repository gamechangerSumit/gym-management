package app.gym.attendance;

import app.gym.attendance.AttendanceDtos.AttendanceDto;
import app.gym.attendance.AttendanceDtos.ManualRequest;
import app.gym.attendance.AttendanceDtos.ScanRequest;
import app.gym.attendance.AttendanceDtos.ScanResponse;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reception only: STAFF and OWNER. MEMBER gets 403 (self-check-in is deliberately not offered). */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping("/scan")
    @PreAuthorize("@perm.staffOrOwner()")
    public ScanResponse scan(@Valid @RequestBody ScanRequest req) {
        return attendance.scan(req.token());
    }

    @PostMapping("/manual")
    @PreAuthorize("@perm.staffOrOwner()")
    public ScanResponse manual(@Valid @RequestBody ManualRequest req) {
        return attendance.manual(req.memberId(), req.reason());
    }

    @GetMapping
    @PreAuthorize("@perm.staffOrOwner()")
    public PageResponse<AttendanceDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return attendance.list(from, to, memberId, Paging.of(page, size));
    }
}
