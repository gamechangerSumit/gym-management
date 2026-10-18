package app.gym.portal;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.membership.StatusCalculator;
import app.gym.payment.DuesCalculator.Dues;
import app.gym.payment.DuesService;
import app.gym.payment.PaymentDtos.ReceiptDto;
import app.gym.payment.ReceiptService;
import app.gym.portal.MeDtos.MeAttendanceItem;
import app.gym.portal.MeDtos.MeAttendancePage;
import app.gym.portal.MeDtos.MeDues;
import app.gym.portal.MeDtos.MeMembership;
import app.gym.portal.MeDtos.MePatchRequest;
import app.gym.portal.MeDtos.MePayment;
import app.gym.portal.MeDtos.MeProfile;
import app.gym.portal.MeDtos.MeQr;
import app.gym.portal.MeDtos.MonthVisits;
import app.gym.qr.QrDtos.PassDto;
import app.gym.qr.QrPassService;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettings;
import app.gym.settings.GymSettingsService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method derives the member from the authenticated session. No method accepts a member id, so one member can
 * never address another member's data through URL or body manipulation.
 */
@Service
public class MePortalService {

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;
    private final DuesService dues;
    private final QrPassService qr;
    private final ReceiptService receipts;
    private final AuditService audit;

    public MePortalService(JdbcClient jdbc, GymClock clock, GymSettingsService settings, DuesService dues,
                           QrPassService qr, ReceiptService receipts, AuditService audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.dues = dues;
        this.qr = qr;
        this.receipts = receipts;
        this.audit = audit;
    }

    /** Resolves the member profile linked to the logged-in account. */
    public long memberId() {
        long userId = CurrentUser.id();
        return jdbc.sql("SELECT id FROM member_profile WHERE user_id = :u AND status = 'ACTIVE'")
                .param("u", userId).query(Long.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_MEMBER_PROFILE",
                        "No active member profile is linked to this account."));
    }

    public MeProfile profile() {
        long id = memberId();
        boolean canEditEmail = settings.get().memberCanEditEmail();
        return jdbc.sql("SELECT member_code, full_name, phone, email, join_date, emergency_contact, status, " +
                        "transactional_messages_enabled, marketing_consent "
                        + "FROM member_profile WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new MeProfile(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        Db.date(rs, "join_date"), rs.getString(6), rs.getString(7), rs.getBoolean(8), rs.getBoolean(9), canEditEmail))
                .single();
    }

    @Transactional
    public MeProfile patch(MePatchRequest req) {
        long id = memberId();
        if (req.email() == null && req.emergencyContact() == null
                && req.transactionalMessagesEnabled() == null && req.marketingConsent() == null) {
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "Provide a field to update.");
        }
        if (req.email() != null && !settings.get().memberCanEditEmail()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FIELD_NOT_EDITABLE",
                    "Your gym does not allow members to change their email. Please ask reception.");
        }
        if (req.email() != null) {
            String email = req.email().isBlank() ? null : req.email().trim();
            jdbc.sql("UPDATE member_profile SET email = :e, updated_at = now() WHERE id = :id")
                    .param("e", email).param("id", id).update();
        }
        if (req.emergencyContact() != null) {
            String ec = req.emergencyContact().isBlank() ? null : req.emergencyContact().trim();
            jdbc.sql("UPDATE member_profile SET emergency_contact = :e, updated_at = now() WHERE id = :id")
                    .param("e", ec).param("id", id).update();
        }
        if (req.transactionalMessagesEnabled() != null) {
            jdbc.sql("UPDATE member_profile SET transactional_messages_enabled=:enabled, updated_at=now() WHERE id=:id")
                .param("enabled", req.transactionalMessagesEnabled()).param("id", id).update();
            if (!req.transactionalMessagesEnabled()) {
                jdbc.sql("UPDATE notification_outbox SET status='CANCELLED', updated_at=now() WHERE member_id=:id AND status='QUEUED'")
                    .param("id", id).update();
            }
        }
        if (req.marketingConsent() != null) {
            jdbc.sql("UPDATE member_profile SET marketing_consent=:consent, updated_at=now() WHERE id=:id")
                .param("consent", req.marketingConsent()).param("id", id).update();
        }
        audit.record("MEMBER_SELF_UPDATED", "MEMBER", id, Map.of(
                "emailChanged", req.email() != null, "emergencyContactChanged", req.emergencyContact() != null,
                "transactionalMessagesPreferenceChanged", req.transactionalMessagesEnabled() != null,
                "marketingConsentChanged", req.marketingConsent() != null));
        return profile();
    }

    public MeMembership membership() {
        long id = memberId();
        LocalDate today = clock.today();
        int soon = settings.get().expiringSoonDays();
        long visits = visitsThisMonth(id, today);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long visitsThisWeek = visitsBetween(id, weekStart, today);
        int weeklyGoal = settings.get().weeklyVisitGoal();
        boolean weeklyGoalPaused = isWeekPaused(id, weekStart, today);
        int streakWeeks = currentStreakWeeks(id, today, weeklyGoal);
        long duesPaise = dues.duesFor(id).duesPaise();
        return jdbc.sql("SELECT p.name, ms.start_date, ms.end_date, ms.status FROM membership ms "
                        + "JOIN membership_plan p ON p.id = ms.plan_id "
                        + "WHERE ms.member_id = :m AND ms.status <> 'CANCELLED' "
                        + "ORDER BY (ms.start_date <= :t AND ms.end_date >= :t) DESC, ms.end_date DESC, ms.id DESC LIMIT 1")
                .param("m", id).param("t", today)
                .query((rs, i) -> {
                    LocalDate start = Db.date(rs, "start_date");
                    LocalDate end = Db.date(rs, "end_date");
                    return new MeMembership(true, rs.getString("name"), start, end,
                            StatusCalculator.compute(rs.getString("status"), start, end, today, soon),
                            StatusCalculator.daysRemaining(today, end), visits, visitsThisWeek, weeklyGoal, streakWeeks, weeklyGoalPaused, duesPaise);
                })
                .optional()
                .orElse(new MeMembership(false, null, null, null, null, null, visits, visitsThisWeek, weeklyGoal, streakWeeks, weeklyGoalPaused, duesPaise));
    }

    public MeAttendancePage attendance(Paging paging) {
        long id = memberId();
        LocalDate today = clock.today();
        long total = jdbc.sql("SELECT count(*) FROM attendance_record WHERE member_id = :m")
                .param("m", id).query(Long.class).single();
        List<MeAttendanceItem> items = jdbc.sql("SELECT id, checked_in_at, checkin_date, source FROM attendance_record "
                        + "WHERE member_id = :m ORDER BY checked_in_at DESC, id DESC LIMIT :lim OFFSET :off")
                .param("m", id).param("lim", paging.size()).param("off", paging.offset())
                .query((rs, i) -> new MeAttendanceItem(rs.getLong(1), Db.instant(rs, "checked_in_at"),
                        Db.date(rs, "checkin_date"), rs.getString(4)))
                .list();
        PageResponse<MeAttendanceItem> page = PageResponse.of(items, paging.page(), paging.size(), total);
        return new MeAttendancePage(page.items(), page.page(), page.size(), page.totalItems(), page.totalPages(),
                visitsThisMonth(id, today), monthly(id, today));
    }

    private long visitsBetween(long memberId, LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT count(DISTINCT checkin_date) FROM attendance_record WHERE member_id = :m AND checkin_date BETWEEN :from AND :to")
                .param("m", memberId).param("from", from).param("to", to)
                .query(Long.class).single();
    }

    /** Consecutive calendar weeks that reached the configured goal; current incomplete week does not reset a streak. */
    private int currentStreakWeeks(long memberId, LocalDate today, int goal) {
        LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate from = currentWeek.minusWeeks(52);
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.sql("SELECT date_trunc('week', checkin_date)::date AS week_start, count(DISTINCT checkin_date) AS visits " +
                        "FROM attendance_record WHERE member_id = :m AND checkin_date >= :from AND checkin_date <= :today GROUP BY 1")
                .param("m", memberId).param("from", from).param("today", today)
                .query((rs, row) -> {
                    counts.put(Db.date(rs, "week_start"), rs.getLong("visits"));
                    return 0;
                }).list();
        Set<LocalDate> pausedWeeks = new HashSet<>();
        jdbc.sql("SELECT h.held_at::date AS held_on, COALESCE(h.resumed_at::date,:today) AS resumed_on " +
                        "FROM membership_hold_event h JOIN membership ms ON ms.id=h.membership_id " +
                        "WHERE ms.member_id=:m AND h.held_at::date <= :today AND COALESCE(h.resumed_at::date,:today) >= :from")
                .param("m",memberId).param("from",from).param("today",today)
                .query((rs,row)->{
                    LocalDate first=Db.date(rs,"held_on").with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    LocalDate last=Db.date(rs,"resumed_on").with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    for(LocalDate w=first;!w.isAfter(last);w=w.plusWeeks(1))pausedWeeks.add(w);
                    return 0;
                }).list();
        LocalDate cursor = counts.getOrDefault(currentWeek, 0L) >= goal || pausedWeeks.contains(currentWeek)
                ? currentWeek : currentWeek.minusWeeks(1);
        int streak = 0;
        while (!cursor.isBefore(from)) {
            if (pausedWeeks.contains(cursor)) { cursor=cursor.minusWeeks(1); continue; }
            if (counts.getOrDefault(cursor, 0L) < goal) break;
            streak++;
            cursor = cursor.minusWeeks(1);
        }
        return streak;
    }

    private boolean isWeekPaused(long memberId, LocalDate from, LocalDate to) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM membership_hold_event h " +
                        "JOIN membership ms ON ms.id=h.membership_id WHERE ms.member_id=:m " +
                        "AND h.held_at::date <= :to AND COALESCE(h.resumed_at::date,:to) >= :from)")
                .param("m",memberId).param("from",from).param("to",to).query(Boolean.class).single());
    }

    private long visitsThisMonth(long memberId, LocalDate today) {
        return jdbc.sql("SELECT count(*) FROM attendance_record WHERE member_id = :m AND checkin_date >= :from "
                        + "AND checkin_date <= :to")
                .param("m", memberId).param("from", today.withDayOfMonth(1)).param("to", today)
                .query(Long.class).single();
    }

    /** Visits per calendar month for the last six months (zero-filled), oldest first. */
    private List<MonthVisits> monthly(long memberId, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        LocalDate since = current.minusMonths(5).atDay(1);
        Map<String, Long> counts = new HashMap<>();
        jdbc.sql("SELECT to_char(checkin_date, 'YYYY-MM') AS ym, count(*) AS c FROM attendance_record "
                        + "WHERE member_id = :m AND checkin_date >= :since GROUP BY 1")
                .param("m", memberId).param("since", since)
                .query((rs, i) -> {
                    counts.put(rs.getString("ym"), rs.getLong("c"));
                    return 0;
                }).list();
        List<MonthVisits> out = new ArrayList<>();
        for (int k = 5; k >= 0; k--) {
            String key = current.minusMonths(k).toString();
            out.add(new MonthVisits(key, counts.getOrDefault(key, 0L)));
        }
        return out;
    }

    public PageResponse<MePayment> payments(Paging paging) {
        long id = memberId();
        long total = jdbc.sql("SELECT count(*) FROM payment WHERE member_id = :m")
                .param("m", id).query(Long.class).single();
        List<MePayment> items = jdbc.sql("SELECT id, receipt_no, amount_paise, paid_at, method, status FROM payment "
                        + "WHERE member_id = :m ORDER BY paid_at DESC, id DESC LIMIT :lim OFFSET :off")
                .param("m", id).param("lim", paging.size()).param("off", paging.offset())
                .query((rs, i) -> new MePayment(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        Db.instant(rs, "paid_at"), rs.getString(5), rs.getString(6)))
                .list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    /** 404 (not 403) when the payment belongs to someone else, so ids cannot be probed. */
    public ReceiptDto receipt(long paymentId) {
        long id = memberId();
        boolean mine = Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM payment WHERE id = :p AND member_id = :m)")
                .param("p", paymentId).param("m", id).query(Boolean.class).single());
        if (!mine) {
            throw ApiException.notFound("Receipt not found.");
        }
        return receipts.receipt(paymentId);
    }

    public MeDues duesView() {
        Dues d = dues.duesFor(memberId());
        return new MeDues(d.billedPaise(), d.paidPaise(), d.duesPaise());
    }

    @Transactional
    public MeQr qr() {
        PassDto pass = qr.currentPass(memberId());
        return new MeQr(pass.token(), pass.payload(), pass.createdAt());
    }

    /** The old pass stops working immediately. */
    @Transactional
    public MeQr reissueQr() {
        PassDto pass = qr.reissue(memberId());
        return new MeQr(pass.token(), pass.payload(), pass.createdAt());
    }
}
