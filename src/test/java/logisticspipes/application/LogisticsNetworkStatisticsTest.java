package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.statistics.PipeStatistics.Kind;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class LogisticsNetworkStatisticsTest {

    private static final RouterId A = router(1);
    private static final RouterId B = router(2);
    private static final RouterId C = router(3);
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode a = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode b = new FakeNode(PipeKind.BASIC);
    private final FakeNode c = new FakeNode(PipeKind.REQUEST);

    // A - B - C in a line running west to east, A and C have an inventory on top
    private void line() {
        network.addRouter(A, a, 0);
        network.addRouter(B, b, 0);
        network.addRouter(C, c, 0);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2)), 0);
        network.updateLinks(B, List.of(new Link(A, Side.WEST, 2), new Link(C, Side.EAST, 5)), 0);
        network.updateLinks(C, List.of(new Link(B, Side.WEST, 5)), 0);
        a.inventories.add(Side.UP);
        a.inventoryRefs.put(Side.UP, new InventoryRef(1));
        c.inventories.add(Side.UP);
        c.inventoryRefs.put(Side.UP, new InventoryRef(3));
    }

    private static int[] session(FakeNode node) {
        return new int[] {node.statistics.session(Kind.SENT), node.statistics.session(Kind.RECEIVED),
                node.statistics.session(Kind.RELAYED)};
    }

    @Test
    void anItemOutOfTheOwnInventoryCountsAsSent() {
        line();

        network.chooseExit(A, new TravellingItem(batch("stone", 8), Side.UP, C), NOW);

        assertThat(session(a)).containsExactly(8, 0, 0);
        assertThat(a.statistics.lifetime(Kind.SENT)).isEqualTo(8);
    }

    @Test
    void anItemPassingThroughCountsAsRelayed() {
        line();

        network.chooseExit(B, new TravellingItem(batch("stone", 8), Side.WEST, C), NOW);

        assertThat(session(b)).containsExactly(0, 0, 8);
    }

    @Test
    void anItemReachingItsDestinationCountsAsReceived() {
        line();

        network.chooseExit(C, new TravellingItem(batch("stone", 8), Side.WEST, C), NOW);

        assertThat(session(c)).containsExactly(0, 8, 0);
    }

    @Test
    void whatDidNotFitAndTriesAgainIsNotReceivedTwice() {
        line();
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);
        network.chooseExit(C, item, NOW);

        network.chooseExit(C, item.remainder(batch("stone", 3)), NOW + 1);

        assertThat(session(c)).containsExactly(0, 8, 0);
    }

    @Test
    void anItemWaitingForRoomIsCountedOnceItGetsIn() {
        line();
        c.room = false;
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.WEST, C);

        assertThat(network.chooseExit(C, item, NOW)).isEmpty();
        assertThat(session(c)).containsExactly(0, 0, 0);

        c.room = true;
        network.chooseExit(C, item, NOW + 1);
        assertThat(session(c)).containsExactly(0, 8, 0);
    }

    @Test
    void anOrderDroppingOutOfAPipeWithoutInventoryWasReceivedToo() {
        line();

        network.chooseExit(B, new TravellingItem(batch("stone", 8), Side.WEST, B), NOW);

        assertThat(session(b)).containsExactly(0, 8, 0);
    }

    @Test
    void anItemWithNowhereToGoCountsNothing() {
        line();

        // nobody in this line takes items that were not ordered
        assertThat(network.chooseExit(B, new TravellingItem(batch("stone", 8), Side.WEST, null), NOW)).isEmpty();

        assertThat(session(b)).containsExactly(0, 0, 0);
    }
}
