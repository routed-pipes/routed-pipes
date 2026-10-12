package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import logisticspipes.domain.routing.Side;
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

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode a = new FakeNode(PipeKind.BASIC);
    private final FakeNode b = new FakeNode(PipeKind.BASIC);
    private final FakeNode c = new FakeNode(PipeKind.BASIC);

    // A - B - C in a line running west to east, B is 2 from A and C is 5 from B
    private void line() {
        network.addRouter(A, a);
        network.addRouter(B, b);
        network.addRouter(C, c);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2)));
        network.updateLinks(B, List.of(new Link(A, Side.WEST, 2), new Link(C, Side.EAST, 5)));
        network.updateLinks(C, List.of(new Link(B, Side.WEST, 5)));
    }

    private static TravellingItem itemFrom(Side origin) {
        return new TravellingItem(batch("stone", 8), origin, null);
    }

    @Test
    void itemWithoutAnySinkHasNoExit() {
        line();
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item)).isEmpty();
        assertThat(item.destination()).isEmpty();
    }

    @Test
    void sendsItemTowardsTheOnlySink() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item)).contains(Side.EAST);
        assertThat(item.destination()).contains(C);
    }

    @Test
    void intermediateRouterPassesItemOnWithoutRedeciding() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(B, item)).contains(Side.EAST);
        assertThat(item.destination()).contains(C);
    }

    @Test
    void destinationDeliversIntoItsInventory() {
        line();
        c.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(C, item)).contains(Side.UP);
    }

    @Test
    void prefersTheNearestSink() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(B, item)).contains(Side.WEST);
        assertThat(item.destination()).contains(A);
    }

    @Test
    void usesOwnInventoryBeforeAnyOtherRouter() {
        line();
        a.inventories.add(Side.DOWN);
        b.inventories.add(Side.NORTH);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(B, item)).contains(Side.NORTH);
        assertThat(item.destination()).contains(B);
    }

    @Test
    void neverReturnsItemToTheInventoryItCameFrom() {
        line();
        a.inventories.add(Side.UP);
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item)).contains(Side.EAST);
        assertThat(item.destination()).contains(C);
    }

    @Test
    void picksNewDestinationWhenTheOldOneIsFull() {
        line();
        a.inventories.add(Side.DOWN);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(C, item)).contains(Side.WEST);
        assertThat(item.destination()).contains(A);
    }

    @Test
    void picksNewDestinationWhenTheOldOneIsGone() {
        line();
        a.inventories.add(Side.DOWN);
        c.inventories.add(Side.UP);
        network.removeRouter(C);
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.EAST, C);

        assertThat(network.chooseExit(B, item)).contains(Side.WEST);
        assertThat(item.destination()).contains(A);
    }

    @Test
    void ignoresSinksItCannotReach() {
        network.addRouter(A, a);
        network.addRouter(C, c);
        c.inventories.add(Side.UP);
        TravellingItem item = itemFrom(Side.UP);

        assertThat(network.chooseExit(A, item)).isEmpty();
    }

    @Test
    void clearsDestinationWhenNothingIsLeft() {
        line();
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(C, item)).isEmpty();
        assertThat(item.destination()).isEmpty();
    }
}
