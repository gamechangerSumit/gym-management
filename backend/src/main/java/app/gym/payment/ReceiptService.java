package app.gym.payment;

import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.common.Money;
import app.gym.payment.PaymentDtos.ReceiptDto;
import app.gym.settings.GymSettings;
import app.gym.settings.GymSettingsService;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a z", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final GymSettingsService settings;

    public ReceiptService(JdbcClient jdbc, GymSettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    public ReceiptDto receipt(long paymentId) {
        GymSettings gym = settings.get();
        return jdbc.sql("SELECT p.receipt_no, p.status, p.void_reason, p.paid_at, p.amount_paise, p.method, p.note, "
                        + "m.member_code, m.full_name, pl.name AS plan_name FROM payment p "
                        + "JOIN member_profile m ON m.id = p.member_id "
                        + "LEFT JOIN membership ms ON ms.id = p.membership_id "
                        + "LEFT JOIN membership_plan pl ON pl.id = ms.plan_id WHERE p.id = :id")
                .param("id", paymentId)
                .query((rs, i) -> {
                    java.time.Instant paidAt = Db.instant(rs, "paid_at");
                    long amount = rs.getLong("amount_paise");
                    return new ReceiptDto(gym.gymName(), rs.getString("receipt_no"), rs.getString("status"),
                            rs.getString("void_reason"), paidAt, paidAt.atZone(gym.zone()).format(WHEN), amount,
                            Money.formatInr(amount), rs.getString("method"), rs.getString("member_code"),
                            rs.getString("full_name"), rs.getString("plan_name"), rs.getString("note"));
                })
                .optional()
                .orElseThrow(() -> ApiException.notFound("Receipt not found."));
    }

    /** Standalone printable HTML. Every dynamic value is escaped. */
    public String html(ReceiptDto r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Receipt ").append(esc(r.receiptNo())).append("</title>")
                .append("<style>body{font-family:system-ui,sans-serif;max-width:420px;margin:24px auto;padding:0 16px;color:#111}")
                .append("h1{font-size:20px;margin:0 0 4px}.muted{color:#555;font-size:13px}")
                .append("table{width:100%;border-collapse:collapse;margin-top:16px}td{padding:6px 0;border-bottom:1px solid #ddd}")
                .append("td:last-child{text-align:right}.total td{font-weight:700;font-size:18px;border-bottom:2px solid #111}")
                .append(".void{color:#b00020;font-weight:700;border:2px solid #b00020;display:inline-block;padding:2px 8px;margin-top:8px}")
                .append("@media print{body{margin:0}}</style></head><body>");
        sb.append("<h1>").append(esc(r.gymName())).append("</h1><div class=\"muted\">Payment receipt</div>");
        if ("VOID".equals(r.status())) {
            sb.append("<div class=\"void\">VOIDED").append(r.voidReason() == null ? "" : " - " + esc(r.voidReason()))
                    .append("</div>");
        }
        sb.append("<table>");
        row(sb, "Receipt no.", r.receiptNo());
        row(sb, "Date", r.paidAtText());
        row(sb, "Member", r.memberName() + " (" + r.memberCode() + ")");
        if (r.planName() != null) {
            row(sb, "Plan", r.planName());
        }
        row(sb, "Method", r.method().replace('_', ' '));
        if (r.note() != null) {
            row(sb, "Note", r.note());
        }
        sb.append("<tr class=\"total\"><td>Amount</td><td>").append(esc(r.amountText())).append("</td></tr></table>");
        sb.append("<p class=\"muted\">Thank you. This receipt does not contain card or bank details.</p></body></html>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><td>").append(esc(label)).append("</td><td>").append(esc(value)).append("</td></tr>");
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
