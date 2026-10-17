package app.gym.dashboard;

import app.gym.common.Db;
import app.gym.common.Money;
import app.gym.common.Sql;
import app.gym.dashboard.DashboardDtos.HealthFactor;
import app.gym.dashboard.DashboardDtos.HealthScore;
import app.gym.dashboard.DashboardDtos.Leakage;
import app.gym.dashboard.DashboardDtos.LeakageIndicator;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettings;
import app.gym.settings.GymSettingsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Business Health Score: a simple, transparent operational indicator (not a financial statement). Four visible
 * factors with fixed weights; factors with no data are left out and the remaining weights are re-normalised.
 */
@Service
public class HealthScoreService {

    public static final String DISCLAIMER =
            "A simple operational indicator built from your recorded data using the visible factors below. "
                    + "It is not a financial or forecasting measure.";

    public record Inputs(long activeProfiles, long validMembers, long engagedMembers, long validZeroDues,
                         long endedRecent, long endedRecentRenewed) {}

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;

    public HealthScoreService(JdbcClient jdbc, GymClock clock, GymSettingsService settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
    }

    public HealthScore compute() {
        GymSettings gs = settings.get();
        Inputs in = jdbc.sql(Sql.load("health_inputs"))
                .param("today", clock.today())
                .param("dropoff", gs.dropOffDays())
                .query((rs, i) -> new Inputs(rs.getLong("active_profiles"), rs.getLong("valid_members"),
                        rs.getLong("engaged_members"), rs.getLong("valid_zero_dues"), rs.getLong("ended_recent"),
                        rs.getLong("ended_recent_renewed")))
                .single();
        return score(in, gs.dropOffDays());
    }

    /** Pure calculation, unit-tested. */
    public static HealthScore score(Inputs in, int dropOffDays) {
        List<HealthFactor> factors = new ArrayList<>();
        factors.add(new HealthFactor("COVERAGE", "Membership coverage", 35,
                pct(in.validMembers(), in.activeProfiles()),
                in.validMembers() + " of " + in.activeProfiles() + " active members have a valid membership today",
                "Members with a valid membership today / all active members"));
        factors.add(new HealthFactor("ENGAGEMENT", "Recent attendance", 25,
                pct(in.engagedMembers(), in.validMembers()),
                in.engagedMembers() + " of " + in.validMembers() + " valid members visited in the last " + dropOffDays + " days",
                "Valid members with a visit in the last " + dropOffDays + " days / valid members"));
        factors.add(new HealthFactor("RENEWAL", "Renewals (last 30 days)", 25,
                pct(in.endedRecentRenewed(), in.endedRecent()),
                in.endedRecentRenewed() + " of " + in.endedRecent() + " memberships that ended in the last 30 days were followed by a new one",
                "Memberships ended in the last 30 days that have a later membership / memberships ended in the last 30 days"));
        factors.add(new HealthFactor("COLLECTIONS", "Dues up to date", 15,
                pct(in.validZeroDues(), in.validMembers()),
                in.validZeroDues() + " of " + in.validMembers() + " valid members have no outstanding dues",
                "Valid members with dues <= 0 / valid members"));

        double weighted = 0;
        int weights = 0;
        for (HealthFactor f : factors) {
            if (f.value() != null) {
                weighted += f.value() * f.weight();
                weights += f.weight();
            }
        }
        Integer score = weights == 0 ? null : (int) Math.round(weighted / weights);
        String label = score == null ? "Not enough data yet"
                : score >= 80 ? "Healthy" : score >= 60 ? "Needs attention" : "At risk";
        return new HealthScore(score, label, DISCLAIMER, factors);
    }

    private static Integer pct(long part, long whole) {
        return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
    }

    /** Revenue leakage indicators; each states its rule and uses payment/membership records only. */
    public Leakage leakage() {
        LocalDate today = clock.today();
        LocalDate since = today.minusDays(30);
        return jdbc.sql(Sql.load("leakage"))
                .param("today", today)
                .param("since", Db.ts(clock.startOfDay(since)))
                .query((rs, i) -> {
                    List<LeakageIndicator> list = new ArrayList<>();
                    list.add(new LeakageIndicator("UNPAID_ACTIVE", "Active members with unpaid dues",
                            rs.getLong("unpaid_count"), rs.getLong("unpaid_paise"),
                            "Valid membership today and dues > 0 (price charged minus recorded payments)."));
                    list.add(new LeakageIndicator("EXPIRED_NOT_CONTACTED", "Recently expired, not contacted",
                            rs.getLong("uncontacted_count"), null,
                            "Memberships that ended in the last 30 days, not renewed, with no contact recorded in the Renewal Recovery Center."));
                    list.add(new LeakageIndicator("VOIDED_PAYMENTS", "Payments voided (last 30 days)",
                            rs.getLong("voided_count"), rs.getLong("voided_paise"),
                            "Payments voided in the last 30 days. Review the reasons in the audit log."));
                    list.add(new LeakageIndicator("DISCOUNTED_MEMBERSHIPS", "Memberships below plan price (last 30 days)",
                            rs.getLong("discounted_count"), rs.getLong("discounted_paise"),
                            "Memberships created in the last 30 days and charged less than the plan's current price; amount is the total difference."));
                    return new Leakage(today, list);
                })
                .single();
    }

    static String describeMoney(long paise) {
        return Money.formatInr(paise);
    }
}
