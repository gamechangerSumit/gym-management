package app.gym.payment;

import java.util.List;

/**
 * Dues are always recomputed from records: SUM(price of non-cancelled memberships) minus SUM(amount of RECORDED
 * payments). There is no stored balance to drift. The SQL view member_dues_v implements the same rule for bulk reads;
 * tests assert both agree.
 */
public final class DuesCalculator {

    public record ChargeLine(long pricePaise, String status) {}

    public record PaymentLine(long amountPaise, String status) {}

    public record Dues(long billedPaise, long paidPaise, long duesPaise) {}

    private DuesCalculator() {}

    public static Dues compute(List<ChargeLine> memberships, List<PaymentLine> payments) {
        long billed = 0;
        for (ChargeLine m : memberships) {
            if (!"CANCELLED".equals(m.status())) {
                billed = Math.addExact(billed, m.pricePaise());
            }
        }
        long paid = 0;
        for (PaymentLine p : payments) {
            if ("RECORDED".equals(p.status())) {
                paid = Math.addExact(paid, p.amountPaise());
            }
        }
        return new Dues(billed, paid, billed - paid);
    }
}
