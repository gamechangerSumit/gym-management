package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class RenewalOrderIT extends app.gym.support.AbstractIT {

    @Test
    void membershipIsExtendedOnlyAfterMatchingPaymentIsRecorded() throws Exception {
        long memberId = createMember("Renewal Member", "9800000101");
        long planId = createPlan("Renewal Plan", 30, 150000);
        assign(memberId, planId, "2026-10-15");
        Who member = createMemberUser("renewal-member", memberId);

        Integer before = jdbc.sql("SELECT count(*) FROM membership WHERE member_id=:m")
                .param("m", memberId).query(Integer.class).single();

        var created = post("/api/v1/me/renewal-orders", map("planId", planId), member)
                .andExpect(status().isCreated()).andReturn();
        long orderId = ((Number) read(created, "$.id")).longValue();
        assertThat(read(created, "$.status").toString()).isEqualTo("PAYMENT_PENDING");

        Integer whilePending = jdbc.sql("SELECT count(*) FROM membership WHERE member_id=:m")
                .param("m", memberId).query(Integer.class).single();
        assertThat(whilePending).isEqualTo(before);

        var payment = post("/api/v1/payments", map(
                "memberId", memberId,
                "membershipId", null,
                "amountPaise", 150000,
                "method", "CASH",
                "note", "Verified renewal payment",
                "allowOverpayment", false,
                "addonOrderId", null,
                "renewalOrderId", orderId
        ), owner).andExpect(status().isCreated()).andReturn();

        assertThat(read(payment, "$.renewalOrderId").toString()).isEqualTo(Long.toString(orderId));
        Integer after = jdbc.sql("SELECT count(*) FROM membership WHERE member_id=:m")
                .param("m", memberId).query(Integer.class).single();
        assertThat(after).isEqualTo(before + 1);

        String orderStatus = jdbc.sql("SELECT status FROM renewal_order WHERE id=:id")
                .param("id", orderId).query(String.class).single();
        assertThat(orderStatus).isEqualTo("PAID");
        Long linkedMembership = jdbc.sql("SELECT membership_id FROM payment WHERE renewal_order_id=:id")
                .param("id", orderId).query(Long.class).single();
        assertThat(linkedMembership).isNotNull();
    }
}
