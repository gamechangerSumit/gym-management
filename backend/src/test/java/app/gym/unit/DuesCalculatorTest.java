package app.gym.unit;

import static org.assertj.core.api.Assertions.assertThat;

import app.gym.payment.DuesCalculator;
import app.gym.payment.DuesCalculator.ChargeLine;
import app.gym.payment.DuesCalculator.PaymentLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DuesCalculatorTest {

    @Test
    void cancelledMembershipsAndVoidPaymentsAreIgnored() {
        var dues = DuesCalculator.compute(
                List.of(new ChargeLine(100000, "ACTIVE"), new ChargeLine(50000, "CANCELLED"), new ChargeLine(20000, "ON_HOLD")),
                List.of(new PaymentLine(30000, "RECORDED"), new PaymentLine(99999, "VOID")));
        assertThat(dues.billedPaise()).isEqualTo(120000);
        assertThat(dues.paidPaise()).isEqualTo(30000);
        assertThat(dues.duesPaise()).isEqualTo(90000);
    }

    @Test
    void noRecordsMeansZeroDues() {
        assertThat(DuesCalculator.compute(List.of(), List.of()).duesPaise()).isZero();
    }

    @Test
    void overpaymentGivesNegativeDues() {
        var dues = DuesCalculator.compute(List.of(new ChargeLine(1000, "ACTIVE")), List.of(new PaymentLine(1500, "RECORDED")));
        assertThat(dues.duesPaise()).isEqualTo(-500);
    }

    /** Property-style: for many random record sets the result equals an independent recomputation. */
    @Test
    void matchesIndependentRecomputationForManyRandomSets() {
        Random rnd = new Random(42);
        String[] mStatus = {"ACTIVE", "ON_HOLD", "CANCELLED"};
        String[] pStatus = {"RECORDED", "VOID"};
        for (int round = 0; round < 500; round++) {
            List<ChargeLine> charges = new ArrayList<>();
            List<PaymentLine> pays = new ArrayList<>();
            long expectedBilled = 0;
            long expectedPaid = 0;
            for (int i = rnd.nextInt(8); i > 0; i--) {
                long price = rnd.nextInt(500_000);
                String s = mStatus[rnd.nextInt(3)];
                charges.add(new ChargeLine(price, s));
                if (!s.equals("CANCELLED")) expectedBilled += price;
            }
            for (int i = rnd.nextInt(20); i > 0; i--) {
                long amt = 1 + rnd.nextInt(300_000);
                String s = pStatus[rnd.nextInt(2)];
                pays.add(new PaymentLine(amt, s));
                if (s.equals("RECORDED")) expectedPaid += amt;
            }
            var dues = DuesCalculator.compute(charges, pays);
            assertThat(dues.billedPaise()).isEqualTo(expectedBilled);
            assertThat(dues.paidPaise()).isEqualTo(expectedPaid);
            assertThat(dues.duesPaise()).isEqualTo(expectedBilled - expectedPaid);
        }
    }
}
