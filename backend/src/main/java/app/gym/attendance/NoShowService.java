package app.gym.attendance;

import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.settings.GymClock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoShowService {
    public record NoShowCase(
            long id, long memberId, String memberCode, String memberName, String phone,
            String planName, LocalDate membershipEnd, LocalDate lastVisit, int absentDays,
            String outcome, LocalDate nextActionDate) {}
    public record OutcomeRequest(String outcome, String note, LocalDate nextActionDate) {}
    public record FollowupEvent(long id, String outcome, String note, LocalDate nextActionDate,
                                String recordedBy, java.time.Instant createdAt) {}

    private final JdbcClient jdbc;
    private final GymClock clock;
    public NoShowService(JdbcClient jdbc, GymClock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Transactional
    public int scan(int threshold) {
        if (threshold < 1 || threshold > 365) {
            throw ApiException.badRequest("INVALID_THRESHOLD", "Threshold must be between 1 and 365 days.");
        }
        // Only active members with a currently valid, non-held membership qualify.
        // The unique partial index makes the daily job safe to run more than once.
        jdbc.sql("""
            UPDATE no_show_case c
            SET status = 'CANCELLED', resolved_at = now(), updated_at = now()
            FROM member_profile m
            WHERE c.member_id = m.id AND c.status = 'OPEN'
              AND (m.status <> 'ACTIVE' OR NOT EXISTS (
                  SELECT 1 FROM membership ms
                  WHERE ms.member_id = m.id AND ms.status = 'ACTIVE'
                    AND ms.start_date <= :today AND ms.end_date >= :today
              ))
            """).param("today", clock.today()).update();

        return jdbc.sql("""
            INSERT INTO no_show_case (gym_id, member_id, last_attendance_at, absent_days)
            SELECT m.gym_id, m.id, latest.last_visit,
                   GREATEST(0, (:today - COALESCE(latest.last_visit::date, m.join_date)))
            FROM member_profile m
            LEFT JOIN LATERAL (
                SELECT MAX(a.checked_in_at) AS last_visit
                FROM attendance_record a WHERE a.member_id = m.id
            ) latest ON TRUE
            WHERE m.status = 'ACTIVE'
              AND EXISTS (
                  SELECT 1 FROM membership ms
                  WHERE ms.member_id = m.id AND ms.status = 'ACTIVE'
                    AND ms.start_date <= :today AND ms.end_date >= :today
              )
              AND (:today - COALESCE(latest.last_visit::date, m.join_date)) >= :threshold
            ON CONFLICT (member_id) WHERE status = 'OPEN'
            DO UPDATE SET last_attendance_at = EXCLUDED.last_attendance_at,
                          absent_days = EXCLUDED.absent_days,
                          updated_at = now()
            """).param("today", clock.today()).param("threshold", threshold).update();
    }

    public List<NoShowCase> openCases() {
        return jdbc.sql("""
            SELECT c.id, m.id AS member_id, m.member_code, m.full_name, m.phone,
                   p.name AS plan_name, ms.end_date AS membership_end,
                   c.last_attendance_at::date AS last_visit, c.absent_days,
                   c.outcome, c.next_action_date
            FROM no_show_case c
            JOIN member_profile m ON m.id = c.member_id
            LEFT JOIN LATERAL (
                SELECT x.* FROM membership x WHERE x.member_id = m.id
                  AND x.status <> 'CANCELLED'
                ORDER BY x.end_date DESC LIMIT 1
            ) ms ON TRUE
            LEFT JOIN membership_plan p ON p.id = ms.plan_id
            WHERE c.status = 'OPEN'
            ORDER BY c.absent_days DESC, c.opened_at
            """).query((rs, row) -> new NoShowCase(
                rs.getLong("id"), rs.getLong("member_id"), rs.getString("member_code"),
                rs.getString("full_name"), rs.getString("phone"), rs.getString("plan_name"),
                rs.getObject("membership_end", LocalDate.class), rs.getObject("last_visit", LocalDate.class),
                rs.getInt("absent_days"), rs.getString("outcome"),
                rs.getObject("next_action_date", LocalDate.class)
            )).list();
    }

    public List<FollowupEvent> history(long caseId) {
        boolean exists = Boolean.TRUE.equals(jdbc.sql(
                "SELECT EXISTS (SELECT 1 FROM no_show_case WHERE id = :id)")
            .param("id", caseId).query(Boolean.class).single());
        if (!exists) throw ApiException.notFound("No-show case not found.");
        return jdbc.sql("""
            SELECT e.id, e.outcome, e.note, e.next_action_date, u.login_id AS recorded_by, e.created_at
            FROM no_show_followup_event e
            LEFT JOIN user_account u ON u.id = e.recorded_by
            WHERE e.case_id = :id ORDER BY e.created_at DESC, e.id DESC
            """).param("id", caseId).query((rs, row) -> new FollowupEvent(
                rs.getLong("id"), rs.getString("outcome"), rs.getString("note"),
                rs.getObject("next_action_date", LocalDate.class), rs.getString("recorded_by"),
                rs.getTimestamp("created_at").toInstant()
            )).list();
    }

    @Transactional
    public void recordOutcome(long caseId, OutcomeRequest request) {
        if (request == null || request.outcome() == null ||
            !List.of("WILL_RETURN", "INJURED", "TRAVELLING", "TIMING_ISSUE", "UNHAPPY", "NO_RESPONSE", "CANCELLED").contains(request.outcome())) {
            throw ApiException.badRequest("INVALID_OUTCOME", "Choose a valid follow-up outcome.");
        }
        if (request.note() != null && request.note().length() > 500) {
            throw ApiException.badRequest("NOTE_TOO_LONG", "Note must be 500 characters or fewer.");
        }
        int updated = jdbc.sql("""
            UPDATE no_show_case SET outcome = :outcome, next_action_date = :nextAction,
                status = CASE WHEN :outcome = 'CANCELLED' THEN 'CANCELLED' ELSE 'OPEN' END,
                resolved_at = CASE WHEN :outcome = 'CANCELLED' THEN now() ELSE NULL END,
                updated_at = now()
            WHERE id = :id AND status = 'OPEN'
            """).param("outcome", request.outcome())
            .param("nextAction", request.nextActionDate()).param("id", caseId).update();
        if (updated == 0) throw ApiException.notFound("Open no-show case not found.");
        jdbc.sql("""
            INSERT INTO no_show_followup_event(case_id, outcome, note, next_action_date, recorded_by)
            VALUES (:id, :outcome, :note, :nextAction, :recordedBy)
            """).param("id", caseId).param("outcome", request.outcome())
            .param("note", request.note()).param("nextAction", request.nextActionDate())
            .param("recordedBy", CurrentUser.idOrNull()).update();
    }

    @Transactional
    public void resolveOnReturn(long memberId) {
        jdbc.sql("""
            UPDATE no_show_case SET status = 'RESOLVED', resolved_at = now(), updated_at = now()
            WHERE member_id = :member AND status = 'OPEN'
            """).param("member", memberId).update();
    }
}
