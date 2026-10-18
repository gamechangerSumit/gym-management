package app.gym.renewal;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.membership.MembershipDtos.MembershipDto;
import app.gym.membership.MembershipDtos.RenewRequest;
import app.gym.membership.MembershipService;
import app.gym.settings.GymClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenewalOrderService {
    private record CurrentMembership(long id, LocalDate endDate, String status) {}
    public record CreateRequest(Long planId) {}
    public record RenewalPlan(long id, String name, int durationDays, long pricePaise, String description) {}
    public record RenewalOrder(long id, long memberId, String memberCode, String memberName,
                               long sourceMembershipId, long planId, String planName, long pricePaise,
                               int durationDays, String status, Long paidMembershipId, Instant createdAt, Instant paidAt) {}

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final MembershipService memberships;
    private final AuditService audit;

    public RenewalOrderService(JdbcClient jdbc, GymClock clock, MembershipService memberships, AuditService audit) {
        this.jdbc = jdbc; this.clock = clock; this.memberships = memberships; this.audit = audit;
    }

    public List<RenewalPlan> availablePlans() {
        return jdbc.sql("SELECT id,name,duration_days,price_paise,description FROM membership_plan WHERE active=TRUE AND price_paise > 0 ORDER BY duration_days,price_paise")
            .query((rs,row)->new RenewalPlan(rs.getLong("id"),rs.getString("name"),rs.getInt("duration_days"),
                rs.getLong("price_paise"),rs.getString("description"))).list();
    }

    @Transactional
    public RenewalOrder createFromMembership(long membershipId) {
        CreateRequest req=jdbc.sql("SELECT plan_id FROM membership WHERE id=:id AND status<>'CANCELLED'")
            .param("id",membershipId).query((rs,row)->new CreateRequest(rs.getLong("plan_id"))).optional()
            .orElseThrow(()->ApiException.notFound("Renewable membership not found."));
        long memberId=jdbc.sql("SELECT member_id FROM membership WHERE id=:id")
            .param("id",membershipId).query(Long.class).single();
        return create(memberId,req);
    }

    @Transactional
    public RenewalOrder createForCurrentMember(CreateRequest req) {
        long memberId = jdbc.sql("SELECT id FROM member_profile WHERE user_id=:u AND status='ACTIVE'")
            .param("u", CurrentUser.id()).query(Long.class).optional()
            .orElseThrow(() -> ApiException.notFound("Active member profile not found."));
        return create(memberId, req);
    }

    @Transactional
    public RenewalOrder create(long memberId, CreateRequest req) {
        if (req == null || req.planId() == null) throw ApiException.badRequest("PLAN_REQUIRED","Choose a renewal plan.");
        jdbc.sql("SELECT id FROM member_profile WHERE id=:id AND status='ACTIVE' FOR UPDATE")
            .param("id",memberId).query(Long.class).optional().orElseThrow(()->ApiException.notFound("Active member not found."));
        CurrentMembership current=jdbc.sql("""
            SELECT id, end_date, status FROM membership WHERE member_id=:member AND status <> 'CANCELLED'
            ORDER BY end_date DESC, id DESC LIMIT 1 FOR UPDATE
            """).param("member",memberId)
            .query((rs,row)->new CurrentMembership(rs.getLong("id"),rs.getObject("end_date",LocalDate.class),rs.getString("status")))
            .optional().orElseThrow(()->ApiException.conflict("NO_MEMBERSHIP","Assign a membership before creating a renewal."));
        LocalDate today=clock.today();
        LocalDate end=current.endDate();
        if (end.isBefore(today.minusDays(30)))
            throw ApiException.conflict("RENEWAL_WINDOW_CLOSED","This membership ended more than 30 days ago. Contact the front desk.");
        if ("ON_HOLD".equals(current.status()))
            throw ApiException.conflict("MEMBERSHIP_ON_HOLD","Resume the membership before renewing.");
        Map<String,Object> plan=jdbc.sql("SELECT id,name,duration_days,price_paise FROM membership_plan WHERE id=:id AND active=TRUE AND price_paise > 0")
            .param("id",req.planId()).query((rs,row) -> {
                Map<String,Object> item = new java.util.HashMap<>();
                item.put("id",rs.getLong("id")); item.put("name",rs.getString("name"));
                item.put("duration_days",rs.getInt("duration_days")); item.put("price_paise",rs.getLong("price_paise"));
                return item;
            }).optional().orElseThrow(()->ApiException.notFound("Active plan not found."));
        boolean pending=Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS(SELECT 1 FROM renewal_order WHERE member_id=:m AND status='PAYMENT_PENDING')")
            .param("m",memberId).query(Boolean.class).single());
        if(pending) throw ApiException.conflict("RENEWAL_ALREADY_PENDING","You already have a renewal awaiting payment.");
        long orderId=jdbc.sql("""
            INSERT INTO renewal_order(member_id,source_membership_id,plan_id,price_paise,duration_days)
            VALUES (:member,:source,:plan,:price,:duration) RETURNING id
            """).param("member",memberId).param("source",current.id())
            .param("plan",req.planId()).param("price",((Number)plan.get("price_paise")).longValue())
            .param("duration",((Number)plan.get("duration_days")).intValue()).query(Long.class).single();
        audit.record("RENEWAL_ORDER_CREATED","RENEWAL_ORDER",orderId,
            Map.of("memberId",memberId,"sourceMembershipId",current.id(),"planId",req.planId(),"pricePaise",plan.get("price_paise")));
        return get(orderId);
    }

    public List<RenewalOrder> myOrders() {
        long memberId=jdbc.sql("SELECT id FROM member_profile WHERE user_id=:u AND status='ACTIVE'")
            .param("u",CurrentUser.id()).query(Long.class).optional().orElseThrow(()->ApiException.notFound("Active member profile not found."));
        return list(" WHERE o.member_id=:member",Map.of("member",memberId));
    }
    public List<RenewalOrder> allOrders(){return list("",Map.of());}

    private List<RenewalOrder> list(String where,Map<String,Object> params){
        return jdbc.sql(select()+where+" ORDER BY o.created_at DESC,o.id DESC")
            .params(params).query((rs,row)->map(rs)).list();
    }
    public RenewalOrder get(long id){
        return jdbc.sql(select()+" WHERE o.id=:id").param("id",id).query((rs,row)->map(rs)).optional()
            .orElseThrow(()->ApiException.notFound("Renewal order not found."));
    }
    private static String select(){return """
        SELECT o.id,o.member_id,m.member_code,m.full_name,o.source_membership_id,o.plan_id,p.name AS plan_name,
               o.price_paise,o.duration_days,o.status,o.paid_membership_id,o.created_at,o.paid_at
        FROM renewal_order o JOIN member_profile m ON m.id=o.member_id
        JOIN membership_plan p ON p.id=o.plan_id
        """;}
    private static RenewalOrder map(java.sql.ResultSet rs)throws java.sql.SQLException{
        return new RenewalOrder(rs.getLong("id"),rs.getLong("member_id"),rs.getString("member_code"),rs.getString("full_name"),
            rs.getLong("source_membership_id"),rs.getLong("plan_id"),rs.getString("plan_name"),rs.getLong("price_paise"),
            rs.getInt("duration_days"),rs.getString("status"),Db.longOrNull(rs,"paid_membership_id"),
            Db.instant(rs,"created_at"),Db.instant(rs,"paid_at"));
    }

    @Transactional
    public void cancelForCurrentMember(long orderId){
        long memberId=jdbc.sql("SELECT id FROM member_profile WHERE user_id=:u AND status='ACTIVE'")
            .param("u",CurrentUser.id()).query(Long.class).optional().orElseThrow(()->ApiException.notFound("Active member profile not found."));
        cancel(orderId,memberId);
    }
    @Transactional
    public void cancel(long orderId,Long requiredMemberId){
        Map<String,Object> o=jdbc.sql("SELECT member_id,status FROM renewal_order WHERE id=:id FOR UPDATE")
            .param("id",orderId).query((rs,row) -> {
                Map<String,Object> item = new java.util.HashMap<>();
                item.put("member_id",rs.getLong("member_id")); item.put("status",rs.getString("status"));
                return item;
            }).optional().orElseThrow(()->ApiException.notFound("Renewal order not found."));
        if(requiredMemberId!=null&&((Number)o.get("member_id")).longValue()!=requiredMemberId)throw ApiException.notFound("Renewal order not found.");
        if(!"PAYMENT_PENDING".equals(o.get("status")))throw ApiException.conflict("RENEWAL_NOT_CANCELLABLE","Only unpaid renewal orders can be cancelled.");
        jdbc.sql("UPDATE renewal_order SET status='CANCELLED',cancelled_at=now(),updated_at=now() WHERE id=:id").param("id",orderId).update();
        audit.record("RENEWAL_ORDER_CANCELLED","RENEWAL_ORDER",orderId,Map.of("memberId",o.get("member_id")));
    }

    @Transactional
    public long completePayment(long orderId,long paymentId){
        Map<String,Object> o=jdbc.sql("SELECT member_id,source_membership_id,plan_id,price_paise,status FROM renewal_order WHERE id=:id FOR UPDATE")
            .param("id",orderId).query((rs,row) -> {
                Map<String,Object> item = new java.util.HashMap<>();
                item.put("member_id",rs.getLong("member_id")); item.put("source_membership_id",rs.getLong("source_membership_id"));
                item.put("plan_id",rs.getLong("plan_id")); item.put("price_paise",rs.getLong("price_paise"));
                item.put("status",rs.getString("status")); return item;
            }).optional().orElseThrow(()->ApiException.notFound("Renewal order not found."));
        if(!"PAYMENT_PENDING".equals(o.get("status")))throw ApiException.conflict("RENEWAL_NOT_PAYABLE","Renewal order is no longer awaiting payment.");
        MembershipDto renewed=memberships.renew(((Number)o.get("source_membership_id")).longValue(),
            new RenewRequest(((Number)o.get("plan_id")).longValue(),null,((Number)o.get("price_paise")).longValue()));
        jdbc.sql("UPDATE payment SET membership_id=:membership WHERE id=:payment AND renewal_order_id=:order")
            .param("membership",renewed.id()).param("payment",paymentId).param("order",orderId).update();
        jdbc.sql("UPDATE renewal_order SET status='PAID',paid_membership_id=:membership,paid_at=now(),updated_at=now() WHERE id=:id")
            .param("membership",renewed.id()).param("id",orderId).update();
        audit.record("RENEWAL_PAYMENT_VERIFIED","RENEWAL_ORDER",orderId,
            Map.of("paymentId",paymentId,"membershipId",renewed.id(),"memberId",o.get("member_id")));
        return renewed.id();
    }
}
