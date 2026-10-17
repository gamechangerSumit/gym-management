package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.auth.StaffPermissions;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MembershipIT extends app.gym.support.AbstractIT {

    private long member;
    private long plan;

    private void setUp() throws Exception {
        member = createMember("Mia Member", "9300000001");
        plan = createPlan("Monthly", 30, 150000);
    }

    @Test
    void endDateIsStartPlusDurationMinusOneAndPriceDefaultsFromPlan() throws Exception {
        setUp();
        post("/api/v1/members/" + member + "/memberships", map("planId", plan, "startDate", "2026-10-01"), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").value("2026-10-30"))
                .andExpect(jsonPath("$.pricePaise").value(150000))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.daysRemaining").value(15));
    }

    @Test
    void priceCanBeOverriddenAndPlanPriceChangesDoNotRewriteHistory() throws Exception {
        setUp();
        long ms = id(post("/api/v1/members/" + member + "/memberships",
                map("planId", plan, "startDate", "2026-10-01", "pricePaise", 120000), owner));
        put("/api/v1/plans/" + plan, map("name", "Monthly", "durationDays", 30, "pricePaise", 999900), owner);
        get("/api/v1/members/" + member + "/memberships", owner).andExpect(jsonPath("$[0].pricePaise").value(120000));
        assertThat(ms).isPositive();
    }

    @Test
    void overlappingMembershipIsRejectedButAdjacentIsAllowed() throws Exception {
        setUp();
        assign(member, plan, "2026-10-01");
        post("/api/v1/members/" + member + "/memberships", map("planId", plan, "startDate", "2026-10-20"), owner)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBERSHIP_OVERLAP"));
        post("/api/v1/members/" + member + "/memberships", map("planId", plan, "startDate", "2026-10-30"), owner)
                .andExpect(status().isConflict());
        post("/api/v1/members/" + member + "/memberships", map("planId", plan, "startDate", "2026-10-31"), owner)
                .andExpect(status().isCreated());
    }

    @Test
    void databaseConstraintAlsoRejectsOverlaps() throws Exception {
        setUp();
        assign(member, plan, "2026-10-01");
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO membership (member_id, plan_id, start_date, end_date, price_charged_paise) "
                        + "VALUES (:m, :p, :s, :e, 1)")
                .param("m", member).param("p", plan)
                .param("s", LocalDate.of(2026, 10, 30)).param("e", LocalDate.of(2026, 11, 5)).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        // A cancelled period does not block a new one.
        long first = jdbc.sql("SELECT id FROM membership").query(Long.class).single();
        post("/api/v1/memberships/" + first + "/cancel", map("reason", "entered by mistake"), owner).andExpect(status().isOk());
        assign(member, plan, "2026-10-10");
    }

    @Test
    void renewalCreatesALinkedRowAndKeepsHistory() throws Exception {
        setUp();
        long first = assign(member, plan, "2026-10-01");
        post("/api/v1/memberships/" + first + "/renew", null, owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-10-31"))
                .andExpect(jsonPath("$.endDate").value("2026-11-29"))
                .andExpect(jsonPath("$.renewedFromId").value(first));
        get("/api/v1/members/" + member + "/memberships", owner).andExpect(jsonPath("$.length()").value(2));
        // Old row untouched.
        post("/api/v1/memberships/" + first + "/renew", null, owner)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_RENEWED"));
        String endOfFirst = jdbc.sql("SELECT end_date::text FROM membership WHERE id = :id").param("id", first)
                .query(String.class).single();
        assertThat(endOfFirst).isEqualTo("2026-10-30");
    }

    @Test
    void renewingAnExpiredMembershipStartsToday() throws Exception {
        setUp();
        long first = assign(member, plan, "2026-08-01"); // ended 2026-08-30
        post("/api/v1/memberships/" + first + "/renew", null, owner)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.startDate").value("2026-10-15"));
    }

    @Test
    void derivedStatusFollowsTheClockWithInclusiveEndDate() throws Exception {
        setUp();
        assign(member, plan, "2026-10-01"); // 2026-10-01 .. 2026-10-30
        String[][] cases = {
                {"2026-09-30T04:00:00Z", "SCHEDULED"},
                {"2026-10-01T04:00:00Z", "ACTIVE"},
                {"2026-10-22T04:00:00Z", "ACTIVE"},          // 8 days left: outside the 7-day window
                {"2026-10-23T04:00:00Z", "EXPIRING_SOON"},   // 7 days left
                {"2026-10-30T04:00:00Z", "EXPIRING_SOON"},   // last day is still valid
                {"2026-10-31T04:00:00Z", "EXPIRED"},
        };
        for (String[] c : cases) {
            clock.set(Instant.parse(c[0]));
            get("/api/v1/members/" + member + "/memberships", owner)
                    .andExpect(jsonPath("$[0].status").value(c[1]));
            get("/api/v1/members/" + member, owner).andExpect(jsonPath("$.membershipStatus").value(c[1]));
        }
    }

    @Test
    void holdResumeAndCancel() throws Exception {
        setUp();
        long ms = assign(member, plan, "2026-10-01");
        post("/api/v1/memberships/" + ms + "/hold", map("reason", "travelling"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ON_HOLD"));
        post("/api/v1/memberships/" + ms + "/hold", map("reason", "again"), owner).andExpect(status().isConflict());
        post("/api/v1/memberships/" + ms + "/resume", null, owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        post("/api/v1/memberships/" + ms + "/cancel", map("reason", "refunded"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        // Cancelled memberships no longer count towards dues; the row is kept.
        get("/api/v1/members/" + member + "/dues", owner).andExpect(jsonPath("$.duesPaise").value(0));
        assertThat(jdbc.sql("SELECT count(*) FROM membership").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void permissionsAreEnforcedServerSide() throws Exception {
        setUp();
        long ms = assign(member, plan, "2026-10-01");
        Who none = createStaff("desk-none", StaffPermissions.none());
        Who manager = createStaff("desk-mgr", new StaffPermissions(false, true, false, false));
        post("/api/v1/members/" + member + "/memberships", map("planId", plan, "startDate", "2026-12-01"), none)
                .andExpect(status().isForbidden());
        post("/api/v1/memberships/" + ms + "/renew", null, none).andExpect(status().isForbidden());
        post("/api/v1/memberships/" + ms + "/hold", map("reason", "x"), none).andExpect(status().isForbidden());
        get("/api/v1/members/" + member + "/memberships", none).andExpect(status().isOk());

        post("/api/v1/memberships/" + ms + "/renew", null, manager).andExpect(status().isCreated());
        // Cancelling is owner-only even for staff who manage memberships.
        post("/api/v1/memberships/" + ms + "/cancel", map("reason", "x"), manager).andExpect(status().isForbidden());
    }

    @Test
    void inactiveMembersAndRetiredPlansCannotBeAssigned() throws Exception {
        setUp();
        long retired = createPlan("Old", 30, 100);
        put("/api/v1/plans/" + retired, map("name", "Old", "durationDays", 30, "pricePaise", 100, "active", false), owner);
        post("/api/v1/members/" + member + "/memberships", map("planId", retired), owner)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_INACTIVE"));
        post("/api/v1/members/" + member + "/deactivate", map("reason", "x"), owner);
        post("/api/v1/members/" + member + "/memberships", map("planId", plan), owner)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER_INACTIVE"));
    }

    @Test
    void membershipActionsAreAudited() throws Exception {
        setUp();
        long ms = assign(member, plan, "2026-10-01");
        post("/api/v1/memberships/" + ms + "/renew", null, owner);
        get("/api/v1/audit?entityType=MEMBERSHIP", owner)
                .andExpect(jsonPath("$.items[?(@.action=='MEMBERSHIP_ASSIGNED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.action=='MEMBERSHIP_RENEWED')]").isNotEmpty());
    }
}
