package app.gym.addon;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddOnService {
    public record AddOnDto(long id, String kind, String name, String description, long pricePaise,
                           Integer validityDays, Integer stockOnHand, Integer sessionsIncluded, boolean active) {}
    public record OrderDto(long id, long memberId, String memberCode, String memberName, long addonId,
                           String addonName, String kind, int quantity, long unitPricePaise, long totalPaise,
                           Integer sessionsIncluded, int sessionsUsed, String status, Instant createdAt,
                           Instant paidAt, Instant fulfilledAt) {}
    public record AddOnRequest(String kind, String name, String description, long pricePaise,
                               Integer validityDays, Integer stockOnHand, Integer sessionsIncluded, Boolean active) {}
    public record CreateOrderRequest(Long addonId, Integer quantity) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public AddOnService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<AddOnDto> catalog(boolean includeInactive) {
        String where = includeInactive ? "" : " WHERE active = TRUE";
        return jdbc.sql("SELECT id, kind, name, description, price_paise, validity_days, stock_on_hand, sessions_included, active " +
                "FROM gym_addon" + where + " ORDER BY active DESC, kind, name")
            .query((rs, row) -> new AddOnDto(rs.getLong("id"), rs.getString("kind"), rs.getString("name"),
                rs.getString("description"), rs.getLong("price_paise"), intOrNull(rs, "validity_days"),
                intOrNull(rs, "stock_on_hand"), intOrNull(rs, "sessions_included"), rs.getBoolean("active"))).list();
    }

    @Transactional
    public AddOnDto create(AddOnRequest req) {
        validate(req);
        long id = jdbc.sql("""
            INSERT INTO gym_addon(kind, name, description, price_paise, validity_days, stock_on_hand, sessions_included, active)
            VALUES (:kind, :name, :description, :price, :validity, :stock, :sessions, :active)
            RETURNING id
            """).param("kind", req.kind()).param("name", req.name().trim())
            .param("description", clean(req.description())).param("price", req.pricePaise())
            .param("validity", req.validityDays()).param("stock", req.stockOnHand())
            .param("sessions", req.sessionsIncluded()).param("active", req.active() == null || req.active())
            .query(Long.class).single();
        audit.record("ADDON_CREATED", "ADDON", id, describe(req));
        return get(id);
    }

    @Transactional
    public AddOnDto update(long id, AddOnRequest req) {
        validate(req);
        int changed = jdbc.sql("""
            UPDATE gym_addon SET kind=:kind, name=:name, description=:description, price_paise=:price,
                validity_days=:validity, stock_on_hand=:stock, sessions_included=:sessions,
                active=:active, updated_at=now() WHERE id=:id
            """).param("kind", req.kind()).param("name", req.name().trim())
            .param("description", clean(req.description())).param("price", req.pricePaise())
            .param("validity", req.validityDays()).param("stock", req.stockOnHand())
            .param("sessions", req.sessionsIncluded()).param("active", req.active() == null || req.active())
            .param("id", id).update();
        if (changed == 0) throw ApiException.notFound("Add-on not found.");
        audit.record("ADDON_UPDATED", "ADDON", id, describe(req));
        return get(id);
    }

    private static void validate(AddOnRequest req) {
        if (req == null || req.kind() == null || !List.of("PT", "DIET", "PRODUCT").contains(req.kind()))
            throw ApiException.badRequest("INVALID_ADDON_KIND", "Choose PT, DIET or PRODUCT.");
        if (req.name() == null || req.name().isBlank() || req.name().trim().length() > 120)
            throw ApiException.badRequest("INVALID_ADDON_NAME", "Add-on name is required (maximum 120 characters).");
        if (req.description() != null && req.description().length() > 500)
            throw ApiException.badRequest("INVALID_ADDON_DESCRIPTION", "Description must be 500 characters or fewer.");
        if (req.pricePaise() <= 0 || req.pricePaise() > 10_000_000_000L)
            throw ApiException.badRequest("INVALID_ADDON_PRICE", "Price must be greater than zero and within the supported payment limit.");
        if (req.validityDays() != null && (req.validityDays() < 1 || req.validityDays() > 3650))
            throw ApiException.badRequest("INVALID_ADDON_VALIDITY", "Validity must be between 1 and 3650 days.");
        if ("PRODUCT".equals(req.kind()) && (req.stockOnHand() == null || req.stockOnHand() < 0))
            throw ApiException.badRequest("PRODUCT_STOCK_REQUIRED", "Products need a non-negative stock quantity.");
        if (!"PRODUCT".equals(req.kind()) && req.stockOnHand() != null)
            throw ApiException.badRequest("STOCK_NOT_SUPPORTED", "Stock quantity is only used for products.");
        if (req.sessionsIncluded() != null && (req.sessionsIncluded() < 1 || req.sessionsIncluded() > 500))
            throw ApiException.badRequest("INVALID_SESSION_COUNT", "Sessions must be between 1 and 500.");
        if ("PT".equals(req.kind()) && req.sessionsIncluded() == null)
            throw ApiException.badRequest("PT_SESSIONS_REQUIRED", "Personal training add-ons need a session count.");
        if (!"PT".equals(req.kind()) && req.sessionsIncluded() != null)
            throw ApiException.badRequest("SESSIONS_NOT_SUPPORTED", "Only personal training add-ons use sessions.");
    }

    public AddOnDto get(long id) {
        return jdbc.sql("SELECT id, kind, name, description, price_paise, validity_days, stock_on_hand, sessions_included, active FROM gym_addon WHERE id=:id")
            .param("id", id).query((rs, row) -> new AddOnDto(rs.getLong("id"), rs.getString("kind"), rs.getString("name"),
                rs.getString("description"), rs.getLong("price_paise"), intOrNull(rs, "validity_days"),
                intOrNull(rs, "stock_on_hand"), intOrNull(rs, "sessions_included"), rs.getBoolean("active")))
            .optional().orElseThrow(() -> ApiException.notFound("Add-on not found."));
    }

    @Transactional
    public OrderDto createMemberOrder(CreateOrderRequest req) {
        long memberId = jdbc.sql("SELECT id FROM member_profile WHERE user_id=:user AND status='ACTIVE'")
            .param("user", CurrentUser.id()).query(Long.class).optional()
            .orElseThrow(() -> ApiException.notFound("Active member profile not found."));
        return createOrder(memberId, req);
    }

    @Transactional
    public OrderDto createOrder(long memberId, CreateOrderRequest req) {
        if (req == null || req.addonId() == null || req.quantity() == null || req.quantity() < 1 || req.quantity() > 20)
            throw ApiException.badRequest("INVALID_ORDER", "Choose an add-on and quantity from 1 to 20.");
        jdbc.sql("SELECT id FROM member_profile WHERE id=:id AND status='ACTIVE' FOR UPDATE")
            .param("id", memberId).query(Long.class).optional()
            .orElseThrow(() -> ApiException.notFound("Active member not found."));
        Map<String,Object> addon = jdbc.sql("SELECT id, kind, name, price_paise, stock_on_hand, sessions_included FROM gym_addon WHERE id=:id AND active=TRUE FOR UPDATE")
            .param("id", req.addonId()).query().optional()
            .orElseThrow(() -> ApiException.notFound("Active add-on not found."));
        String kind = (String) addon.get("kind");
        long unitPrice = ((Number) addon.get("price_paise")).longValue();
        long total = Math.multiplyExact(unitPrice, req.quantity().longValue());
        if (total > 10_000_000_000L)
            throw ApiException.badRequest("ORDER_AMOUNT_TOO_HIGH", "Order total exceeds the supported payment limit.");
        if ("PRODUCT".equals(kind)) {
            Object stockValue = addon.get("stock_on_hand");
            int stock = ((Number) stockValue).intValue();
            if (stock < req.quantity())
                throw ApiException.conflict("OUT_OF_STOCK", "Not enough stock is available for this order.");
            jdbc.sql("UPDATE gym_addon SET stock_on_hand=stock_on_hand-:qty, updated_at=now() WHERE id=:id")
                .param("qty", req.quantity()).param("id", req.addonId()).update();
        }
        long orderId = jdbc.sql("""
            INSERT INTO addon_order(member_id, addon_id, quantity, unit_price_paise, total_paise, sessions_included, created_by)
            VALUES (:member, :addon, :qty, :unitPrice, :total, :sessions, :by) RETURNING id
            """).param("member", memberId).param("addon", req.addonId()).param("qty", req.quantity())
            .param("unitPrice", unitPrice).param("total", total)
            .param("sessions", addon.get("sessions_included")).param("by", CurrentUser.idOrNull())
            .query(Long.class).single();
        audit.record("ADDON_ORDER_CREATED", "ADDON_ORDER", orderId,
            Map.of("memberId", memberId, "addonId", req.addonId(), "quantity", req.quantity(), "totalPaise", total));
        return getOrder(orderId);
    }

    public List<OrderDto> memberOrders() {
        long memberId = jdbc.sql("SELECT id FROM member_profile WHERE user_id=:user AND status='ACTIVE'")
            .param("user", CurrentUser.id()).query(Long.class).optional()
            .orElseThrow(() -> ApiException.notFound("Active member profile not found."));
        return orders(" WHERE o.member_id=:member", Map.of("member", memberId));
    }

    public List<OrderDto> allOrders() { return orders("", Map.of()); }

    private List<OrderDto> orders(String where, Map<String,Object> params) {
        return jdbc.sql("""
            SELECT o.id, o.member_id, m.member_code, m.full_name, o.addon_id, a.name AS addon_name, a.kind,
                   o.quantity, o.unit_price_paise, o.total_paise, o.sessions_included, o.sessions_used,
                   o.status, o.created_at, o.paid_at, o.fulfilled_at
            FROM addon_order o JOIN member_profile m ON m.id=o.member_id
            JOIN gym_addon a ON a.id=o.addon_id
            """ + where + " ORDER BY o.created_at DESC, o.id DESC")
            .params(params).query((rs,row)->mapOrder(rs)).list();
    }

    public OrderDto getOrder(long id) {
        return jdbc.sql("""
            SELECT o.id, o.member_id, m.member_code, m.full_name, o.addon_id, a.name AS addon_name, a.kind,
                   o.quantity, o.unit_price_paise, o.total_paise, o.sessions_included, o.sessions_used,
                   o.status, o.created_at, o.paid_at, o.fulfilled_at
            FROM addon_order o JOIN member_profile m ON m.id=o.member_id
            JOIN gym_addon a ON a.id=o.addon_id WHERE o.id=:id
            """).param("id",id).query((rs,row)->mapOrder(rs)).optional()
            .orElseThrow(()->ApiException.notFound("Add-on order not found."));
    }

    private static OrderDto mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderDto(rs.getLong("id"), rs.getLong("member_id"), rs.getString("member_code"),
            rs.getString("full_name"), rs.getLong("addon_id"), rs.getString("addon_name"), rs.getString("kind"),
            rs.getInt("quantity"), rs.getLong("unit_price_paise"), rs.getLong("total_paise"),
            Db.intOrNull(rs,"sessions_included"), rs.getInt("sessions_used"), rs.getString("status"),
            Db.instant(rs,"created_at"), Db.instant(rs,"paid_at"), Db.instant(rs,"fulfilled_at"));
    }

    @Transactional
    public void cancelMemberOrder(long orderId) {
        long memberId = jdbc.sql("SELECT id FROM member_profile WHERE user_id=:user AND status='ACTIVE'")
            .param("user", CurrentUser.id()).query(Long.class).optional()
            .orElseThrow(() -> ApiException.notFound("Active member profile not found."));
        cancelOrder(orderId, memberId);
    }

    @Transactional
    public void cancelOrder(long orderId, Long requiredMemberId) {
        Map<String,Object> order = jdbc.sql("""
            SELECT o.member_id, o.addon_id, o.status, a.kind, o.quantity
            FROM addon_order o JOIN gym_addon a ON a.id=o.addon_id WHERE o.id=:id FOR UPDATE
            """).param("id",orderId).query().optional().orElseThrow(()->ApiException.notFound("Add-on order not found."));
        if (requiredMemberId != null && ((Number)order.get("member_id")).longValue()!=requiredMemberId)
            throw ApiException.notFound("Add-on order not found.");
        if (!"PAYMENT_PENDING".equals(order.get("status")))
            throw ApiException.conflict("ORDER_NOT_CANCELLABLE","Only unpaid add-on orders can be cancelled.");
        if ("PRODUCT".equals(order.get("kind"))) {
            jdbc.sql("UPDATE gym_addon SET stock_on_hand=stock_on_hand+:qty, updated_at=now() WHERE id=:id")
                .param("qty",order.get("quantity")).param("id",order.get("addon_id")).update();
        }
        jdbc.sql("UPDATE addon_order SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE id=:id")
            .param("id",orderId).update();
        audit.record("ADDON_ORDER_CANCELLED","ADDON_ORDER",orderId,Map.of("memberId",order.get("member_id")));
    }

    @Transactional
    public OrderDto fulfill(long orderId) {
        OrderDto order=getOrder(orderId);
        if ("FULFILLED".equals(order.status())) throw ApiException.conflict("ALREADY_FULFILLED","Order is already fulfilled.");
        if ("PT".equals(order.kind())) throw ApiException.conflict("PT_SESSION_TRACKING_REQUIRED","Record PT sessions individually.");
        if (!"PAID".equals(order.status()) && !"IN_PROGRESS".equals(order.status()))
            throw ApiException.conflict("PAYMENT_REQUIRED","Record and associate the payment before fulfilment.");
        jdbc.sql("UPDATE addon_order SET status='FULFILLED', fulfilled_at=now(), updated_at=now() WHERE id=:id")
            .param("id",orderId).update();
        audit.record("ADDON_ORDER_FULFILLED","ADDON_ORDER",orderId,Map.of("memberId",order.memberId()));
        return getOrder(orderId);
    }

    @Transactional
    public OrderDto recordSession(long orderId) {
        OrderDto order=getOrder(orderId);
        if (!"PT".equals(order.kind()) || (!"PAID".equals(order.status()) && !"IN_PROGRESS".equals(order.status())))
            throw ApiException.conflict("PT_ORDER_NOT_ACTIVE","Only paid PT packages can record sessions.");
        if (order.sessionsIncluded()==null || order.sessionsUsed()>=order.sessionsIncluded())
            throw ApiException.conflict("NO_SESSIONS_LEFT","No PT sessions remain.");
        int used=order.sessionsUsed()+1;
        String status=used>=order.sessionsIncluded()?"FULFILLED":"IN_PROGRESS";
        jdbc.sql("UPDATE addon_order SET sessions_used=:used, status=:status, fulfilled_at=CASE WHEN :status='FULFILLED' THEN now() ELSE fulfilled_at END, updated_at=now() WHERE id=:id")
            .param("used",used).param("status",status).param("id",orderId).update();
        audit.record("PT_SESSION_RECORDED","ADDON_ORDER",orderId,Map.of("sessionsUsed",used,"sessionsIncluded",order.sessionsIncluded()));
        return getOrder(orderId);
    }

    private static Integer intOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Map<String,Object> describe(AddOnRequest req) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("kind",req.kind());out.put("name",req.name());out.put("pricePaise",req.pricePaise());
        out.put("validityDays",req.validityDays());out.put("stockOnHand",req.stockOnHand());
        out.put("sessionsIncluded",req.sessionsIncluded());out.put("active",req.active());
        return out;
    }
}
