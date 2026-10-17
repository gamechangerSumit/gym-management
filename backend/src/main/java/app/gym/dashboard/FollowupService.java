package app.gym.dashboard;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.common.Sql;
import app.gym.config.AppProperties;
import app.gym.dashboard.DashboardDtos.FollowupItem;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettingsService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renewal Recovery Center: records who was contacted. It never sends anything to anyone. */
@Service
public class FollowupService {

    static final Set<String> STATUSES = Set.of("NOT_CONTACTED", "CONTACTED", "NO_ANSWER", "WILL_RENEW", "NOT_INTERESTED");

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;
    private final AuditService audit;
    private final AppProperties props;

    public FollowupService(JdbcClient jdbc, GymClock clock, GymSettingsService settings, AuditService audit,
                           AppProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.audit = audit;
        this.props = props;
    }

    public List<FollowupItem> list(String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw ApiException.badRequest("INVALID_STATUS", "Unknown contact status.");
        }
        List<FollowupItem> all = jdbc.sql(Sql.load("recovery_list"))
                .param("today", clock.today())
                .param("lookback", props.dashboard().expiredLookbackDays())
                .param("soon", settings.get().expiringSoonDays())
                .query((rs, i) -> new FollowupItem(rs.getLong("membership_id"), rs.getLong("member_id"),
                        rs.getString("member_code"), rs.getString("full_name"), rs.getString("phone"),
                        Db.date(rs, "end_date"), rs.getString("plan_name"), rs.getString("contact_status"),
                        rs.getString("note"), Db.instant(rs, "updated_at")))
                .list();
        if (status == null || status.isBlank()) {
            return all;
        }
        return all.stream().filter(f -> f.contactStatus().equals(status)).toList();
    }

    @Transactional
    public void update(long membershipId, String status, String note) {
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("INVALID_STATUS", "Unknown contact status.");
        }
        Long memberId = jdbc.sql("SELECT member_id FROM membership WHERE id = :id").param("id", membershipId)
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Membership not found."));
        jdbc.sql("INSERT INTO renewal_followup (member_id, membership_id, contact_status, note, updated_by) "
                        + "VALUES (:m, :ms, :s, :n, :by) ON CONFLICT (membership_id) DO UPDATE SET "
                        + "contact_status = EXCLUDED.contact_status, note = EXCLUDED.note, "
                        + "updated_by = EXCLUDED.updated_by, updated_at = now()")
                .param("m", memberId).param("ms", membershipId).param("s", status)
                .param("n", note == null || note.isBlank() ? null : note.trim())
                .param("by", CurrentUser.idOrNull()).update();
        audit.record("FOLLOWUP_UPDATED", "MEMBERSHIP", membershipId, Map.of("contactStatus", status));
    }
}
