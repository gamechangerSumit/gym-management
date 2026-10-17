package app.gym.settings;

import app.gym.audit.AuditService;
import app.gym.common.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GymSettingsService {

    public record UpdateSettingsRequest(
            @NotBlank @Size(max = 120) String gymName,
            @NotBlank @Size(max = 60) String timezone,
            @Min(0) @Max(1440) int duplicateWindowMinutes,
            @Min(1) @Max(365) int dropOffDays,
            @Min(0) @Max(90) int expiringSoonDays,
            boolean memberCanEditEmail) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public GymSettingsService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public GymSettings get() {
        return jdbc.sql("SELECT g.name, s.timezone, s.duplicate_window_minutes, s.drop_off_days, "
                        + "s.expiring_soon_days, s.member_can_edit_email FROM gym g "
                        + "JOIN gym_settings s ON s.gym_id = g.id WHERE g.id = 1")
                .query((rs, i) -> new GymSettings(rs.getString(1), ZoneId.of(rs.getString(2)), rs.getInt(3),
                        rs.getInt(4), rs.getInt(5), rs.getBoolean(6)))
                .single();
    }

    @Transactional
    public GymSettings update(UpdateSettingsRequest req) {
        ZoneId zone;
        try {
            zone = ZoneId.of(req.timezone().trim());
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "Unknown timezone. Use a name like Asia/Kolkata.");
        }
        GymSettings before = get();
        jdbc.sql("UPDATE gym SET name = :n WHERE id = 1").param("n", req.gymName().trim()).update();
        jdbc.sql("UPDATE gym_settings SET timezone = :tz, duplicate_window_minutes = :dw, drop_off_days = :dd, "
                        + "expiring_soon_days = :es, member_can_edit_email = :me, updated_at = now() WHERE gym_id = 1")
                .param("tz", zone.getId())
                .param("dw", req.duplicateWindowMinutes())
                .param("dd", req.dropOffDays())
                .param("es", req.expiringSoonDays())
                .param("me", req.memberCanEditEmail())
                .update();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", describe(before));
        GymSettings after = get();
        details.put("after", describe(after));
        audit.record("SETTINGS_UPDATED", "GYM_SETTINGS", 1L, details);
        return after;
    }

    private static Map<String, Object> describe(GymSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gymName", s.gymName());
        m.put("timezone", s.zone().getId());
        m.put("duplicateWindowMinutes", s.duplicateWindowMinutes());
        m.put("dropOffDays", s.dropOffDays());
        m.put("expiringSoonDays", s.expiringSoonDays());
        m.put("memberCanEditEmail", s.memberCanEditEmail());
        return m;
    }
}
