package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gym.auth.StaffPermissions;
import org.junit.jupiter.api.Test;

class MemberPlanIT extends app.gym.support.AbstractIT {

    private static StaffPermissions editOnly() {
        return new StaffPermissions(false, false, true, false);
    }

    @Test
    void ownerCreatesMemberWithGeneratedCodeNormalisedPhoneAndAPass() throws Exception {
        post("/api/v1/members", map("fullName", "  Asha   Rao ", "phone", "98765 43210", "email", "asha@example.com",
                "staffNotes", "prefers mornings"), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCode").value("M00001"))
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.phone").value("+919876543210"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.staffNotes").value("prefers mornings"));
        long passes = jdbc.sql("SELECT count(*) FROM member_qr_pass WHERE status = 'ACTIVE'").query(Long.class).single();
        assertThat(passes).isEqualTo(1);
    }

    @Test
    void duplicatePhoneIsRejectedWhateverTheFormatting() throws Exception {
        createMember("First Person", "9876543210");
        for (String variant : new String[]{"9876543210", "98765 43210", "+91 98765-43210", "09876543210"}) {
            post("/api/v1/members", map("fullName", "Someone Else Entirely", "phone", variant), owner)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
        }
        long count = jdbc.sql("SELECT count(*) FROM member_profile").query(Long.class).single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invalidInputGivesFieldErrors() throws Exception {
        post("/api/v1/members", map("fullName", "A", "phone", "12345", "email", "not-an-email"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        post("/api/v1/members", map("fullName", "Valid Name", "phone", "12345"), owner)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PHONE"));
    }

    @Test
    void verySimilarNameAndPhoneReturnsAWarningUntilConfirmed() throws Exception {
        createMember("Rahul Sharma", "9876543210");
        post("/api/v1/members", map("fullName", "Rahul Sharma", "phone", "9876543211"), owner)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SIMILAR_MEMBER"));
        post("/api/v1/members", map("fullName", "Rahul Sharma", "phone", "9876543211", "confirmSimilar", true), owner)
                .andExpect(status().isCreated());
        // Same name but a clearly different phone is not similar.
        post("/api/v1/members", map("fullName", "Rahul Sharma", "phone", "9123456780"), owner).andExpect(status().isCreated());
    }

    @Test
    void staffNeedsCanEditMembersToCreateEditOrDeactivate() throws Exception {
        long id = createMember("Existing Person", "9000000001");
        Who noPerm = createStaff("desk-none", StaffPermissions.none());
        Who canEdit = createStaff("desk-edit", editOnly());

        post("/api/v1/members", map("fullName", "New Person", "phone", "9000000002"), noPerm).andExpect(status().isForbidden());
        put("/api/v1/members/" + id, map("fullName", "Existing Person", "phone", "9000000001"), noPerm).andExpect(status().isForbidden());
        post("/api/v1/members/" + id + "/deactivate", map("reason", "x"), noPerm).andExpect(status().isForbidden());

        post("/api/v1/members", map("fullName", "New Person", "phone", "9000000002"), canEdit).andExpect(status().isCreated());
        put("/api/v1/members/" + id, map("fullName", "Existing Renamed", "phone", "9000000001", "confirmSimilar", true), canEdit)
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Existing Renamed"));
        // Anyone on staff may view the list and the detail (including staff-only notes).
        get("/api/v1/members", noPerm).andExpect(status().isOk());
        get("/api/v1/members/" + id, noPerm).andExpect(status().isOk());
    }

    @Test
    void searchByNamePhoneAndCodeWithPagination() throws Exception {
        for (int i = 1; i <= 25; i++) {
            createMember(String.format("Search Person %02d", i), String.format("90000000%02d", i));
        }
        createMember("Unrelated Human", "9111111111");

        get("/api/v1/members?q=Search&size=10&page=0", owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items.length()").value(10));
        get("/api/v1/members?q=Search&size=10&page=2", owner)
                .andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.page").value(2));
        get("/api/v1/members?q=9000000007", owner)
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].fullName").value("Search Person 07"));
        get("/api/v1/members?q=M00003", owner)
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].memberCode").value("M00003"));
        get("/api/v1/members?q=person%20%2F", owner).andExpect(jsonPath("$.totalItems").value(0));
        // LIKE wildcards in the search text are matched literally.
        get("/api/v1/members?q=%25", owner).andExpect(jsonPath("$.totalItems").value(0));
        get("/api/v1/members?size=500", owner).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void deactivateAndReactivateKeepTheRecordAndFilterByStatus() throws Exception {
        long id = createMember("Leaving Member", "9000000009");
        createMember("Staying Member", "9000000008");
        post("/api/v1/members/" + id + "/deactivate", map("reason", "moved city"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        get("/api/v1/members?status=INACTIVE", owner).andExpect(jsonPath("$.totalItems").value(1));
        get("/api/v1/members?status=ACTIVE", owner).andExpect(jsonPath("$.totalItems").value(1));
        get("/api/v1/members?status=BOGUS", owner).andExpect(status().isBadRequest());
        post("/api/v1/members/" + id + "/reactivate", null, owner).andExpect(jsonPath("$.status").value("ACTIVE"));
        long rows = jdbc.sql("SELECT count(*) FROM member_profile").query(Long.class).single();
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void memberChangesAreAudited() throws Exception {
        long id = createMember("Audited Person", "9000000010");
        put("/api/v1/members/" + id, map("fullName", "Audited Person", "phone", "9000000010", "email", "a@b.co"), owner)
                .andExpect(status().isOk());
        post("/api/v1/members/" + id + "/deactivate", map("reason", "test"), owner).andExpect(status().isOk());
        get("/api/v1/audit?entityType=MEMBER", owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.action=='MEMBER_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.action=='MEMBER_UPDATED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.action=='MEMBER_DEACTIVATED')]").isNotEmpty());
        String all = get("/api/v1/audit?size=100", owner).andReturn().getResponse().getContentAsString();
        assertThat(all).doesNotContain("OwnerPass123").doesNotContain("password");
    }

    @Test
    void planCrudIsOwnerOnly() throws Exception {
        Who staff = createStaff("desk", StaffPermissions.all());
        post("/api/v1/plans", map("name", "Monthly", "durationDays", 30, "pricePaise", 150000), staff).andExpect(status().isForbidden());

        long id = createPlan("Monthly", 30, 150000);
        put("/api/v1/plans/" + id, map("name", "Monthly", "durationDays", 30, "pricePaise", 160000), staff).andExpect(status().isForbidden());
        put("/api/v1/plans/" + id, map("name", "Monthly", "durationDays", 30, "pricePaise", 160000, "description", "Gym access"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.pricePaise").value(160000));
        post("/api/v1/plans", map("name", "monthly", "durationDays", 30, "pricePaise", 1), owner)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_PLAN"));
        post("/api/v1/plans", map("name", "Bad", "durationDays", 0, "pricePaise", -5), owner)
                .andExpect(status().isBadRequest());

        // Retired plans disappear for staff but stay visible to the owner.
        put("/api/v1/plans/" + id, map("name", "Monthly", "durationDays", 30, "pricePaise", 160000, "active", false), owner)
                .andExpect(status().isOk());
        get("/api/v1/plans", staff).andExpect(jsonPath("$.length()").value(0));
        get("/api/v1/plans?includeInactive=true", staff).andExpect(jsonPath("$.length()").value(0));
        get("/api/v1/plans?includeInactive=true", owner).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void settingsAreOwnerOnlyAndValidated() throws Exception {
        put("/api/v1/settings", map("gymName", "Iron Works", "timezone", "Asia/Kolkata", "duplicateWindowMinutes", 5,
                "dropOffDays", 10, "expiringSoonDays", 5, "memberCanEditEmail", true, "weeklyVisitGoal", 3, "noShowThresholdDays", 10), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.gymName").value("Iron Works"));
        put("/api/v1/settings", map("gymName", "X", "timezone", "Mars/Olympus", "duplicateWindowMinutes", 5,
                "dropOffDays", 10, "expiringSoonDays", 5, "memberCanEditEmail", true), owner)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
        get("/api/v1/audit?entityType=GYM_SETTINGS", owner).andExpect(jsonPath("$.items[0].action").value("SETTINGS_UPDATED"));
    }
}
