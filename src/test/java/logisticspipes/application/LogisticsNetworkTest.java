package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import logisticspipes.domain.routing.Side;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class LogisticsNetworkTest {

    private static final RouterId A = router(1);
    private static final RouterId B = router(2);
    private static final RouterId C = router(3);

    // long after the network was put together
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode a = new FakeNode(PipeKind.BASIC);
    private final FakeNode b = new FakeNode(PipeKind.BASIC);
    private final FakeNode c = new FakeNode(PipeKind.BASIC);

    // A - B - C in a line running west to east, B is 2 from A and C is 5 from B
    private void line() {
        network.addRouter(A, a, 0);
        network.addRouter(B, b, 0);
        network.addRouter(C, c, 0);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2)), 0);
        network.updateLinks(B, List.of(new Link(A, Side.WEST, 2), new Link(C, Side.EAST, 5)), 0);
        network.updateLinks(C, List.of(new Link(B, Side.WEST, 5)), 0);
    }

    private static TravellingItem itemFrom(Side origin) {
        return new TravellingItem(batch("stone", 8), origin, null);
    }

    @Test
    void itemWithoutAnySinkHasNoExit() {
        line();
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item, NOW)).isEmpty();
        assertThat(item.destination()).isEmpty();
    }

    @Test
    void sendsItemTowardsTheOnlySink() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(C);
    }

    @Test
    void intermediateRouterPassesItemOnWithoutRedeciding() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(B, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(C);
    }

    @Test
    void destinationDeliversIntoItsInventory() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(C, item, NOW)).contains(Side.UP);
    }

    @Test
    void prefersTheNearestSink() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(B, item, NOW)).contains(Side.WEST);
        assertThat(item.destination()).contains(A);
    }

    @Test
    void usesOwnInventoryBeforeAnyOtherRouter() {
        line();
        a.inventories.add(Side.DOWN);
        b.inventories.add(Side.NORTH);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(B, item, NOW)).contains(Side.NORTH);
        assertThat(item.destination()).contains(B);
    }

    @Test
    void neverReturnsItemToTheInventoryItCameFrom() {
        line();
        a.inventories.add(Side.UP);
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(C);
    }

    @Test
    void picksNewDestinationWhenTheOldOneIsFull() {
        line();
        a.inventories.add(Side.DOWN);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(C, item, NOW)).contains(Side.WEST);
        assertThat(item.destination()).contains(A);
    }

    @Test
    void waitsAWhileForADestinationThatHasGoneMissing() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);
        network.removeRouter(C, 0);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.EAST, C);

        for (int tick = 0; tick < LogisticsNetwork.REROUTE_PATIENCE; tick++) {
            assertThat(network.chooseExit(B, item, NOW)).isEmpty();
            assertThat(item.destination()).contains(C);
            item.waitForExit();
        }

        assertThat(network.chooseExit(B, item, NOW)).contains(Side.WEST);
        assertThat(item.destination()).contains(A);
    }

    @Test
    void goesOnAsSoonAsTheMissingDestinationIsBack() {
        line();
        c.inventories.add(Side.UP);
        network.removeRouter(C, 0);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);
        assertThat(network.chooseExit(B, item, NOW)).isEmpty();

        network.addRouter(C, c, NOW);
        network.updateLinks(C, List.of(new Link(B, Side.WEST, 5)), NOW);

        assertThat(network.chooseExit(B, item, NOW + LogisticsNetwork.SETTLE_TICKS)).contains(Side.EAST);
    }

    @Test
    void waitingForTheNetworkToSettleDoesNotCountAgainstTheItem() {
        line();
        TravellingItem item = itemFrom(Side.UP);
        item.waitForExit();
        item.waitForExit();

        network.chooseExit(A, item, 1);

        assertThat(item.waited()).isZero();
    }

    @Test
    void ignoresSinksItCannotReach() {
        network.addRouter(A, a, 0);
        network.addRouter(C, c, 0);
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item, NOW)).isEmpty();
    }

    @Test
    void clearsDestinationWhenNothingIsLeft() {
        line();
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(C, item, NOW)).isEmpty();
        assertThat(item.destination()).isEmpty();
    }

    @Test
    void tellsTheItemWhichRouterComesNext() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        network.chooseExit(A, item, NOW);

        assertThat(item.nextHop()).contains(B);
    }

    @Test
    void filteredSinkBeatsANearerDefaultRoute() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);
        c.sink = new ItemSink(Set.of(batch("stone", 1).type()), false);
        network.sinksChanged(C);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(B, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(C);
    }

    @Test
    void filteredSinkIgnoresOtherItems() {
        line();
        c.inventories.add(Side.UP);
        c.sink = new ItemSink(Set.of(batch("dirt", 1).type()), false);
        network.sinksChanged(C);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item, NOW)).isEmpty();
    }

    @Test
    void handsOutNoNewDestinationWhileTheNetworkIsSettling() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item, LogisticsNetwork.SETTLE_TICKS - 1)).isEmpty();
        assertThat(item.destination()).isEmpty();
        assertThat(network.chooseExit(A, item, LogisticsNetwork.SETTLE_TICKS)).contains(Side.EAST);
    }

    @Test
    void keepsADestinationThatIsNotKnownYetWhileSettling() {
        line();
        a.inventories.add(Side.DOWN);
        network.removeRouter(C, 50);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.EAST, C);

        assertThat(network.chooseExit(B, item, 51)).isEmpty();
        assertThat(item.destination()).contains(C);
    }

    @Test
    void stillPassesItemsWithAKnownDestinationWhileSettling() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(B, item, 1)).contains(Side.EAST);
    }

    @Test
    void unchangedLinksDoNotCountAsAChange() {
        line();
        c.inventories.add(Side.UP);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2)), 95);

        assertThat(network.chooseExit(A, itemFrom(Side.UP), 96)).contains(Side.EAST);
    }

    @Test
    void looksOnlyIntoTheInventoryOfTheBestCandidate() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);

        network.chooseExit(B, itemFrom(Side.UP), NOW);

        assertThat(a.probes).isEqualTo(1);
        assertThat(c.probes).isZero();
    }

    @Test
    void movesOnToTheNextCandidateWhenTheBestIsFull() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(B, item, NOW)).contains(Side.EAST);
        assertThat(a.probes).isEqualTo(1);
        assertThat(c.probes).isEqualTo(1);
    }

    @Test
    void pipesChangingInAnUnconnectedNetworkDoNotHoldItemsUp() {
        line();
        c.inventories.add(Side.UP);
        FakeNode far = new FakeNode(PipeKind.BASIC);
        FakeNode farther = new FakeNode(PipeKind.BASIC);
        network.addRouter(router(8), far, 95);
        network.addRouter(router(9), farther, 96);
        network.updateLinks(router(8), List.of(new Link(router(9), Side.EAST, 1)), 97);
        network.removeRouter(router(9), 98);

        assertThat(network.chooseExit(A, itemFrom(Side.UP), 99)).contains(Side.EAST);
    }

    @Test
    void aRouterVanishingHoldsUpTheOnesThatWereConnectedToIt() {
        line();
        a.inventories.add(Side.DOWN);
        network.removeRouter(C, 95);

        assertThat(network.chooseExit(B, itemFrom(Side.UP), 96)).isEmpty();
        assertThat(network.chooseExit(B, itemFrom(Side.UP), 95 + LogisticsNetwork.SETTLE_TICKS)).contains(Side.WEST);
    }

    @Test
    void nothingIsDecidedInTheTickTheNetworkChanges() {
        line();
        c.inventories.add(Side.UP);
        network.addRouter(router(8), new FakeNode(PipeKind.BASIC), NOW);
        TravellingItem ordered = new TravellingItem(batch("stone", 8), Side.WEST, C);
        ordered.waitForExit();

        assertThat(network.chooseExit(B, ordered, NOW)).isEmpty();
        assertThat(ordered.waited()).isZero();
        assertThat(network.chooseExit(B, ordered, NOW + 1)).contains(Side.EAST);
    }

    @Test
    void onlySoManyRoutersWorkOutTheirRoutesInOneTick() {
        // a hub with many routers around it, each of which has an item to pass on
        int spokes = LogisticsNetwork.MAX_NEW_TABLES_PER_TICK + 5;
        FakeNode hub = new FakeNode(PipeKind.BASIC);
        hub.inventories.add(Side.UP);
        network.addRouter(router(1000), hub, 0);
        List<Link> hubLinks = new java.util.ArrayList<>();
        for (int i = 0; i < spokes; i++) {
            FakeNode spoke = new FakeNode(PipeKind.REQUEST);
            network.addRouter(router(2000 + i), spoke, 0);
            network.updateLinks(router(2000 + i), List.of(new Link(router(1000), Side.EAST, 1)), 0);
            hubLinks.add(new Link(router(2000 + i), Side.WEST, 1));
        }
        network.updateLinks(router(1000), hubLinks, 0);

        int routedFirstTick = 0;
        int routedSecondTick = 0;
        List<TravellingItem> waiting = new java.util.ArrayList<>();
        for (int i = 0; i < spokes; i++) {
            TravellingItem item = itemFrom(Side.UP);
            if (network.chooseExit(router(2000 + i), item, NOW).isPresent()) {
                routedFirstTick++;
            } else {
                waiting.add(item);
            }
        }
        for (int i = 0; i < spokes; i++) {
            if (network.chooseExit(router(2000 + i), itemFrom(Side.UP), NOW + 1).isPresent()) {
                routedSecondTick++;
            }
        }

        assertThat(routedFirstTick).isEqualTo(LogisticsNetwork.MAX_NEW_TABLES_PER_TICK);
        assertThat(waiting).allMatch(item -> item.waited() == 0);
        assertThat(routedSecondTick).isEqualTo(spokes);
    }
}
