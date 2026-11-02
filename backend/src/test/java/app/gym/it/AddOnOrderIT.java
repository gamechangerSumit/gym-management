package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AddOnOrderIT extends app.gym.support.AbstractIT {

    @Test
    void productOrderReservesStockAndAddonPaymentDoesNotReduceMembershipDues() throws Exception {
        long memberId = createMember("Add-on Member", "9800000102");
        long planId = createPlan("Add-on Base Plan", 30, 150000);
        assign(memberId, planId, "2026-10-15");
        Who member = createMemberUser("addon-member", memberId);
        long duesBefore = jdbc.sql("SELECT dues_paise FROM member_dues_v WHERE member_id=:m")
                .param("m", memberId).query(Long.class).single();

        var addon = post("/api/v1/addons", map(
                "kind", "PRODUCT",
                "name", "Protein tub",
                "description", "Sample product",
                "pricePaise", 250000,
                "validityDays", null,
                "stockOnHand", 2,
                "sessionsIncluded", null,
                "active", true
        ), owner).andExpect(status().isCreated());
        long addonId = ((Number) read(addon, "$.id")).longValue();

        var order = post("/api/v1/me/addon-orders", map("addonId", addonId, "quantity", 1), member)
                .andExpect(status().isCreated());
        long orderId = ((Number) read(order, "$.id")).longValue();
        assertThat(read(order, "$.status").toString()).isEqualTo("PAYMENT_PENDING");
        assertThat(jdbc.sql("SELECT stock_on_hand FROM gym_addon WHERE id=:id").param("id",addonId)
                .query(Integer.class).single()).isEqualTo(1);

        post("/api/v1/payments", map(
                "memberId", memberId,
                "membershipId", null,
                "amountPaise", 250000,
                "method", "CASH",
                "note", "Verified add-on payment",
                "allowOverpayment", false,
                "addonOrderId", orderId,
                "renewalOrderId", null
        ), owner).andExpect(status().isCreated());

        assertThat(jdbc.sql("SELECT status FROM addon_order WHERE id=:id").param("id",orderId)
                .query(String.class).single()).isEqualTo("PAID");
        long duesAfter = jdbc.sql("SELECT dues_paise FROM member_dues_v WHERE member_id=:m")
                .param("m", memberId).query(Long.class).single();
        assertThat(duesAfter).isEqualTo(duesBefore);

        post("/api/v1/addon-orders/" + orderId + "/fulfill", map(), owner)
                .andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT status FROM addon_order WHERE id=:id").param("id",orderId)
                .query(String.class).single()).isEqualTo("FULFILLED");
    }
}
