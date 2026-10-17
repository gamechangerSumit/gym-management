package app.gym.unit;

import static org.assertj.core.api.Assertions.assertThat;

import app.gym.dashboard.DashboardDtos.HealthFactor;
import app.gym.dashboard.DashboardDtos.HealthScore;
import app.gym.dashboard.HealthScoreService;
import org.junit.jupiter.api.Test;

/** Factor calculations are pure functions of the counted inputs. */
class HealthScoreTest {

    private static HealthScore score(long active, long valid, long engaged, long zeroDues, long ended, long renewed) {
        return HealthScoreService.score(new HealthScoreService.Inputs(active, valid, engaged, zeroDues, ended, renewed), 7);
    }

    private static HealthFactor factor(HealthScore s, String key) {
        return s.factors().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void factorsAreVisibleWithWeightsAndRules() {
        HealthScore s = score(10, 8, 6, 4, 4, 2);
        assertThat(s.factors()).hasSize(4);
        assertThat(s.factors()).allSatisfy(f -> {
            assertThat(f.rule()).isNotBlank();
            assertThat(f.weight()).isPositive();
        });
        assertThat(factor(s, "COVERAGE").value()).isEqualTo(80);
        assertThat(factor(s, "ENGAGEMENT").value()).isEqualTo(75);
        assertThat(factor(s, "RENEWAL").value()).isEqualTo(50);
        assertThat(factor(s, "COLLECTIONS").value()).isEqualTo(50);
        // (80*35 + 75*25 + 50*25 + 50*15) / 100 = 66.75 -> 67
        assertThat(s.score()).isEqualTo(67);
        assertThat(s.disclaimer()).contains("not a financial");
    }

    @Test
    void factorsWithoutDataAreSkippedAndWeightsRenormalised() {
        HealthScore s = score(10, 10, 10, 10, 0, 0); // no ended memberships: renewal factor has no data
        assertThat(factor(s, "RENEWAL").value()).isNull();
        assertThat(s.score()).isEqualTo(100);
    }

    @Test
    void emptyGymHasNoScore() {
        HealthScore s = score(0, 0, 0, 0, 0, 0);
        assertThat(s.score()).isNull();
        assertThat(s.label()).isEqualTo("Not enough data yet");
    }
}
