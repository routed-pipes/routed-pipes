package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import routedpipes.domain.logistics.FirewallRule;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class LogisticsNetworkSystemEntranceTest {

    private static final UUID CARD = new UUID(5, 1);
    private static final UUID OTHER_CARD = new UUID(5, 2);
    private static final RouterId ENTRANCE = router(1);
    private static final RouterId SINK = router(2);
    private static final RouterId NEAR = router(4);
    // the far one has the smaller id, so it is not picked for coming first in some list
    private static final RouterId FAR = router(3);
    private static final long BUILT = -100;
    private static final long NOW = 0;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode entrance = new FakeNode(PipeKind.SYSTEM_ENTRANCE);
    private final FakeNode sink = new FakeNode(PipeKind.BASIC);
    private final FakeNode near = new FakeNode(PipeKind.SYSTEM_DESTINATION);
    private final FakeNode far = new FakeNode(PipeKind.SYSTEM_DESTINATION);

    // a sink right beside the entrance to the west, to the east the near destination and behind it the far one
    private void build() {
        sink.inventories.add(Side.UP);
        near.inventories.add(Side.UP);
        far.inventories.add(Side.UP);
        network.addRouter(ENTRANCE, entrance, BUILT);
        network.addRouter(SINK, sink, BUILT);
        network.addRouter(NEAR, near, BUILT);
        network.addRouter(FAR, far, BUILT);
        network.updateLinks(ENTRANCE, List.of(new Link(SINK, Side.WEST, 1), new Link(NEAR, Side.EAST, 5)), BUILT);
        network.updateLinks(SINK, List.of(new Link(ENTRANCE, Side.EAST, 1)), BUILT);
        network.updateLinks(NEAR, List.of(new Link(ENTRANCE, Side.WEST, 5), new Link(FAR, Side.EAST, 5)), BUILT);
        network.updateLinks(FAR, List.of(new Link(NEAR, Side.WEST, 5)), BUILT);
    }

    private static TravellingItem loose() {
        return new TravellingItem(batch("stone", 4), Side.UP, null);
    }

    @Test
    void withoutACardAnEntranceSortsLikeAnyPipe() {
        build();
        near.frequency = CARD;
        TravellingItem item = loose();

        assertThat(network.chooseExit(ENTRANCE, item, NOW)).contains(Side.WEST);
        assertThat(item.destination()).contains(SINK);
    }

    @Test
    void whatComesInWithoutADestinationGoesToTheDestinationPipeWithTheSameCard() {
        build();
        entrance.frequency = CARD;
        near.frequency = OTHER_CARD;
        far.frequency = CARD;
        TravellingItem item = loose();

        assertThat(network.chooseExit(ENTRANCE, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(FAR);
        assertThat(item.mode()).isEqualTo(TransportMode.REQUESTED);
        assertThat(entrance.power).isEqualTo(1_000_000 - LogisticsNetwork.ENERGY_PER_SYSTEM_ENTRY);
    }

    @Test
    void ofSeveralDestinationsWithTheCardTheNearestIsPicked() {
        build();
        entrance.frequency = CARD;
        near.frequency = CARD;
        far.frequency = CARD;
        TravellingItem item = loose();

        network.chooseExit(ENTRANCE, item, NOW);

        assertThat(item.destination()).contains(NEAR);
    }

    @Test
    void withNoMatchingDestinationTheItemIsSortedLikeAnyOther() {
        build();
        entrance.frequency = CARD;
        near.frequency = OTHER_CARD;
        TravellingItem item = loose();

        assertThat(network.chooseExit(ENTRANCE, item, NOW)).contains(Side.WEST);
        assertThat(item.destination()).contains(SINK);
    }

    @Test
    void withoutPowerTheItemIsNotSentToTheDestination() {
        build();
        entrance.frequency = CARD;
        near.frequency = CARD;
        for (FakeNode node : List.of(entrance, sink, near, far)) {
            node.power = 0;
        }
        TravellingItem item = loose();

        network.chooseExit(ENTRANCE, item, NOW);

        assertThat(item.destination()).isNotEqualTo(Optional.of(NEAR));
    }

    @Test
    void anItemThatAlreadyHasADestinationKeepsIt() {
        build();
        entrance.frequency = CARD;
        near.frequency = CARD;
        TravellingItem item = new TravellingItem(batch("stone", 4), Side.UP, SINK);

        assertThat(network.chooseExit(ENTRANCE, item, NOW)).contains(Side.WEST);
        assertThat(item.destination()).contains(SINK);
    }

    @Test
    void onlyAnEntranceSendsItemsToADestinationPipe() {
        build();
        near.frequency = CARD;
        far.frequency = CARD;
        TravellingItem item = new TravellingItem(batch("stone", 4), Side.EAST, null);

        network.chooseExit(SINK, item, NOW);

        assertThat(item.destination()).contains(SINK);
    }

    @Test
    void theDestinationPipePutsTheItemIntoItsInventoryAndWaitsWhenThatIsFull() {
        build();
        entrance.frequency = CARD;
        near.frequency = CARD;
        TravellingItem item = loose();
        network.chooseExit(ENTRANCE, item, NOW);
        TravellingItem arriving = item.enteringFrom(Side.WEST);

        assertThat(network.chooseExit(NEAR, arriving, NOW)).contains(Side.UP);

        near.room = false;
        TravellingItem waiting = item.enteringFrom(Side.WEST);
        assertThat(network.chooseExit(NEAR, waiting, NOW)).isEmpty();
        assertThat(waiting.destination()).contains(NEAR);
    }

    @Test
    void aDestinationBehindAFirewallThatStopsTheItemIsPassedOver() {
        RouterId wall = router(9);
        FakeNode firewall = new FakeNode(PipeKind.FIREWALL);
        firewall.firewall = new FirewallRule(Set.of(batch("stone", 1).type()), true, false, false, false, false);
        build();
        network.addRouter(wall, firewall, BUILT);
        // the near destination is now only reached through the firewall, the far one has a way of its own
        network.updateLinks(ENTRANCE, List.of(new Link(SINK, Side.WEST, 1), new Link(wall, Side.EAST, 2), new Link(FAR, Side.NORTH, 20)),
                BUILT);
        network.updateLinks(wall, List.of(new Link(ENTRANCE, Side.WEST, 2), new Link(NEAR, Side.EAST, 2)), BUILT);
        network.updateLinks(NEAR, List.of(new Link(wall, Side.WEST, 2)), BUILT);
        network.updateLinks(FAR, List.of(new Link(ENTRANCE, Side.SOUTH, 20)), BUILT);
        entrance.frequency = CARD;
        near.frequency = CARD;
        far.frequency = CARD;
        TravellingItem item = loose();

        assertThat(network.chooseExit(ENTRANCE, item, NOW)).contains(Side.NORTH);
        assertThat(item.destination()).contains(FAR);
    }
}
