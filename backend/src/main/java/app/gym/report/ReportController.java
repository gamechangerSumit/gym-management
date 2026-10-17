package app.gym.report;

import app.gym.common.ApiException;
import app.gym.common.Paging;
import app.gym.report.ReportDtos.AttendanceReport;
import app.gym.report.ReportDtos.CollectionsReport;
import app.gym.report.ReportDtos.ExpiryReport;
import app.gym.report.ReportDtos.OutstandingReport;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner, or staff holding can_view_reports. Finance reports (collections, outstanding) use the same gate. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("@perm.viewReports()")
public class ReportController {

    private static final int EXPORT_LIMIT = 10_000;

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate f = reports.defaultFrom(from);
        LocalDate t = reports.defaultTo(to);
        if (t.isBefore(f)) {
            throw ApiException.badRequest("INVALID_RANGE", "The end date must not be before the start date.");
        }
        if (f.plusDays(366).isBefore(t)) {
            throw ApiException.badRequest("INVALID_RANGE", "Please choose a range of at most one year.");
        }
        return new LocalDate[]{f, t};
    }

    @GetMapping("/attendance")
    public AttendanceReport attendance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        LocalDate[] r = range(from, to);
        return reports.attendance(r[0], r[1], Paging.of(page, size));
    }

    @GetMapping("/expiry")
    public ExpiryReport expiry(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        LocalDate[] r = range(from, to);
        return reports.expiry(r[0], r[1], Paging.of(page, size));
    }

    @GetMapping("/collections")
    public CollectionsReport collections(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        LocalDate[] r = range(from, to);
        return reports.collections(r[0], r[1], Paging.of(page, size));
    }

    @GetMapping("/outstanding")
    public OutstandingReport outstanding(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return reports.outstanding(Paging.of(page, size));
    }

    // ------------------------------------------------------------------ CSV

    @GetMapping("/attendance/export")
    public ResponseEntity<String> attendanceCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        StringBuilder sb = new StringBuilder(Csv.line(List.of("Member code", "Member", "Checked in at (UTC)", "Date", "Source")));
        reports.attendance(r[0], r[1], new Paging(0, EXPORT_LIMIT)).rows().items().forEach(x ->
                sb.append(Csv.line(java.util.Arrays.asList(x.memberCode(), x.memberName(), x.checkedInAt(), x.date(), x.source()))));
        return csv("attendance", r, sb);
    }

    @GetMapping("/expiry/export")
    public ResponseEntity<String> expiryCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        StringBuilder sb = new StringBuilder(Csv.line(List.of("Member code", "Member", "Phone", "Plan", "Start", "End", "Status", "Renewed")));
        reports.expiry(r[0], r[1], new Paging(0, EXPORT_LIMIT)).rows().items().forEach(x ->
                sb.append(Csv.line(java.util.Arrays.asList(x.memberCode(), x.memberName(), x.phone(), x.planName(),
                        x.startDate(), x.endDate(), x.status(), x.renewed() ? "yes" : "no"))));
        return csv("expiry", r, sb);
    }

    @GetMapping("/collections/export")
    public ResponseEntity<String> collectionsCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        StringBuilder sb = new StringBuilder(Csv.line(List.of("Receipt", "Paid at (UTC)", "Member code", "Member", "Method", "Amount (paise)")));
        reports.collections(r[0], r[1], new Paging(0, EXPORT_LIMIT)).rows().items().forEach(x ->
                sb.append(Csv.line(java.util.Arrays.asList(x.receiptNo(), x.paidAt(), x.memberCode(), x.memberName(),
                        x.method(), x.amountPaise()))));
        return csv("collections", r, sb);
    }

    @GetMapping("/outstanding/export")
    public ResponseEntity<String> outstandingCsv() {
        StringBuilder sb = new StringBuilder(Csv.line(List.of("Member code", "Member", "Phone", "Billed (paise)", "Paid (paise)", "Dues (paise)")));
        reports.outstanding(new Paging(0, EXPORT_LIMIT)).rows().items().forEach(x ->
                sb.append(Csv.line(java.util.Arrays.asList(x.memberCode(), x.memberName(), x.phone(), x.billedPaise(),
                        x.paidPaise(), x.duesPaise()))));
        return csv("outstanding", null, sb);
    }

    private static ResponseEntity<String> csv(String name, LocalDate[] range, StringBuilder body) {
        String file = name + (range == null ? "" : "-" + range[0] + "_" + range[1]) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body.toString());
    }
}
