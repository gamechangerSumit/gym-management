package app.gym.notification;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.settings.GymClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenewalReminderService {
    public record Reminder(long id, long memberId, String memberCode, String memberName, String phone,
                           long membershipId, String planName, LocalDate expiryDate, int stageDays,
                           String status, String messageText, Instant scheduledFor, Instant sentAt) {}

    private static final int[] STAGES={14,7,3,0};
    private final JdbcClient jdbc;
    private final GymClock clock;
    private final AuditService audit;

    public RenewalReminderService(JdbcClient jdbc,GymClock clock,AuditService audit){
        this.jdbc=jdbc;this.clock=clock;this.audit=audit;
    }

    @Transactional
    public int enqueueDueReminders(){
        LocalDate today=clock.today();
        int inserted=0;
        for(int stage:STAGES){
            LocalDate expiry=today.plusDays(stage);
            inserted+=jdbc.sql("""
                INSERT INTO notification_outbox(gym_id,member_id,membership_id,kind,stage_days,channel,status,message_text,scheduled_for)
                SELECT m.gym_id,m.id,ms.id,'RENEWAL_REMINDER',:stage,'IN_APP',
                    CASE WHEN m.transactional_messages_enabled THEN 'QUEUED' ELSE 'SKIPPED_OPT_OUT' END,
                    'Hi ' || m.full_name || ', your ' || p.name || ' membership expires on ' ||
                    to_char(ms.end_date,'DD Mon YYYY') || '. Review renewal options in your member portal or contact reception.',
                    :scheduled
                FROM membership ms
                JOIN member_profile m ON m.id=ms.member_id
                JOIN membership_plan p ON p.id=ms.plan_id
                WHERE m.status='ACTIVE' AND ms.status='ACTIVE' AND ms.end_date=:expiry
                  AND NOT EXISTS (SELECT 1 FROM membership child WHERE child.renewed_from_id=ms.id AND child.status<>'CANCELLED')
                ON CONFLICT DO NOTHING
                """).param("stage",stage).param("scheduled",Db.ts(clock.now())).param("expiry",expiry).update();
        }
        jdbc.sql("""
            UPDATE notification_outbox n SET status='CANCELLED',updated_at=now()
            WHERE n.status='QUEUED' AND n.kind='RENEWAL_REMINDER'
              AND (NOT EXISTS (SELECT 1 FROM member_profile m WHERE m.id=n.member_id AND m.status='ACTIVE'
                               AND m.transactional_messages_enabled=TRUE)
                   OR NOT EXISTS (SELECT 1 FROM membership ms WHERE ms.id=n.membership_id AND ms.status='ACTIVE'
                                  AND ms.end_date>=:today)
                   OR EXISTS (SELECT 1 FROM membership child WHERE child.renewed_from_id=n.membership_id
                              AND child.status<>'CANCELLED'))
            """).param("today",today).update();
        return inserted;
    }

    public List<Reminder> queue(){
        return jdbc.sql("""
            SELECT n.id,n.member_id,m.member_code,m.full_name,m.phone,n.membership_id,p.name AS plan_name,
                   ms.end_date,n.stage_days,n.status,n.message_text,n.scheduled_for,n.sent_at
            FROM notification_outbox n
            JOIN member_profile m ON m.id=n.member_id
            LEFT JOIN membership ms ON ms.id=n.membership_id
            LEFT JOIN membership_plan p ON p.id=ms.plan_id
            WHERE n.kind='RENEWAL_REMINDER' AND n.status IN ('QUEUED','FAILED')
            ORDER BY n.stage_days ASC,n.scheduled_for,n.id
            """).query((rs,row)->new Reminder(rs.getLong("id"),rs.getLong("member_id"),rs.getString("member_code"),
                rs.getString("full_name"),rs.getString("phone"),rs.getLong("membership_id"),rs.getString("plan_name"),
                rs.getObject("end_date",LocalDate.class),rs.getInt("stage_days"),rs.getString("status"),
                rs.getString("message_text"),Db.instant(rs,"scheduled_for"),Db.instant(rs,"sent_at"))).list();
    }

    @Transactional
    public void markSent(long id){
        int changed=jdbc.sql("UPDATE notification_outbox SET status='SENT',sent_at=:at,updated_at=now() WHERE id=:id AND status IN ('QUEUED','FAILED')")
            .param("at",Db.ts(clock.now())).param("id",id).update();
        if(changed==0)throw ApiException.conflict("REMINDER_NOT_QUEUED","Reminder is no longer queued.");
        audit.record("RENEWAL_REMINDER_MARKED_SENT","NOTIFICATION",id,
            java.util.Map.<String,Object>of("actorId",CurrentUser.idOrNull()==null?0:CurrentUser.idOrNull()));
    }
}
