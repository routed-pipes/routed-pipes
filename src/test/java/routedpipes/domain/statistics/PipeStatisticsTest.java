package routedpipes.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import routedpipes.domain.statistics.PipeStatistics.Kind;
import org.junit.jupiter.api.Test;

class PipeStatisticsTest {

    private final PipeStatistics statistics = new PipeStatistics();

    @Test
    void startsAtZero() {
        for (Kind kind : Kind.values()) {
            assertThat(statistics.session(kind)).isZero();
            assertThat(statistics.lifetime(kind)).isZero();
        }
    }

    @Test
    void countsEveryKindOnItsOwnInSessionAndLifetime() {
        statistics.count(Kind.SENT, 3);
        statistics.count(Kind.SENT, 4);
        statistics.count(Kind.RELAYED, 64);

        assertThat(statistics.session(Kind.SENT)).isEqualTo(7);
        assertThat(statistics.lifetime(Kind.SENT)).isEqualTo(7);
        assertThat(statistics.session(Kind.RELAYED)).isEqualTo(64);
        assertThat(statistics.session(Kind.RECEIVED)).isZero();
    }

    @Test
    void restoredLifetimeGoesOnCountingWhileTheSessionStartsOver() {
        statistics.restoreLifetime(Kind.RECEIVED, 5_000_000_000L);
        statistics.count(Kind.RECEIVED, 2);

        assertThat(statistics.lifetime(Kind.RECEIVED)).isEqualTo(5_000_000_002L);
        assertThat(statistics.session(Kind.RECEIVED)).isEqualTo(2);
    }

    @Test
    void aBrokenSaveNeverGivesANegativeLifetime() {
        statistics.restoreLifetime(Kind.SENT, -5);

        assertThat(statistics.lifetime(Kind.SENT)).isZero();
    }

    @Test
    void refusesNegativeAmounts() {
        assertThatThrownBy(() -> statistics.count(Kind.SENT, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
