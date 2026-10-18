package app.gym.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import app.gym.auth.AppUser;
import app.gym.auth.RateLimiter;
import app.gym.auth.Role;
import app.gym.auth.StaffPermissions;
import app.gym.auth.UserAccountRepository;
import app.gym.settings.GymSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: real PostgreSQL via Testcontainers (Docker required), full Spring context, MockMvc,
 * a controllable clock, and helpers to create users and call the API as a given identity.
 * One container and one Spring context are shared by all subclasses; each test starts from a clean database.
 */
@SpringBootTest(properties = {
        "app.security.bcrypt-strength=4",
        "app.security.max-failed-attempts=3",
        "app.cookie.secure=false",
        "app.bootstrap.owner-login=owner",
        "app.bootstrap.owner-password=OwnerPass123",
        "app.bootstrap.owner-must-change-password=false",
        "app.rate-limit.ip-capacity=100000",
        "app.rate-limit.account-capacity=100000",
        "spring.main.banner-mode=off"
})
@AutoConfigureMockMvc
@Import(AbstractIT.TestClockConfig.class)
public abstract class AbstractIT {

    /** 2026-10-15 09:30 in Asia/Kolkata (a Thursday). */
    public static final Instant START = Instant.parse("2026-10-15T04:00:00Z");
    public static final MediaType JSON = MediaType.APPLICATION_JSON;

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gym").withUsername("gym").withPassword("gym");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    /** An authenticated identity for MockMvc calls. */
    public record Who(long id, String login, Role role) {}

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcClient jdbc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected UserAccountRepository users;
    @Autowired protected RateLimiter rateLimiter;
    @Autowired protected MutableClock clock;
    @Autowired protected GymSettingsService gymSettings;

    protected Who owner;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("TRUNCATE attendance_record, payment, renewal_followup, membership, member_qr_pass, member_profile, "
                + "membership_plan, one_time_token, staff_permission, user_account, spring_session, audit_log "
                + "RESTART IDENTITY CASCADE").update();
        jdbc.sql("ALTER SEQUENCE member_code_seq RESTART").update();
        jdbc.sql("ALTER SEQUENCE receipt_seq RESTART").update();
        jdbc.sql("UPDATE gym_settings SET timezone = 'Asia/Kolkata', duplicate_window_minutes = 10, drop_off_days = 7, "
                + "expiring_soon_days = 7, no_show_threshold_days = 10, weekly_visit_goal = 3, member_can_edit_email = FALSE").update();
        jdbc.sql("UPDATE gym SET name = 'Test Gym'").update();
        clock.set(START);
        rateLimiter.reset();
        long id = users.insert("owner", encoder.encode("OwnerPass123"), Role.OWNER, "ACTIVE", false, false);
        owner = new Who(id, "owner", Role.OWNER);
    }

    // ------------------------------------------------------------ identities

    protected Who createStaff(String login, StaffPermissions perms) {
        long id = users.insert(login, encoder.encode("StaffPass123"), Role.STAFF, "ACTIVE", false, false);
        users.savePermissions(id, perms);
        return new Who(id, login, Role.STAFF);
    }

    /** Creates an ACTIVE member login linked to an existing member profile. */
    protected Who createMemberUser(String login, long memberId) {
        long id = users.insert(login, encoder.encode("MemberPass123"), Role.MEMBER, "ACTIVE", false, false);
        jdbc.sql("UPDATE member_profile SET user_id = :u WHERE id = :m").param("u", id).param("m", memberId).update();
        return new Who(id, login, Role.MEMBER);
    }

    // --------------------------------------------------------------- calls

    protected ResultActions call(HttpMethod method, String url, Object body, Who who) throws Exception {
        MockHttpServletRequestBuilder b = request(method, url).accept(JSON).with(csrf());
        if (body != null) {
            b.contentType(JSON).content(body instanceof String s ? s : mapper.writeValueAsString(body));
        }
        if (who != null) {
            b.with(authentication(new UsernamePasswordAuthenticationToken(
                    new AppUser(who.id(), who.login(), who.role()), null,
                    new AppUser(who.id(), who.login(), who.role()).getAuthorities())));
        }
        return mvc.perform(b);
    }

    protected MockHttpServletRequestBuilder get(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
    }

    protected MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    protected ResultActions get(String url, Who who) throws Exception {
        return call(HttpMethod.GET, url, null, who);
    }

    protected ResultActions post(String url, Object body, Who who) throws Exception {
        return call(HttpMethod.POST, url, body, who);
    }

    protected ResultActions put(String url, Object body, Who who) throws Exception {
        return call(HttpMethod.PUT, url, body, who);
    }

    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    protected static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    protected static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    // ---------------------------------------------------------- data helpers

    protected long createMember(String name, String phone) throws Exception {
        return id(post("/api/v1/members", map("fullName", name, "phone", phone, "confirmSimilar", true), owner));
    }

    protected long createPlan(String name, int days, long pricePaise) throws Exception {
        return id(post("/api/v1/plans", map("name", name, "durationDays", days, "pricePaise", pricePaise), owner));
    }

    protected long assign(long memberId, long planId, String startDate) throws Exception {
        Map<String, Object> body = map("planId", planId);
        if (startDate != null) {
            body.put("startDate", startDate);
        }
        return id(post("/api/v1/members/" + memberId + "/memberships", body, owner));
    }
}
