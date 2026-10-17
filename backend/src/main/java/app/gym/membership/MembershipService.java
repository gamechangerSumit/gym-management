package app.gym.membership;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.membership.MembershipDtos.AssignRequest;
import app.gym.membership.MembershipDtos.MembershipDto;
import app.gym.membership.MembershipDtos.RenewRequest;
import app.gym.member.MemberDtos.MemberBasic;
import app.gym.member.MemberService;
import app.gym.plan.PlanDtos.PlanDto;
import app.gym.plan.PlanService;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettingsService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private static final String SELECT = "SELECT ms.id, ms.member_id, ms.plan_id, p.name AS plan_name, ms.start_date, "
            + "ms.end_date, ms.price_charged_paise, ms.status, ms.renewed_from_id, ms.hold_reason, ms.cancel_reason, "
            + "ms.created_at FROM membership ms JOIN membership_plan p ON p.id = ms.plan_id ";

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;
    private final AuditService audit;
    private final MemberService members;
    private final PlanService plans;

    public MembershipService(JdbcClient jdbc, GymClock clock, GymSettingsService settings, AuditService audit,
                             MemberService members, PlanService plans) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.audit = audit;
        this.members = members;
        this.plans = plans;
    }

    // ---------------------------------------------------------------- queries

    public List<MembershipDto> listForMember(long memberId) {
        members.basic(memberId);
        LocalDate today = clock.today();
        int soon = settings.get().expiringSoonDays();
        return jdbc.sql(SELECT + "WHERE ms.member_id = :m ORDER BY ms.start_date DESC, ms.id DESC")
                .param("m", memberId)
                .query((rs, i) -> map(rs, today, soon))
                .list();
    }

    public MembershipDto get(long id) {
        LocalDate today = clock.today();
        int soon = settings.get().expiringSoonDays();
        return jdbc.sql(SELECT + "WHERE ms.id = :id").param("id", id)
                .query((rs, i) -> map(rs, today, soon))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Membership not found."));
    }

    private static MembershipDto map(java.sql.ResultSet rs, LocalDate today, int soon) throws java.sql.SQLException {
        LocalDate start = Db.date(rs, "start_date");
        LocalDate end = Db.date(rs, "end_date");
        String stored = rs.getString("status");
        return new MembershipDto(rs.getLong("id"), rs.getLong("member_id"), rs.getLong("plan_id"),
                rs.getString("plan_name"), start, end, rs.getLong("price_charged_paise"), stored,
                StatusCalculator.compute(stored, start, end, today, soon), Db.longOrNull(rs, "renewed_from_id"),
                StatusCalculator.daysRemaining(today, end), rs.getString("hold_reason"), rs.getString("cancel_reason"),
                Db.instant(rs, "created_at"));
    }

    // --------------------------------------------------------------- commands

    @Transactional
    public MembershipDto assign(long memberId, AssignRequest req) {
        MemberBasic member = lockActiveMember(memberId);
        PlanDto plan = requireActivePlan(req.planId());
        LocalDate start = req.startDate() != null ? req.startDate() : clock.today();
        long id = insert(member.id(), plan, start, req.pricePaise(), null);
        audit.record("MEMBERSHIP_ASSIGNED", "MEMBERSHIP", id, describe(memberId, plan, start, null));
        return get(id);
    }

    /** Renewal never overwrites history: it creates a new row linked by renewed_from_id. */
    @Transactional
    public MembershipDto renew(long membershipId, RenewRequest req) {
        MembershipDto old = get(membershipId);
        if ("CANCELLED".equals(old.storedStatus())) {
            throw ApiException.conflict("MEMBERSHIP_CANCELLED", "A cancelled membership cannot be renewed. Assign a new one.");
        }
        MemberBasic member = lockActiveMember(old.memberId());
        boolean alreadyRenewed = Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM membership "
                        + "WHERE renewed_from_id = :id AND status <> 'CANCELLED')")
                .param("id", membershipId).query(Boolean.class).single());
        if (alreadyRenewed) {
            throw ApiException.conflict("ALREADY_RENEWED", "This membership has already been renewed.");
        }
        PlanDto plan = requireActivePlan(req.planId() != null ? req.planId() : old.planId());
        LocalDate today = clock.today();
        LocalDate start = req.startDate() != null ? req.startDate()
                : (old.endDate().isBefore(today) ? today : old.endDate().plusDays(1));
        long id = insert(member.id(), plan, start, req.pricePaise(), membershipId);
        audit.record("MEMBERSHIP_RENEWED", "MEMBERSHIP", id, describe(member.id(), plan, start, membershipId));
        return get(id);
    }

    @Transactional
    public MembershipDto hold(long id, String reason) {
        MembershipDto m = get(id);
        if (!"ACTIVE".equals(m.storedStatus())) {
            throw ApiException.conflict("INVALID_STATE", "Only an active membership can be put on hold.");
        }
        jdbc.sql("UPDATE membership SET status = 'ON_HOLD', hold_reason = :r WHERE id = :id")
                .param("r", reason.trim()).param("id", id).update();
        audit.record("MEMBERSHIP_HELD", "MEMBERSHIP", id, Map.of("memberId", m.memberId(), "reason", reason.trim()));
        return get(id);
    }

    @Transactional
    public MembershipDto resume(long id) {
        MembershipDto m = get(id);
        if (!"ON_HOLD".equals(m.storedStatus())) {
            throw ApiException.conflict("INVALID_STATE", "Only a membership on hold can be resumed.");
        }
        jdbc.sql("UPDATE membership SET status = 'ACTIVE', hold_reason = NULL WHERE id = :id").param("id", id).update();
        audit.record("MEMBERSHIP_RESUMED", "MEMBERSHIP", id, Map.of("memberId", m.memberId()));
        return get(id);
    }

    /** Owner only. A cancelled membership no longer counts towards dues or overlap checks; the row is kept. */
    @Transactional
    public MembershipDto cancel(long id, String reason) {
        MembershipDto m = get(id);
        if ("CANCELLED".equals(m.storedStatus())) {
            throw ApiException.conflict("INVALID_STATE", "This membership is already cancelled.");
        }
        jdbc.sql("UPDATE membership SET status = 'CANCELLED', cancel_reason = :r WHERE id = :id")
                .param("r", reason.trim()).param("id", id).update();
        audit.record("MEMBERSHIP_CANCELLED", "MEMBERSHIP", id,
                Map.of("memberId", m.memberId(), "reason", reason.trim(), "pricePaise", m.pricePaise()));
        return get(id);
    }

    // ---------------------------------------------------------------- helpers

    /** Locks the member row so concurrent assignments for one member are serialised, then checks it is active. */
    private MemberBasic lockActiveMember(long memberId) {
        jdbc.sql("SELECT id FROM member_profile WHERE id = :id FOR UPDATE").param("id", memberId)
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Member not found."));
        MemberBasic member = members.basic(memberId);
        if (!"ACTIVE".equals(member.status())) {
            throw ApiException.conflict("MEMBER_INACTIVE", "This member is inactive. Reactivate them first.");
        }
        return member;
    }

    private PlanDto requireActivePlan(long planId) {
        PlanDto plan = plans.get(planId);
        if (!plan.active()) {
            throw ApiException.conflict("PLAN_INACTIVE", "This plan is no longer offered.");
        }
        return plan;
    }

    private long insert(long memberId, PlanDto plan, LocalDate start, Long priceOverride, Long renewedFrom) {
        LocalDate end = StatusCalculator.endDate(start, plan.durationDays());
        Optional<String> overlap = jdbc.sql("SELECT start_date || ' to ' || end_date FROM membership "
                        + "WHERE member_id = :m AND status <> 'CANCELLED' AND start_date <= :end AND end_date >= :start LIMIT 1")
                .param("m", memberId).param("end", end).param("start", start)
                .query(String.class).optional();
        if (overlap.isPresent()) {
            throw ApiException.conflict("MEMBERSHIP_OVERLAP",
                    "This period overlaps an existing membership (" + overlap.get() + ").");
        }
        long price = priceOverride != null ? priceOverride : plan.pricePaise();
        try {
            return jdbc.sql("INSERT INTO membership (member_id, plan_id, start_date, end_date, price_charged_paise, "
                            + "renewed_from_id, created_by) VALUES (:m, :p, :s, :e, :price, :rf, :by) RETURNING id")
                    .param("m", memberId).param("p", plan.id()).param("s", start).param("e", end)
                    .param("price", price).param("rf", renewedFrom).param("by", CurrentUser.idOrNull())
                    .query(Long.class).single();
        } catch (DataIntegrityViolationException e) {
            // Backstop for races: the exclusion constraint is the final authority on overlaps.
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("membership_no_overlap")) {
                throw ApiException.conflict("MEMBERSHIP_OVERLAP", "This period overlaps an existing membership.");
            }
            throw e;
        }
    }

    private static Map<String, Object> describe(long memberId, PlanDto plan, LocalDate start, Long renewedFrom) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("memberId", memberId);
        m.put("plan", plan.name());
        m.put("startDate", start.toString());
        m.put("endDate", StatusCalculator.endDate(start, plan.durationDays()).toString());
        if (renewedFrom != null) {
            m.put("renewedFromId", renewedFrom);
        }
        return m;
    }
}
