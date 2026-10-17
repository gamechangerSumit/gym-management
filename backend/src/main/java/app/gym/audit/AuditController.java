package app.gym.audit;

import app.gym.common.Db;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    public record AuditEntry(long id, Long actorId, String actorLogin, String action, String entityType,
                             Long entityId, JsonNode details, Instant at) {}

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AuditController(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@perm.owner()")
    public PageResponse<AuditEntry> list(@RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (entityType != null && !entityType.isBlank()) {
            where.append(" AND a.entity_type = :etype");
            params.put("etype", entityType);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND a.action = :action");
            params.put("action", action);
        }
        long total = jdbc.sql("SELECT count(*) FROM audit_log a" + where).params(params).query(Long.class).single();
        Map<String, Object> listParams = new HashMap<>(params);
        listParams.put("lim", paging.size());
        listParams.put("off", paging.offset());
        List<AuditEntry> items = jdbc.sql("SELECT a.id, a.actor_id, u.login_id, a.action, a.entity_type, a.entity_id, "
                        + "a.details::text AS details, a.at FROM audit_log a LEFT JOIN user_account u ON u.id = a.actor_id"
                        + where + " ORDER BY a.at DESC, a.id DESC LIMIT :lim OFFSET :off")
                .params(listParams)
                .query((rs, i) -> new AuditEntry(rs.getLong("id"), Db.longOrNull(rs, "actor_id"), rs.getString("login_id"),
                        rs.getString("action"), rs.getString("entity_type"), Db.longOrNull(rs, "entity_id"),
                        parse(rs.getString("details")), Db.instant(rs, "at")))
                .list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    private JsonNode parse(String json) {
        try {
            return json == null ? null : mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
