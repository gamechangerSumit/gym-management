package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.auth.StaffPermissions;
import app.gym.payment.DuesCalculator;
import app.gym.payment.DuesService;
import app.gym.payment.PaymentDtos.PaymentRequest;
import app.gym.payment.PaymentService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


class PaymentIT extends app.gym.support.AbstractIT {

    @Autowired PaymentService paymentService;
    @Autowired DuesService duesService;

    private long member;
    private long plan;

    private void setUp() throws Exception {
        member = createMember("Pay Person", "9400000001");
        plan = createPlan("Monthly", 30, 150000);
        assign(member, plan, "2026-10-01");
    }

    private long pay(long amount, String method) throws Exception {
        return id(post("/api/v1/payments", map("memberId", member, "amountPaise", amount, "method", method), owner)
                .andExpect(status().isCreated()));
    }

    @Test
    void fullFlowMemberPlanPaymentReceipt() throws Exception {
        setUp();
        long p1 = id(post("/api/v1/payments", map("memberId", member, "amountPaise", 50000, "method", "CASH", "note", "part 1"), owner)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.receiptNo").value("RC-000001"))
                .andExpect(jsonPath("$.status").value("RECORDED")));
        get("/api/v1/members/" + member + "/dues", owner)
                .andExpect(jsonPath("$.billedPaise").value(150000)).andExpect(jsonPath("$.paidPaise").value(50000))
                .andExpect(jsonPath("$.duesPaise").value(100000));
        pay(100000, "UPI");
        get("/api/v1/members/" + member + "/dues", owner).andExpect(jsonPath("$.duesPaise").value(0));
        get("/api/v1/payments/" + p1 + "/receipt", owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.gymName").value("Test Gym"))
                .andExpect(jsonPath("$.receiptNo").value("RC-000001")).andExpect(jsonPath("$.amountText").value("₹500.00"))
                .andExpect(jsonPath("$.memberCode").value("M00001"));
        get("/api/v1/members/" + member, owner).andExpect(jsonPath("$.duesPaise").value(0));
    }

    @Test
    void overpaymentIsRejectedUnlessExplicitlyAllowed() throws Exception {
        setUp();
        pay(150000, "CASH");
        post("/api/v1/payments", map("memberId", member, "amountPaise", 1, "method", "CASH"), owner)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("OVERPAYMENT"));
        post("/api/v1/payments", map("memberId", member, "amountPaise", 1, "method", "CASH", "allowOverpayment", true), owner)
                .andExpect(status().isCreated());
        get("/api/v1/members/" + member + "/dues", owner).andExpect(jsonPath("$.duesPaise").value(-1));
    }

    @Test
    void invalidPaymentsAreRejected() throws Exception {
        setUp();
        post("/api/v1/payments", map("memberId", member, "amountPaise", 100, "method", "BITCOIN"), owner).andExpect(status().isBadRequest());
        post("/api/v1/payments", map("memberId", member, "amountPaise", 0, "method", "CASH"), owner).andExpect(status().isBadRequest());
        post("/api/v1/payments", map("memberId", member, "amountPaise", -5, "method", "CASH"), owner).andExpect(status().isBadRequest());
        post("/api/v1/payments", map("memberId", 99999, "amountPaise", 5, "method", "CASH"), owner).andExpect(status().isNotFound());
        post("/api/v1/payments", map("memberId", member, "amountPaise", 5, "method", "CASH", "paidAt", "2030-01-01T00:00:00Z"), owner)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE"));
        // Card details are not accepted anywhere (strict JSON binding rejects unknown fields).
        post("/api/v1/payments", map("memberId", member, "amountPaise", 5, "method", "CARD", "cardNumber", "4111111111111111"), owner)
                .andExpect(status().isBadRequest());
    }

    @Test
    void voidRequiresOwnerAndReasonAndRestoresDues() throws Exception {
        setUp();
        long p = pay(150000, "CASH");
        Who staff = createStaff("desk", StaffPermissions.all());
        post("/api/v1/payments/" + p + "/void", map("reason", "wrong member"), staff).andExpect(status().isForbidden());
        post("/api/v1/payments/" + p + "/void", map("reason", " "), owner).andExpect(status().isBadRequest());
        post("/api/v1/payments/" + p + "/void", map("reason", "wrong member"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID")).andExpect(jsonPath("$.voidReason").value("wrong member"));
        get("/api/v1/members/" + member + "/dues", owner).andExpect(jsonPath("$.duesPaise").value(150000));
        post("/api/v1/payments/" + p + "/void", map("reason", "again"), owner).andExpect(status().isConflict());
        // The row still exists (never deleted) and the void is audited.
        assertThat(jdbc.sql("SELECT count(*) FROM payment").query(Long.class).single()).isEqualTo(1);
        get("/api/v1/audit?entityType=PAYMENT", owner).andExpect(jsonPath("$.items[?(@.action=='PAYMENT_VOIDED')]").isNotEmpty());
    }

    @Test
    void staffWithoutPaymentPermissionGets403() throws Exception {
        setUp();
        Who none = createStaff("desk-none", StaffPermissions.none());
        Who cashier = createStaff("desk-cash", new StaffPermissions(true, false, false, false));
        post("/api/v1/payments", map("memberId", member, "amountPaise", 100, "method", "CASH"), none).andExpect(status().isForbidden());
        get("/api/v1/payments", none).andExpect(status().isForbidden());
        get("/api/v1/members/" + member + "/dues", none).andExpect(status().isForbidden());
        post("/api/v1/payments", map("memberId", member, "amountPaise", 100, "method", "CASH"), cashier).andExpect(status().isCreated());
        get("/api/v1/payments?memberId=" + member, cashier).andExpect(jsonPath("$.totalItems").value(1));
        // Dues are hidden from staff who cannot touch money or memberships.
        get("/api/v1/members/" + member, none).andExpect(jsonPath("$.duesPaise").doesNotExist());
    }

    @Test
    void receiptHtmlEscapesMemberData() throws Exception {
        long m = createMember("Tom <b>Bold</b>", "9400000002");
        long pl = createPlan("P", 30, 1000);
        assign(m, pl, "2026-10-01");
        long p = id(post("/api/v1/payments", map("memberId", m, "amountPaise", 1000, "method", "CASH"), owner));
        String html = get("/api/v1/payments/" + p + "/receipt.html", owner).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Test Gym").contains("RC-000001").contains("Tom &lt;b&gt;Bold&lt;/b&gt;").doesNotContain("<b>Bold");
    }

    @Test
    void receiptNumbersAreUniqueUnderConcurrentRequests() throws Exception {
        long bigPlan = createPlan("Big", 30, 100_000_000L);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long m = createMember("Concurrent " + (char) ('A' + i) + "uthor", String.format("95000000%02d", i));
            assign(m, bigPlan, "2026-10-01");
            members.add(m);
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (long m : members) {
                Callable<String> task = () -> paymentService.record(new PaymentRequest(m, null, 100, "CASH", null, null, false)).receiptNo();
                futures.add(pool.submit(task));
            }
            Set<String> numbers = new HashSet<>();
            for (Future<String> f : futures) {
                numbers.add(f.get());
            }
            assertThat(numbers).hasSize(20);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.sql("SELECT count(DISTINCT receipt_no) FROM payment").query(Long.class).single()).isEqualTo(20);
    }

    @Test
    void concurrentPaymentsForOneMemberCannotBothPassTheOverpaymentCheck() throws Exception {
        setUp(); // dues = 150000
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        paymentService.record(new PaymentRequest(member, null, 100000, "CASH", null, null, false));
                        return true;
                    } catch (app.gym.common.ApiException e) {
                        return false;
                    }
                }));
            }
            int ok = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) ok++;
            }
            assertThat(ok).isEqualTo(1); // second 100000 would exceed the remaining 50000
        } finally {
            pool.shutdownNow();
        }
        assertThat(duesService.duesFor(member).duesPaise()).isEqualTo(50000);
    }

    /** Property-style: for many random members, the SQL view, the calculator and the API agree with raw sums. */
    @Test
    void duesAlwaysEqualRecomputedTotals() throws Exception {
        long p = createPlan("Any", 30, 1);
        Random rnd = new Random(7);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long m = createMember("Random Person " + i, String.format("96000000%02d", i));
            ids.add(m);
            int memberships = rnd.nextInt(4);
            for (int k = 0; k < memberships; k++) {
                jdbc.sql("INSERT INTO membership (member_id, plan_id, start_date, end_date, price_charged_paise, status) "
                                + "VALUES (:m, :p, :s, :e, :price, :st)")
                        .param("m", m).param("p", p)
                        .param("s", LocalDate.of(2025, 1, 1).plusDays(100L * k))
                        .param("e", LocalDate.of(2025, 1, 30).plusDays(100L * k))
                        .param("price", 1000 + rnd.nextInt(200000))
                        .param("st", rnd.nextInt(4) == 0 ? "CANCELLED" : (rnd.nextInt(5) == 0 ? "ON_HOLD" : "ACTIVE"))
                        .update();
            }
            int payments = rnd.nextInt(6);
            for (int k = 0; k < payments; k++) {
                boolean voided = rnd.nextInt(4) == 0;
                jdbc.sql("INSERT INTO payment (member_id, amount_paise, paid_at, method, receipt_no, status, void_reason) "
                                + "VALUES (:m, :a, now(), 'CASH', :r, :st, :vr)")
                        .param("m", m).param("a", 1 + rnd.nextInt(150000)).param("r", "T-" + m + "-" + k)
                        .param("st", voided ? "VOID" : "RECORDED").param("vr", voided ? "test" : null).update();
            }
        }
        for (long m : ids) {
            long expectedBilled = jdbc.sql("SELECT COALESCE(SUM(price_charged_paise),0) FROM membership WHERE member_id=:m AND status<>'CANCELLED'")
                    .param("m", m).query(Long.class).single();
            long expectedPaid = jdbc.sql("SELECT COALESCE(SUM(amount_paise),0) FROM payment WHERE member_id=:m AND status='RECORDED'")
                    .param("m", m).query(Long.class).single();
            long view = jdbc.sql("SELECT dues_paise FROM member_dues_v WHERE member_id=:m").param("m", m).query(Long.class).single();
            DuesCalculator.Dues calc = duesService.duesFor(m);
            assertThat(view).isEqualTo(expectedBilled - expectedPaid);
            assertThat(calc.duesPaise()).isEqualTo(expectedBilled - expectedPaid);
            get("/api/v1/members/" + m + "/dues", owner).andExpect(jsonPath("$.duesPaise").value(expectedBilled - expectedPaid));
        }
    }
}
