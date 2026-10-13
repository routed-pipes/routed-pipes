package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import routedpipes.application.port.PowerSource;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class LogisticsNetworkPowerTest {

    private static final RouterId LEFT = router(1);
    private static final RouterId RIGHT = router(2);
    private static final long BUILT = -100;

    // one junction between two pipes
    private static final class Junction implements PowerSource {
        private int stored;

        private Junction(int stored) {
            this.stored = stored;
        }

        @Override
        public Object key() {
            return this;
        }

        @Override
        public int draw(int amount, boolean simulate) {
            int drawn = Math.min(amount, stored);
            if (!simulate) {
                stored -= drawn;
            }
            return drawn;
        }
    }

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode left = new FakeNode(PipeKind.REQUEST);
    private final FakeNode right = new FakeNode(PipeKind.PROVIDER);
    private final Junction junction = new Junction(10);

    private void connect() {
        left.sharedPower = junction;
        right.sharedPower = junction;
        network.addRouter(LEFT, left, BUILT);
        network.addRouter(RIGHT, right, BUILT);
        network.updateLinks(LEFT, List.of(new Link(RIGHT, Side.EAST, 1)), BUILT);
        network.updateLinks(RIGHT, List.of(new Link(LEFT, Side.WEST, 1)), BUILT);
    }

    @Test
    void aJunctionTwoPipesShareCountsOnce() {
        connect();

        assertThat(network.usePower(LEFT, 15)).isFalse();
        assertThat(junction.stored).isEqualTo(10);
        assertThat(network.status(LEFT).energy()).isEqualTo(10);
    }

    @Test
    void aRouterThatJoinsInTheSameTickBringsItsEnergy() {
        left.power = 0;
        network.addRouter(LEFT, left, BUILT);
        assertThat(network.usePower(LEFT, 5)).isFalse();

        network.addRouter(RIGHT, right, BUILT);
        network.updateLinks(LEFT, List.of(new Link(RIGHT, Side.EAST, 1)), BUILT);
        network.updateLinks(RIGHT, List.of(new Link(LEFT, Side.WEST, 1)), BUILT);

        assertThat(network.usePower(LEFT, 5)).isTrue();
    }

    @Test
    void anEnergyStoreThatAppearsNextToARouterIsFound() {
        left.power = 0;
        right.power = 0;
        network.addRouter(LEFT, left, BUILT);
        network.addRouter(RIGHT, right, BUILT);
        network.updateLinks(LEFT, List.of(new Link(RIGHT, Side.EAST, 1)), BUILT);
        network.updateLinks(RIGHT, List.of(new Link(LEFT, Side.WEST, 1)), BUILT);
        assertThat(network.usePower(LEFT, 5)).isFalse();

        right.sharedPower = junction;
        network.powerSourcesChanged();

        assertThat(network.usePower(LEFT, 5)).isTrue();
    }

    @Test
    void whatTheSharedJunctionHoldsCanStillBeSpent() {
        connect();

        assertThat(network.usePower(LEFT, 10)).isTrue();
        assertThat(junction.stored).isZero();
    }

    @Test
    void anOrderThatCostsMoreThanTheSharedJunctionHoldsIsNotStarted() {
        connect();
        right.stock("coal", 64);

        assertThat(network.request(LEFT, batch("coal", 8), 0).powered()).isFalse();
        assertThat(right.shipments).isEmpty();
    }
}
