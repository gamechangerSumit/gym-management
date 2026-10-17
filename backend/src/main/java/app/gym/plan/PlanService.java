package app.gym.plan;

import app.gym.audit.AuditService;
import app.gym.common.ApiException;
import app.gym.plan.PlanDtos.PlanDto;
import app.gym.plan.PlanDtos.PlanRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    static final RowMapper<PlanDto> MAPPER = (rs, i) -> new PlanDto(rs.getLong("id"), rs.getString("name"),
            rs.getInt("duration_days"), rs.getLong("price_paise"), rs.getString("description"), rs.getBoolean("active"));

    private static final String SELECT = "SELECT id, name, duration_days, price_paise, description, active FROM membership_plan";

    private final JdbcClient jdbc;
    private final AuditService audit;

    public PlanService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<PlanDto> list(boolean includeInactive) {
        return jdbc.sql(SELECT + (includeInactive ? "" : " WHERE active") + " ORDER BY active DESC, lower(name)")
                .query(MAPPER).list();
    }

    public PlanDto get(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(MAPPER).optional()
                .orElseThrow(() -> ApiException.notFound("Plan not found."));
    }

    @Transactional
    public PlanDto create(PlanRequest req) {
        try {
            long id = jdbc.sql("INSERT INTO membership_plan (name, duration_days, price_paise, description, active) "
                            + "VALUES (:n, :d, :p, :desc, :a) RETURNING id")
                    .param("n", req.name().trim())
                    .param("d", req.durationDays())
                    .param("p", req.pricePaise())
                    .param("desc", blankToNull(req.description()))
                    .param("a", req.active() == null || req.active())
                    .query(Long.class).single();
            audit.record("PLAN_CREATED", "PLAN", id, describe(req));
            return get(id);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("DUPLICATE_PLAN", "A plan with this name already exists.");
        }
    }

    /** Price changes only affect future assignments: memberships keep the price they were charged. */
    @Transactional
    public PlanDto update(long id, PlanRequest req) {
        get(id);
        try {
            jdbc.sql("UPDATE membership_plan SET name = :n, duration_days = :d, price_paise = :p, description = :desc, "
                            + "active = :a, updated_at = now() WHERE id = :id")
                    .param("n", req.name().trim())
                    .param("d", req.durationDays())
                    .param("p", req.pricePaise())
                    .param("desc", blankToNull(req.description()))
                    .param("a", req.active() == null || req.active())
                    .param("id", id)
                    .update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("DUPLICATE_PLAN", "A plan with this name already exists.");
        }
        audit.record("PLAN_UPDATED", "PLAN", id, describe(req));
        return get(id);
    }

    private static Map<String, Object> describe(PlanRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", req.name().trim());
        m.put("durationDays", req.durationDays());
        m.put("pricePaise", req.pricePaise());
        m.put("active", req.active() == null || req.active());
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
