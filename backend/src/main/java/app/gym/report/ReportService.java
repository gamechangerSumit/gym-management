package app.gym.report;

import app.gym.common.Db;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.membership.StatusCalculator;
import app.gym.report.ReportDtos.AttendanceReport;
import app.gym.report.ReportDtos.AttendanceRow;
import app.gym.report.ReportDtos.CollectionRow;
import app.gym.report.ReportDtos.CollectionsReport;
import app.gym.report.ReportDtos.DayCount;
import app.gym.report.ReportDtos.ExpiryReport;
import app.gym.report.ReportDtos.ExpiryRow;
import app.gym.report.ReportDtos.MethodTotal;
import app.gym.report.ReportDtos.OutstandingReport;
import app.gym.report.ReportDtos.OutstandingRow;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettingsService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Reports are built from recorded data only; every total is computed with the same filter as its detail rows. */
@Service
public class ReportService {

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;

    public ReportService(JdbcClient jdbc, GymClock clock, GymSettingsService settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
    }

    public LocalDate defaultFrom(LocalDate from) {
        return from != null ? from : clock.today().withDayOfMonth(1);
    }

    public LocalDate defaultTo(LocalDate to) {
        return to != null ? to : clock.today();
    }

    public AttendanceReport attendance(LocalDate from, LocalDate to, Paging paging) {
        long[] totals = jdbc.sql("SELECT count(*) AS total, count(DISTINCT member_id) AS uniq FROM attendance_record "
                        + "WHERE checkin_date BETWEEN :f AND :t")
                .param("f", from).param("t", to)
                .query((rs, i) -> new long[]{rs.getLong("total"), rs.getLong("uniq")}).single();
        List<DayCount> byDay = jdbc.sql("SELECT checkin_date AS d, count(*) AS c FROM attendance_record "
                        + "WHERE checkin_date BETWEEN :f AND :t GROUP BY checkin_date ORDER BY checkin_date")
                .param("f", from).param("t", to)
                .query((rs, i) -> new DayCount(Db.date(rs, "d"), rs.getLong("c"))).list();
        List<AttendanceRow> rows = jdbc.sql("SELECT m.member_code, m.full_name, a.checked_in_at, a.checkin_date, a.source "
                        + "FROM attendance_record a JOIN member_profile m ON m.id = a.member_id "
                        + "WHERE a.checkin_date BETWEEN :f AND :t ORDER BY a.checked_in_at DESC, a.id DESC LIMIT :lim OFFSET :off")
                .param("f", from).param("t", to).param("lim", paging.size()).param("off", paging.offset())
                .query((rs, i) -> new AttendanceRow(rs.getString(1), rs.getString(2), Db.instant(rs, "checked_in_at"),
                        Db.date(rs, "checkin_date"), rs.getString("source"))).list();
        return new AttendanceReport(from, to, totals[0], totals[1], byDay,
                PageResponse.of(rows, paging.page(), paging.size(), totals[0]));
    }

    public ExpiryReport expiry(LocalDate from, LocalDate to, Paging paging) {
        LocalDate today = clock.today();
        int soon = settings.get().expiringSoonDays();
        long total = jdbc.sql("SELECT count(*) FROM membership WHERE status <> 'CANCELLED' AND end_date BETWEEN :f AND :t")
                .param("f", from).param("t", to).query(Long.class).single();
        List<ExpiryRow> rows = jdbc.sql("SELECT m.id AS member_id, m.member_code, m.full_name, m.phone, p.name AS plan_name, "
                        + "ms.start_date, ms.end_date, ms.status, "
                        + "EXISTS (SELECT 1 FROM membership n WHERE n.member_id = ms.member_id AND n.status <> 'CANCELLED' "
                        + "AND n.start_date > ms.end_date) AS renewed "
                        + "FROM membership ms JOIN member_profile m ON m.id = ms.member_id "
                        + "JOIN membership_plan p ON p.id = ms.plan_id "
                        + "WHERE ms.status <> 'CANCELLED' AND ms.end_date BETWEEN :f AND :t "
                        + "ORDER BY ms.end_date, lower(m.full_name), ms.id LIMIT :lim OFFSET :off")
                .param("f", from).param("t", to).param("lim", paging.size()).param("off", paging.offset())
                .query((rs, i) -> {
                    LocalDate start = Db.date(rs, "start_date");
                    LocalDate end = Db.date(rs, "end_date");
                    return new ExpiryRow(rs.getLong("member_id"), rs.getString("member_code"), rs.getString("full_name"),
                            rs.getString("phone"), rs.getString("plan_name"), start, end,
                            StatusCalculator.compute(rs.getString("status"), start, end, today, soon),
                            rs.getBoolean("renewed"));
                }).list();
        return new ExpiryReport(from, to, total, PageResponse.of(rows, paging.page(), paging.size(), total));
    }

    public CollectionsReport collections(LocalDate from, LocalDate to, Paging paging) {
        var fromTs = Db.ts(clock.startOfDay(from));
        var toTs = Db.ts(clock.startOfDay(to.plusDays(1)));
        List<MethodTotal> byMethod = jdbc.sql("SELECT method, count(*) AS c, COALESCE(sum(amount_paise), 0) AS s FROM payment "
                        + "WHERE status = 'RECORDED' AND paid_at >= :f AND paid_at < :t GROUP BY method ORDER BY method")
                .param("f", fromTs).param("t", toTs)
                .query((rs, i) -> new MethodTotal(rs.getString("method"), rs.getLong("c"), rs.getLong("s"))).list();
        long totalPaise = byMethod.stream().mapToLong(MethodTotal::paise).sum();
        long count = byMethod.stream().mapToLong(MethodTotal::count).sum();
        long[] voided = jdbc.sql("SELECT count(*) AS c, COALESCE(sum(amount_paise), 0) AS s FROM payment "
                        + "WHERE status = 'VOID' AND paid_at >= :f AND paid_at < :t")
                .param("f", fromTs).param("t", toTs)
                .query((rs, i) -> new long[]{rs.getLong("c"), rs.getLong("s")}).single();
        List<CollectionRow> rows = jdbc.sql("SELECT p.id, p.receipt_no, p.paid_at, m.member_code, m.full_name, p.method, p.amount_paise "
                        + "FROM payment p JOIN member_profile m ON m.id = p.member_id "
                        + "WHERE p.status = 'RECORDED' AND p.paid_at >= :f AND p.paid_at < :t "
                        + "ORDER BY p.paid_at DESC, p.id DESC LIMIT :lim OFFSET :off")
                .param("f", fromTs).param("t", toTs).param("lim", paging.size()).param("off", paging.offset())
                .query((rs, i) -> new CollectionRow(rs.getLong("id"), rs.getString("receipt_no"), Db.instant(rs, "paid_at"),
                        rs.getString("member_code"), rs.getString("full_name"), rs.getString("method"),
                        rs.getLong("amount_paise"))).list();
        return new CollectionsReport(from, to, totalPaise, count, voided[0], voided[1], byMethod,
                PageResponse.of(rows, paging.page(), paging.size(), count));
    }

    public OutstandingReport outstanding(Paging paging) {
        long[] totals = jdbc.sql("SELECT count(*) AS c, COALESCE(sum(dues_paise), 0) AS s FROM member_dues_v WHERE dues_paise > 0")
                .query((rs, i) -> new long[]{rs.getLong("c"), rs.getLong("s")}).single();
        List<OutstandingRow> rows = jdbc.sql("SELECT m.id, m.member_code, m.full_name, m.phone, d.billed_paise, d.paid_paise, d.dues_paise "
                        + "FROM member_dues_v d JOIN member_profile m ON m.id = d.member_id WHERE d.dues_paise > 0 "
                        + "ORDER BY d.dues_paise DESC, lower(m.full_name), m.id LIMIT :lim OFFSET :off")
                .param("lim", paging.size()).param("off", paging.offset())
                .query((rs, i) -> new OutstandingRow(rs.getLong("id"), rs.getString("member_code"), rs.getString("full_name"),
                        rs.getString("phone"), rs.getLong("billed_paise"), rs.getLong("paid_paise"),
                        rs.getLong("dues_paise"))).list();
        return new OutstandingReport(totals[1], totals[0], PageResponse.of(rows, paging.page(), paging.size(), totals[0]));
    }
}
