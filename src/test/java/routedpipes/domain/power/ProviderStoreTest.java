package routedpipes.domain.power;

import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import routedpipes.domain.routing.RouterId;
import org.junit.jupiter.api.Test;

class ProviderStoreTest {

    private final ProviderStore store = new ProviderStore();
    private final Map<RouterId, Integer> delivered = new LinkedHashMap<>();

    private boolean tick() {
        return store.tick((destination, amount) -> {
            delivered.merge(destination, amount, Integer::sum);
            return true;
        });
    }

    @Test
    void energyComesInThroughTheInputBuffer() {
        assertThat(store.receive(4000, false)).isEqualTo(4000);
        assertThat(store.stored()).isZero();
        assertThat(store.input()).isEqualTo(4000);

        assertThat(tick()).isTrue();

        assertThat(store.stored()).isEqualTo(4000);
        assertThat(store.input()).isZero();
    }

    @Test
    void theInputBufferLimitsWhatComesInPerTick() {
        assertThat(store.receive(25_000, false)).isEqualTo(ProviderStore.INPUT_BUFFER);
        assertThat(store.receive(1, false)).isZero();

        tick();

        assertThat(store.receive(25_000, false)).isEqualTo(ProviderStore.INPUT_BUFFER);
    }

    @Test
    void simulatingTakesNothingIn() {
        assertThat(store.receive(500, true)).isEqualTo(500);
        assertThat(store.input()).isZero();
    }

    @Test
    void aFullStoreLeavesTheRestInTheInputBuffer() {
        store.load(ProviderStore.CAPACITY - 100, 0);
        store.receive(1000, false);

        tick();

        assertThat(store.stored()).isEqualTo(ProviderStore.CAPACITY);
        assertThat(store.input()).isEqualTo(900);
        assertThat(store.receive(10_000, false)).isEqualTo(9100);
    }

    @Test
    void ordersAreServedInFullWhileEnoughMayGoOut() {
        store.load(50_000, 0);
        store.order(router(1), 3000);
        store.order(router(2), 6000);

        assertThat(tick()).isTrue();

        assertThat(delivered).containsEntry(router(1), 3000).containsEntry(router(2), 6000);
        assertThat(store.stored()).isEqualTo(41_000);
    }

    @Test
    void everyoneGetsTheSameShareWhenMoreIsOrderedThanMayGoOutInATick() {
        store.load(1_000_000, 0);
        store.order(router(1), 10_000);
        store.order(router(2), 30_000);

        tick();

        assertThat(delivered).containsEntry(router(1), 2500).containsEntry(router(2), 7500);
        assertThat(store.stored()).isEqualTo(990_000);
    }

    @Test
    void anAlmostEmptyStoreSharesWhatItHas() {
        store.load(1000, 0);
        store.order(router(1), 3000);
        store.order(router(2), 1000);

        tick();

        assertThat(delivered).containsEntry(router(1), 750).containsEntry(router(2), 250);
        assertThat(store.stored()).isZero();
    }

    @Test
    void whatCannotBeDeliveredStaysInTheStore() {
        store.load(5000, 0);
        store.order(router(1), 2000);
        store.order(router(2), 1000);

        store.tick((destination, amount) -> destination.equals(router(2)));

        assertThat(store.stored()).isEqualTo(4000);
    }

    @Test
    void ordersLastOneTick() {
        store.load(5000, 0);
        store.order(router(1), 2000);
        tick();

        assertThat(tick()).isFalse();

        assertThat(delivered).containsEntry(router(1), 2000);
        assertThat(store.stored()).isEqualTo(3000);
    }

    @Test
    void orderingAgainInTheSameTickReplacesTheOrder() {
        store.load(5000, 0);
        store.order(router(1), 2000);
        store.order(router(1), 500);

        tick();

        assertThat(delivered).containsEntry(router(1), 500);
    }

    @Test
    void nothingChangesWithoutInputOrOrders() {
        store.load(5000, 0);

        assertThat(tick()).isFalse();
    }

    @Test
    void loadingKeepsWithinTheLimits() {
        store.load(ProviderStore.CAPACITY + 5, -3);

        assertThat(store.stored()).isEqualTo(ProviderStore.CAPACITY);
        assertThat(store.input()).isZero();
    }
}
