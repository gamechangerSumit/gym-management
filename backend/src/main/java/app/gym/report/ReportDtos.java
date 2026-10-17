package app.gym.report;

import app.gym.common.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record DayCount(LocalDate date, long count) {}

    public record AttendanceRow(String memberCode, String memberName, Instant checkedInAt, LocalDate date, String source) {}

    public record AttendanceReport(LocalDate from, LocalDate to, long totalCheckIns, long uniqueMembers,
                                   List<DayCount> byDay, PageResponse<AttendanceRow> rows) {}

    public record ExpiryRow(long memberId, String memberCode, String memberName, String phone, String planName,
                            LocalDate startDate, LocalDate endDate, String status, boolean renewed) {}

    public record ExpiryReport(LocalDate from, LocalDate to, long totalMemberships, PageResponse<ExpiryRow> rows) {}

    public record MethodTotal(String method, long count, long paise) {}

    public record CollectionRow(long paymentId, String receiptNo, Instant paidAt, String memberCode, String memberName,
                                String method, long amountPaise) {}

    /** totalPaise equals the sum of all RECORDED rows in the range; voided payments are reported separately. */
    public record CollectionsReport(LocalDate from, LocalDate to, long totalPaise, long paymentCount,
                                    long voidedCount, long voidedPaise, List<MethodTotal> byMethod,
                                    PageResponse<CollectionRow> rows) {}

    public record OutstandingRow(long memberId, String memberCode, String memberName, String phone, long billedPaise,
                                 long paidPaise, long duesPaise) {}

    public record OutstandingReport(long totalDuesPaise, long memberCount, PageResponse<OutstandingRow> rows) {}
}
