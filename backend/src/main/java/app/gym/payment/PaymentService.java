package app.gym.payment;

import app.gym.audit.AuditService;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.common.Money;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.payment.DuesCalculator.Dues;
import app.gym.payment.PaymentDtos.PaymentDto;
import app.gym.payment.PaymentDtos.PaymentRequest;
import app.gym.renewal.RenewalOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import app.gym.settings.GymClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    static final Set<String> METHODS = Set.of("CASH", "UPI", "CARD", "BANK_TRANSFER", "OTHER");

    private static final String SELECT = "SELECT p.id, p.member_id, m.member_code, m.full_name, p.membership_id, "
            + "p.amount_paise, p.paid_at, p.method, p.receipt_no, p.status, p.void_reason, p.voided_at, "
            + "u.login_id AS recorded_by, p.note, p.addon_order_id, p.renewal_order_id FROM payment p JOIN member_profile m ON m.id = p.member_id "
            + "LEFT JOIN user_account u ON u.id = p.recorded_by ";

    static final RowMapper<PaymentDto> MAPPER = (rs, i) -> new PaymentDto(rs.getLong("id"), rs.getLong("member_id"),
            rs.getString("member_code"), rs.getString("full_name"), Db.longOrNull(rs, "membership_id"),
            rs.getLong("amount_paise"), Db.instant(rs, "paid_at"), rs.getString("method"), rs.getString("receipt_no"),
            rs.getString("status"), rs.getString("void_reason"), Db.instant(rs, "voided_at"),
            rs.getString("recorded_by"), rs.getString("note"), Db.longOrNull(rs, "addon_order_id"), Db.longOrNull(rs, "renewal_order_id"));

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final AuditService audit;
    private final DuesService dues;
    private final RenewalOrderService renewalOrders;

    @Autowired
    public PaymentService(JdbcClient jdbc, GymClock clock, AuditService audit, DuesService dues,
                          RenewalOrderService renewalOrders) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
        this.dues = dues;
        this.renewalOrders = renewalOrders;
    }

    public PaymentService(JdbcClient jdbc, GymClock clock, AuditService audit, DuesService dues) {
        this(jdbc, clock, audit, dues, null);
    }

    public PaymentDto get(long id) {
        return jdbc.sql(SELECT + "WHERE p.id = :id").param("id", id).query(MAPPER).optional()
                .orElseThrow(() -> ApiException.notFound("Payment not found."));
    }

    public PageResponse<PaymentDto> search(Long memberId, LocalDate from, LocalDate to, String status, Paging paging) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (memberId != null) {
            where.append(" AND p.member_id = :member");
            params.put("member", memberId);
        }
        if (from != null) {
            where.append(" AND p.paid_at >= :from");
            params.put("from", Db.ts(clock.startOfDay(from)));
        }
        if (to != null) {
            where.append(" AND p.paid_at < :to");
            params.put("to", Db.ts(clock.startOfDay(to.plusDays(1))));
        }
        if (status != null && !status.isBlank()) {
            if (!"RECORDED".equals(status) && !"VOID".equals(status)) {
                throw ApiException.badRequest("INVALID_STATUS", "Status must be RECORDED or VOID.");
            }
            where.append(" AND p.status = :status");
            params.put("status", status);
        }
        long total = jdbc.sql("SELECT count(*) FROM payment p" + where).params(params).query(Long.class).single();
        Map<String, Object> listParams = new HashMap<>(params);
        listParams.put("lim", paging.size());
        listParams.put("off", paging.offset());
        List<PaymentDto> items = jdbc.sql(SELECT + where + " ORDER BY p.paid_at DESC, p.id DESC LIMIT :lim OFFSET :off")
                .params(listParams).query(MAPPER).list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    /**
     * Records a payment in one transaction. The member row is locked so concurrent payments for the same member
     * cannot both pass the overpayment check; the receipt number comes from a database sequence (unique by construction).
     */
    @Transactional
    public PaymentDto record(PaymentRequest req) {
        if (!METHODS.contains(req.method())) {
            throw ApiException.badRequest("INVALID_METHOD", "Payment method must be one of " + METHODS + ".");
        }
        jdbc.sql("SELECT id FROM member_profile WHERE id = :id FOR UPDATE").param("id", req.memberId())
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Member not found."));

        Map<String, Object> addonOrder = null;
        if (req.addonOrderId() != null) {
            if (req.membershipId() != null) {
                throw ApiException.badRequest("INVALID_PAYMENT_LINK", "An add-on payment cannot be linked to a membership at the same time.");
            }
            addonOrder = jdbc.sql("SELECT member_id, total_paise, status FROM addon_order WHERE id=:id FOR UPDATE")
                    .param("id", req.addonOrderId()).query((rs,row) -> {
                        Map<String,Object> item = new HashMap<>();
                        item.put("member_id",rs.getLong("member_id")); item.put("total_paise",rs.getLong("total_paise"));
                        item.put("status",rs.getString("status")); return item;
                    }).optional()
                    .orElseThrow(() -> ApiException.notFound("Add-on order not found."));
            if (((Number) addonOrder.get("member_id")).longValue() != req.memberId()) {
                throw ApiException.badRequest("ADDON_MEMBER_MISMATCH", "This add-on order belongs to another member.");
            }
            if (!"PAYMENT_PENDING".equals(addonOrder.get("status"))) {
                throw ApiException.conflict("ADDON_NOT_PAYABLE", "This add-on order is not awaiting payment.");
            }
            if (((Number) addonOrder.get("total_paise")).longValue() != req.amountPaise()) {
                throw ApiException.badRequest("ADDON_AMOUNT_MISMATCH", "Payment amount must exactly match the add-on order total.");
            }
        }

        Map<String, Object> renewalOrder = null;
        if (req.renewalOrderId() != null) {
            if (req.membershipId() != null || req.addonOrderId() != null)
                throw ApiException.badRequest("INVALID_PAYMENT_LINK", "A renewal payment cannot also link to a membership or add-on order.");
            renewalOrder = jdbc.sql("SELECT member_id, price_paise, status FROM renewal_order WHERE id=:id FOR UPDATE")
                    .param("id", req.renewalOrderId()).query((rs,row) -> {
                        Map<String,Object> item = new HashMap<>();
                        item.put("member_id",rs.getLong("member_id")); item.put("price_paise",rs.getLong("price_paise"));
                        item.put("status",rs.getString("status")); return item;
                    }).optional()
                    .orElseThrow(() -> ApiException.notFound("Renewal order not found."));
            if (((Number) renewalOrder.get("member_id")).longValue() != req.memberId())
                throw ApiException.badRequest("RENEWAL_MEMBER_MISMATCH", "This renewal order belongs to another member.");
            if (!"PAYMENT_PENDING".equals(renewalOrder.get("status")))
                throw ApiException.conflict("RENEWAL_NOT_PAYABLE", "This renewal order is not awaiting payment.");
            if (((Number) renewalOrder.get("price_paise")).longValue() != req.amountPaise())
                throw ApiException.badRequest("RENEWAL_AMOUNT_MISMATCH", "Payment amount must exactly match the renewal total.");
        }

        if (req.membershipId() != null) {
            boolean ok = Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM membership WHERE id = :ms "
                            + "AND member_id = :m AND status <> 'CANCELLED')")
                    .param("ms", req.membershipId()).param("m", req.memberId()).query(Boolean.class).single());
            if (!ok) {
                throw ApiException.badRequest("INVALID_MEMBERSHIP", "That membership does not belong to this member.");
            }
        }
        Instant now = clock.now();
        Instant paidAt = req.paidAt() != null ? req.paidAt() : now;
        if (paidAt.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw ApiException.badRequest("INVALID_DATE", "The payment date cannot be in the future.");
        }
        Dues current = dues.duesFor(req.memberId());
        if (req.addonOrderId() == null && req.renewalOrderId() == null && req.amountPaise() > current.duesPaise() && !Boolean.TRUE.equals(req.allowOverpayment())) {
            throw ApiException.unprocessable("OVERPAYMENT", "This payment (" + Money.formatInr(req.amountPaise())
                    + ") is more than the outstanding dues (" + Money.formatInr(Math.max(0, current.duesPaise()))
                    + "). Reduce the amount, or confirm it is an advance payment.");
        }
        Map<String, Object> row = jdbc.sql("INSERT INTO payment (member_id, membership_id, amount_paise, paid_at, method, "
                        + "receipt_no, recorded_by, note, addon_order_id, renewal_order_id) VALUES (:m, :ms, :amt, :at, :method, "
                        + "'RC-' || lpad(nextval('receipt_seq')::text, 6, '0'), :by, :note, :addonOrder, :renewalOrder) RETURNING id, receipt_no")
                .param("m", req.memberId())
                .param("ms", req.membershipId())
                .param("amt", req.amountPaise())
                .param("at", Db.ts(paidAt))
                .param("method", req.method())
                .param("by", CurrentUser.idOrNull())
                .param("note", req.note() == null || req.note().isBlank() ? (req.addonOrderId() == null ? null : "Add-on order #" + req.addonOrderId()) : req.note().trim())
                .param("addonOrder", req.addonOrderId())
                .param("renewalOrder", req.renewalOrderId())
                .query().singleRow();
        long id = ((Number) row.get("id")).longValue();
        if (req.addonOrderId() != null) {
            int paidOrder = jdbc.sql("UPDATE addon_order SET status='PAID', paid_at=:paidAt, updated_at=now() WHERE id=:id AND status='PAYMENT_PENDING'")
                    .param("paidAt", Db.ts(paidAt)).param("id", req.addonOrderId()).update();
            if (paidOrder != 1) throw ApiException.conflict("ADDON_NOT_PAYABLE", "This add-on order is no longer awaiting payment.");
        }
        if (req.renewalOrderId() != null) {
            if (renewalOrders == null) throw new IllegalStateException("RenewalOrderService is required for renewal payments.");
            renewalOrders.completePayment(req.renewalOrderId(), id);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("memberId", req.memberId());
        details.put("amountPaise", req.amountPaise());
        details.put("method", req.method());
        details.put("receiptNo", row.get("receipt_no"));
        if (req.addonOrderId() != null) details.put("addonOrderId", req.addonOrderId());
        if (req.renewalOrderId() != null) details.put("renewalOrderId", req.renewalOrderId());
        audit.record("PAYMENT_RECORDED", "PAYMENT", id, details);
        return get(id);
    }

    /** Payments are never deleted: voiding keeps the row, records who and why, and removes it from dues. */
    @Transactional
    public PaymentDto voidPayment(long id, String reason) {
        PaymentDto payment = get(id);
        if (payment.renewalOrderId() != null) {
            throw ApiException.conflict("RENEWAL_PAYMENT_REVERSAL_REQUIRED", "A verified renewal payment cannot be voided without a membership reversal workflow.");
        }
        if (payment.addonOrderId() != null) {
            String orderStatus = jdbc.sql("SELECT status FROM addon_order WHERE id=:id")
                    .param("id", payment.addonOrderId()).query(String.class).optional().orElse("CANCELLED");
            if ("FULFILLED".equals(orderStatus) || "IN_PROGRESS".equals(orderStatus)) {
                throw ApiException.conflict("ADDON_ALREADY_USED", "An add-on order with fulfilment or PT usage cannot have its payment voided.");
            }
        }
        if (!"RECORDED".equals(payment.status())) {
            throw ApiException.conflict("ALREADY_VOID", "This payment has already been voided.");
        }
        jdbc.sql("SELECT id FROM member_profile WHERE id = :id FOR UPDATE").param("id", payment.memberId())
                .query(Long.class).single();
        jdbc.sql("UPDATE payment SET status = 'VOID', void_reason = :r, voided_by = :by, voided_at = now() "
                        + "WHERE id = :id AND status = 'RECORDED'")
                .param("r", reason.trim()).param("by", CurrentUser.idOrNull()).param("id", id).update();
        if (payment.addonOrderId() != null) {
            jdbc.sql("UPDATE addon_order SET status='PAYMENT_PENDING', paid_at=NULL, updated_at=now() WHERE id=:id")
                    .param("id", payment.addonOrderId()).update();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("memberId", payment.memberId());
        details.put("amountPaise", payment.amountPaise());
        details.put("receiptNo", payment.receiptNo());
        details.put("reason", reason.trim());
        audit.record("PAYMENT_VOIDED", "PAYMENT", id, details);
        return get(id);
    }
}
