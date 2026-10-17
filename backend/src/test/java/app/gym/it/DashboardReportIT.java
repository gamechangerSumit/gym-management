package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.auth.StaffPermissions;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardReportIT extends app.gym.support.AbstractIT {

    private long plan;

    private void seed() throws Exception {
        plan = createPlan("Monthly", 30, 100000);
        long m1 = createMember("Expiring One", "9500000001");
        long m2 = createMember("Active Two", "9500000002");
        long m3 = createMember("Expired Three", "9500000003");
        long m4 = createMember("Dues Four", "9500000004");
        assign(m1, plan, "2026-09-19"); // ends 2026-10-18 -> expiring soon
        assign(m2, plan, "2026-10-10"); // active
        assign(m3, plan, "2026-09-11"); // ended 2026-10-10 -> expired
        assign(m4, plan, "2026-10-01"); // active, unpaid
        pay(m1, 30000);
        pay(m2, 100000);
        pay(m3, 100000);
    }

    private void pay(long member, long amount) throws Exception {
        post("/api/v1/payments", map("memberId", member, "amountPaise", amount, "method", "CASH"), owner)
                .andExpect(status().isCreated());
    }

    @Test
    void summaryCountsMatchSeededData() throws Exception {
        seed();
        get("/api/v1/dashboard/summary", owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMembers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.expiringSoon").value(1))
                .andExpect(jsonPath("$.expired").value(1))
                .andExpect(jsonPath("$.collections.totalPaise").value(230000))
                .andExpect(jsonPath("$.collections.paymentCount").value(3));
    }

    @Test
    void actionListItemsCarryTheirRule() throws Exception {
        seed();
        List<String> rules = read(get("/api/v1/dashboard/action-list", owner).andExpect(status().isOk()), "$.items[*].rule");
        assertThat(rules).isNotEmpty().allMatch(r -> r != null && !r.isBlank());
        List<String> types = read(get("/api/v1/dashboard/action-list", owner), "$.items[*].type");
        assertThat(types).isNotEmpty();
    }

    @Test
    void collectionsReportTotalsReconcileWithRows() throws Exception {
        seed();
        var res = get("/api/v1/reports/collections?from=2026-10-01&to=2026-10-31", owner).andExpect(status().isOk());
        Number total = read(res, "$.totalPaise");
        List<Number> amounts = read(res, "$.rows.items[*].amountPaise");
        assertThat(amounts.stream().mapToLong(Number::longValue).sum()).isEqualTo(total.longValue());
        assertThat(total.longValue()).isEqualTo(230000L);
    }

    @Test
    void voidedPaymentsAreExcludedFromCollections() throws Exception {
        seed();
        long pid = ((Number) read(get("/api/v1/payments?size=1", owner), "$.items[0].id")).longValue();
        post("/api/v1/payments/" + pid + "/void", map("reason", "entered twice"), owner).andExpect(status().is2xxSuccessful());
        var res = get("/api/v1/reports/collections?from=2026-10-01&to=2026-10-31", owner).andExpect(status().isOk());
        assertThat(((Number) read(res, "$.voidedCount")).longValue()).isEqualTo(1L);
        assertThat(((Number) read(res, "$.paymentCount")).longValue()).isEqualTo(2L);
    }

    @Test
    void outstandingReportListsMembersWithDues() throws Exception {
        seed();
        var res = get("/api/v1/reports/outstanding", owner).andExpect(status().isOk());
        long total = ((Number) read(res, "$.totalDuesPaise")).longValue();
        List<Number> dues = read(res, "$.rows.items[*].duesPaise");
        assertThat(dues.stream().mapToLong(Number::longValue).sum()).isEqualTo(total);
        assertThat(total).isEqualTo(70000L + 100000L);
    }

    @Test
    void staffWithoutReportPermissionCannotSeeFinanceReports() throws Exception {
        seed();
        Who plain = createStaff("plainstaff", new StaffPermissions(false, false, false, false));
        get("/api/v1/reports/collections", plain).andExpect(status().isForbidden());
        get("/api/v1/reports/outstanding", plain).andExpect(status().isForbidden());
        // dashboard is allowed but carries no finance block
        get("/api/v1/dashboard/summary", plain).andExpect(status().isOk()).andExpect(jsonPath("$.collections").doesNotExist());
        get("/api/v1/dashboard/health", plain).andExpect(status().isForbidden());
        Who viewer = createStaff("viewerstaff", new StaffPermissions(false, false, false, true));
        get("/api/v1/reports/collections", viewer).andExpect(status().isOk());
    }

    @Test
    void invalidRangeIsRejected() throws Exception {
        get("/api/v1/reports/collections?from=2026-10-10&to=2026-10-01", owner).andExpect(status().isBadRequest());
    }

    @Test
    void membersCannotReachDashboards() throws Exception {
        long m = createMember("Some Member", "9500000009");
        Who mu = createMemberUser("somemember", m);
        get("/api/v1/dashboard/summary", mu).andExpect(status().isForbidden());
        get("/api/v1/reports/attendance", mu).andExpect(status().isForbidden());
    }

    @Test
    void healthScoreAndLeakageAreOwnerOnlyAndExplainThemselves() throws Exception {
        seed();
        get("/api/v1/dashboard/health", owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.disclaimer").isNotEmpty())
                .andExpect(jsonPath("$.factors[0].rule").isNotEmpty());
        get("/api/v1/dashboard/leakage", owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.indicators[0].rule").isNotEmpty());
    }

    @Test
    void followupStatusCanBeUpdated() throws Exception {
        seed();
        long membershipId = jdbc.sql("SELECT id FROM membership ORDER BY id LIMIT 1").query(Long.class).single();
        call(org.springframework.http.HttpMethod.PUT, "/api/v1/followups",
                map("membershipId", membershipId, "contactStatus", "CONTACTED", "note", "Will renew Friday"), owner)
                .andExpect(status().isNoContent());
        call(org.springframework.http.HttpMethod.PUT, "/api/v1/followups",
                map("membershipId", membershipId, "contactStatus", "BOGUS"), owner)
                .andExpect(status().is4xxClientError());
    }
}
