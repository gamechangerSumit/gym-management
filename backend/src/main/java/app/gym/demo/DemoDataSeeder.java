package app.gym.demo;

import app.gym.auth.Role;
import app.gym.auth.StaffPermissions;
import app.gym.auth.UserAccountRepository;
import app.gym.common.Db;
import app.gym.member.MemberDtos.CreateMemberRequest;
import app.gym.member.MemberDtos.MemberDetail;
import app.gym.member.MemberService;
import app.gym.membership.MembershipDtos.AssignRequest;
import app.gym.membership.MembershipService;
import app.gym.payment.PaymentDtos.PaymentRequest;
import app.gym.payment.PaymentService;
import app.gym.plan.PlanDtos.PlanDto;
import app.gym.plan.PlanDtos.PlanRequest;
import app.gym.plan.PlanService;
import app.gym.settings.GymClock;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DEMO DATA ONLY. Active only with the "demo" Spring profile and refuses to run together with "prod".
 * Everything it creates is flagged is_demo, which makes the UI show a visible DEMO banner.
 * Demo logins: staff / DemoStaff1 and demo.member1 / DemoMember1, demo.member2 / DemoMember2.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final Environment env;
    private final JdbcClient jdbc;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final PlanService plans;
    private final MemberService members;
    private final MembershipService memberships;
    private final PaymentService payments;
    private final GymClock clock;

    public DemoDataSeeder(Environment env, JdbcClient jdbc, UserAccountRepository users, PasswordEncoder encoder,
                          PlanService plans, MemberService members, MembershipService memberships,
                          PaymentService payments, GymClock clock) {
        this.env = env;
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.plans = plans;
        this.members = members;
        this.memberships = memberships;
        this.payments = payments;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("The demo profile must never be combined with the prod profile.");
        }
        if (users.anyDemoAccount()) {
            return;
        }
        LocalDate today = clock.today();

        long staffId = users.insert("staff", encoder.encode("DemoStaff1"), Role.STAFF, "ACTIVE", false, true);
        users.savePermissions(staffId, new StaffPermissions(true, true, true, false));

        PlanDto monthly = plans.create(new PlanRequest("Monthly (demo)", 30, 150000, "Demo plan", true));
        PlanDto quarterly = plans.create(new PlanRequest("Quarterly (demo)", 90, 400000, "Demo plan", true));

        MemberDetail a = members.create(new CreateMemberRequest("Demo Member One", "9000000001", "one@example.invalid",
                today.minusDays(40), "Demo contact", "Demo notes: staff only", true));
        MemberDetail b = members.create(new CreateMemberRequest("Demo Member Two", "9000000002", null,
                today.minusDays(70), null, null, true));
        MemberDetail c = members.create(new CreateMemberRequest("Demo Member Three", "9000000003", null,
                today.minusDays(100), null, null, true));

        var m1 = memberships.assign(a.id(), new AssignRequest(monthly.id(), today.minusDays(10), null));
        memberships.assign(b.id(), new AssignRequest(quarterly.id(), today.minusDays(30), null));
        memberships.assign(c.id(), new AssignRequest(monthly.id(), today.minusDays(45), null));

        payments.record(new PaymentRequest(a.id(), m1.id(), 150000, "UPI", Instant.now(), "Demo payment", false));
        payments.record(new PaymentRequest(b.id(), null, 200000, "CASH", Instant.now(), "Demo partial payment", false));

        for (int d : new int[]{1, 2, 4, 6, 9}) {
            jdbc.sql("INSERT INTO attendance_record (member_id, checked_in_at, checkin_date, source) "
                            + "VALUES (:m, :at, :d, 'MANUAL')")
                    .param("m", a.id())
                    .param("at", Db.ts(clock.now().minusSeconds(86400L * d)))
                    .param("d", today.minusDays(d)).update();
        }

        link(a.id(), "demo.member1", "DemoMember1");
        link(b.id(), "demo.member2", "DemoMember2");
        jdbc.sql("UPDATE member_profile SET is_demo = TRUE").update();
        log.warn("DEMO DATA loaded. Never use the demo profile in production.");
    }

    private void link(long memberId, String login, String password) {
        long userId = users.insert(login, encoder.encode(password), Role.MEMBER, "ACTIVE", false, true);
        jdbc.sql("UPDATE member_profile SET user_id = :u WHERE id = :m").param("u", userId).param("m", memberId).update();
    }
}
