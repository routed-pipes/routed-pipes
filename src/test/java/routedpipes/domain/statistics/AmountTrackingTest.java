package routedpipes.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import routedpipes.domain.item.ItemType;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class AmountTrackingTest {

    private static final ItemType STONE = new TestItem("stone");
    private static final ItemType IRON = new TestItem("iron");

    private final AmountTracking tracking = new AmountTracking();

    @Test
    void anItemStartsWithTheAmountThereIsWhenItIsAdded() {
        assertThat(tracking.track(STONE, 64)).isTrue();

        assertThat(tracking.items()).containsExactly(STONE);
        assertThat(tracking.history(STONE).orElseThrow().oldestFirst()).containsExactly(64);
    }

    @Test
    void anItemIsTrackedOnlyOnce() {
        tracking.track(STONE, 64);

        assertThat(tracking.track(STONE, 3)).isFalse();
        assertThat(tracking.history(STONE).orElseThrow().oldestFirst()).containsExactly(64);
    }

    @Test
    void itemsAreListedInTheOrderTheyWereAdded() {
        tracking.track(STONE, 1);
        tracking.track(IRON, 1);

        assertThat(tracking.items()).containsExactly(STONE, IRON);
    }

    @Test
    void anItemThatIsNoLongerTrackedLosesItsHistory() {
        tracking.track(STONE, 1);
        tracking.untrack(STONE);

        assertThat(tracking.items()).isEmpty();
        assertThat(tracking.history(STONE)).isEmpty();
    }

    @Test
    void aSampleIsDueOnceAMinute() {
        int due = 0;
        for (int tick = 0; tick < 3 * AmountTracking.INTERVAL; tick++) {
            if (tracking.due()) {
                due++;
                assertThat((tick + 1) % AmountTracking.INTERVAL).isZero();
            }
        }

        assertThat(due).isEqualTo(3);
    }

    @Test
    void recordingAsksForEveryTrackedItem() {
        tracking.track(STONE, 64);
        tracking.track(IRON, 5);

        tracking.record(type -> Map.of(STONE, 60L).getOrDefault(type, 0L));

        assertThat(tracking.history(STONE).orElseThrow().oldestFirst()).containsExactly(64, 60);
        assertThat(tracking.history(IRON).orElseThrow().oldestFirst()).containsExactly(5, 0);
    }

    @Test
    void aRestoredHistoryIsTrackedOn() {
        tracking.restore(STONE, AmountHistory.of(new long[] {1, 2}));

        tracking.record(type -> 3);

        assertThat(tracking.history(STONE).orElseThrow().oldestFirst()).containsExactly(1, 2, 3);
    }
}
