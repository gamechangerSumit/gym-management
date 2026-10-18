package app.gym.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class SecurityRegressionIT extends app.gym.support.AbstractIT {

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"owner\",\"password\":\"OwnerPass123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedApiCallsGet401Json() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/members").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("code");
    }

    @Test
    void unknownRouteAndBadJsonReturnJsonWithoutStackTraces() throws Exception {
        MvcResult nf = get("/api/v1/members/999999", owner).andReturn();
        assertThat(nf.getResponse().getStatus()).isEqualTo(404);
        assertThat(nf.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("at app.gym");
        MvcResult bad = post("/api/v1/plans", "{not json", owner).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(bad.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("com.fasterxml");
    }

    @Test
    void unknownJsonPropertiesAreRejected() throws Exception {
        post("/api/v1/plans", map("name", "X", "durationDays", 30, "pricePaise", 100, "isAdmin", true), owner)
                .andExpect(status().isBadRequest());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/auth/session")).andReturn();
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void flywayAppliedAllMigrations() {
        Integer n = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success").query(Integer.class).single();
        assertThat(n).isEqualTo(12);
    }

    @Test
    void auditLogIsAppendOnly() throws Exception {
        createMember("Audit Person", "9600000001");
        Integer rows = jdbc.sql("SELECT count(*) FROM audit_log").query(Integer.class).single();
        assertThat(rows).isGreaterThan(0);
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbc.sql("UPDATE audit_log SET action = 'x'").update());
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> jdbc.sql("DELETE FROM audit_log").update());
    }

    @Test
    void onlyOneActiveQrPassPerMemberAtDatabaseLevel() throws Exception {
        long m = createMember("Qr Person", "9600000002");
        jdbc.sql("INSERT INTO member_qr_pass (member_id, token_hash) VALUES (:m, :h)")
                .param("m", m).param("h", "a".repeat(64)).update();
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () ->
                jdbc.sql("INSERT INTO member_qr_pass (member_id, token_hash) VALUES (:m, :h)")
                        .param("m", m).param("h", "b".repeat(64)).update());
    }

    @Test
    void overlappingMembershipsAreRejectedByTheDatabaseToo() throws Exception {
        long m = createMember("Overlap Person", "9600000003");
        long p = createPlan("Monthly", 30, 100000);
        assign(m, p, "2026-10-01");
        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class, () ->
                jdbc.sql("INSERT INTO membership (member_id, plan_id, start_date, end_date, price_charged_paise) "
                        + "VALUES (:m, :p, DATE '2026-10-10', DATE '2026-11-01', 1)").param("m", m).param("p", p).update());
    }
}
