package app.gym.audit;

import app.gym.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Append-only audit trail (the table also rejects UPDATE/DELETE via trigger). Secret-safe: any detail key that
 * looks like a credential is dropped before writing. Joins the caller's transaction when there is one.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i).*(password|token|secret|hash|key|cookie).*");

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Records an action by the currently authenticated user (or by "system" when there is none). */
    public void record(String action, String entityType, Long entityId, Map<String, Object> details) {
        recordAs(CurrentUser.idOrNull(), action, entityType, entityId, details);
    }

    public void recordAs(Long actorId, String action, String entityType, Long entityId, Map<String, Object> details) {
        jdbc.sql("INSERT INTO audit_log (actor_id, action, entity_type, entity_id, details) "
                        + "VALUES (:actor, :action, :type, :eid, CAST(:details AS jsonb))")
                .param("actor", actorId)
                .param("action", action)
                .param("type", entityType)
                .param("eid", entityId)
                .param("details", toJson(sanitize(details)))
                .update();
    }

    static Map<String, Object> sanitize(Map<String, Object> details) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((k, v) -> {
                if (k != null && !SENSITIVE_KEY.matcher(k).matches()) {
                    clean.put(k, v);
                }
            });
        }
        return clean;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise audit details; storing empty object");
            return "{}";
        }
    }
}
