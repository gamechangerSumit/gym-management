package app.gym.dashboard;

import app.gym.auth.Perm;
import app.gym.common.Db;
import app.gym.common.Money;
import app.gym.common.Sql;
import app.gym.config.AppProperties;
import app.gym.dashboard.DashboardDtos.ActionItem;
import app.gym.dashboard.DashboardDtos.ActionList;
import app.gym.dashboard.DashboardDtos.Collections;
import app.gym.dashboard.DashboardDtos.Summary;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettings;
import app.gym.settings.GymSettingsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    public static final String COLLECTIONS_RULE =
            "Sum of RECORDED payments with a payment date in the selected range. Voided payments are excluded.";

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;
    private final Perm perm;
    private final AppProperties props;

    public DashboardService(JdbcClient jdbc, GymClock clock, GymSettingsService settings, Perm perm,
                            AppProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.perm = perm;
        this.props = props;
    }

    /** Collections for an explicit, clearly stated date range (inclusive local dates in the gym timezone). */
    public Collections collections(LocalDate from, LocalDate to) {
        return jdbc.sql(Sql.load("collections_total"))
                .param("from", Db.ts(clock.startOfDay(from)))
                .param("to", Db.ts(clock.startOfDay(to.plusDays(1))))
                .query((rs, i) -> new Collections(from, to, rs.getLong("total_paise"), rs.getLong("payment_count"),
                        COLLECTIONS_RULE))
                .single();
    }

    public Summary summary(LocalDate from, LocalDate to) {
        GymSettings gs = settings.get();
        LocalDate today = clock.today();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today;
        Collections collections = perm.owner() || perm.viewReports() ? collections(f, t) : null;
        return jdbc.sql(Sql.load("dashboard_kpis"))
                .param("today", today)
                .param("soon", gs.expiringSoonDays())
                .query((rs, i) -> new Summary(rs.getLong("active_members"), rs.getLong("expiring_soon"),
                        rs.getLong("expired"), rs.getLong("todays_checkins"), collections, gs.expiringSoonDays(), today,
                        rs.getLong("active_members_7d"), rs.getLong("open_no_show_cases"),
                        rs.getLong("members_returned_7d"), rs.getLong("renewals_due_7d"),
                        rs.getLong("renewal_collected_month_paise")))
                .single();
    }

    /** Daily action list. Overdue dues are finance data and only shown to the owner or staff with finance access. */
    public ActionList actionList() {
        GymSettings gs = settings.get();
        LocalDate today = clock.today();
        boolean finance = perm.owner() || perm.recordPayments();
        int lookback = props.dashboard().expiredLookbackDays();
        List<ActionItem> items = new ArrayList<>();
        List<String> rules = new ArrayList<>();

        String expiringRule = "Membership is valid today and ends within " + gs.expiringSoonDays()
                + " day(s) (0 = last valid day).";
        rules.add("EXPIRING: " + expiringRule);
        items.addAll(jdbc.sql(Sql.load("action_expiring")).param("today", today).param("soon", gs.expiringSoonDays())
                .query((rs, i) -> new ActionItem("EXPIRING", rs.getLong("member_id"), rs.getString("member_code"),
                        rs.getString("full_name"), rs.getString("phone"),
                        "Ends " + Db.date(rs, "ref_date") + (rs.getInt("days") == 0 ? " (today)" : " (in " + rs.getInt("days") + " days)"),
                        expiringRule)).list());

        String expiredRule = "All memberships ended in the last " + lookback + " days and none is current or upcoming.";
        rules.add("EXPIRED: " + expiredRule);
        items.addAll(jdbc.sql(Sql.load("action_expired")).param("today", today).param("lookback", lookback)
                .query((rs, i) -> new ActionItem("EXPIRED", rs.getLong("member_id"), rs.getString("member_code"),
                        rs.getString("full_name"), rs.getString("phone"),
                        "Expired " + Db.date(rs, "ref_date") + " (" + rs.getInt("days") + " days ago)", expiredRule)).list());

        if (finance) {
            String dueRule = "Dues are greater than zero: price charged for non-cancelled memberships minus recorded payments.";
            rules.add("OVERDUE_DUES: " + dueRule);
            items.addAll(jdbc.sql(Sql.load("action_overdue"))
                    .query((rs, i) -> new ActionItem("OVERDUE_DUES", rs.getLong("member_id"), rs.getString("member_code"),
                            rs.getString("full_name"), rs.getString("phone"),
                            "Outstanding " + Money.formatInr(rs.getLong("dues_paise")), dueRule)).list());
        }

        String visitRule = "Valid membership and no visit for " + gs.dropOffDays()
                + " day(s) or more (a visit exactly " + gs.dropOffDays() + " days ago counts as no recent visit).";
        rules.add("NO_RECENT_VISIT: " + visitRule);
        items.addAll(jdbc.sql(Sql.load("action_no_visit")).param("today", today).param("n", gs.dropOffDays())
                .query((rs, i) -> new ActionItem("NO_RECENT_VISIT", rs.getLong("member_id"), rs.getString("member_code"),
                        rs.getString("full_name"), rs.getString("phone"),
                        "Last visit/start " + Db.date(rs, "ref_date") + " (" + rs.getInt("days") + " days ago)", visitRule)).list());

        return new ActionList(today, items, rules);
    }
}
