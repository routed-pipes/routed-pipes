package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.statistics.PipeStatistics.Kind;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class NetworkOverviewTest {

    private static final RouterId A = router(1);
    private static final RouterId B = router(2);
    private static final RouterId C = router(3);
    private static final RouterId ISLAND = router(9);
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode a = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode b = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode c = new FakeNode(PipeKind.REQUEST);
    private final FakeNode island = new FakeNode(PipeKind.BASIC);

    // A - B - C in a line running west to east, A and C have an inventory on top, and one pipe off on its own
    private void line() {
        network.addRouter(A, a, 0);
        network.addRouter(B, b, 0);
        network.addRouter(C, c, 0);
        network.addRouter(ISLAND, island, 0);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2)), 0);
        network.updateLinks(B, List.of(new Link(A, Side.WEST, 2), new Link(C, Side.EAST, 5)), 0);
        network.updateLinks(C, List.of(new Link(B, Side.WEST, 5)), 0);
        a.inventories.add(Side.UP);
        a.inventoryRefs.put(Side.UP, new InventoryRef(1));
        c.inventories.add(Side.UP);
        c.inventoryRefs.put(Side.UP, new InventoryRef(3));
        a.power = 0;
        b.power = 0;
        c.power = 0;
        island.power = 0;
    }

    private static List<RouterId> routers(NetworkOverview overview) {
        return overview.pipes().stream().map(NetworkOverview.Entry::router).toList();
    }

    @Test
    void listsTheOwnPipeFirstAndTheOthersNearestFirst() {
        line();

        assertThat(routers(NetworkOverview.of(network, A))).containsExactly(A, B, C);
        assertThat(routers(NetworkOverview.of(network, C))).containsExactly(C, B, A);
        assertThat(routers(NetworkOverview.of(network, B))).containsExactly(B, A, C);
    }

    @Test
    void tellsTheKindAndTheDistanceOfEveryPipe() {
        line();

        List<NetworkOverview.Entry> pipes = NetworkOverview.of(network, A).pipes();

        assertThat(pipes).extracting(NetworkOverview.Entry::kind)
                .containsExactly(PipeKind.PROVIDER, PipeKind.CRAFTING, PipeKind.REQUEST);
        assertThat(pipes).extracting(NetworkOverview.Entry::distance).containsExactly(0, 2, 7);
    }

    @Test
    void pipesThatCannotBeReachedAreLeftOut() {
        line();

        assertThat(routers(NetworkOverview.of(network, A))).doesNotContain(ISLAND);
        assertThat(routers(NetworkOverview.of(network, ISLAND))).containsExactly(ISLAND);
    }

    @Test
    void tellsWhatEveryPipeSentReceivedAndRelayed() {
        line();
        TravellingItem item = new TravellingItem(batch("stone", 8), Side.UP, C);
        network.chooseExit(A, item, NOW);
        network.chooseExit(B, item.enteringFrom(Side.WEST), NOW);
        network.chooseExit(C, item.enteringFrom(Side.WEST), NOW);

        List<NetworkOverview.Entry> pipes = NetworkOverview.of(network, A).pipes();

        assertThat(pipes.get(0)).extracting(NetworkOverview.Entry::sent, NetworkOverview.Entry::received,
                NetworkOverview.Entry::relayed).containsExactly(8, 0, 0);
        assertThat(pipes.get(1)).extracting(NetworkOverview.Entry::sent, NetworkOverview.Entry::received,
                NetworkOverview.Entry::relayed).containsExactly(0, 0, 8);
        assertThat(pipes.get(2)).extracting(NetworkOverview.Entry::sent, NetworkOverview.Entry::received,
                NetworkOverview.Entry::relayed).containsExactly(0, 8, 0);
    }

    @Test
    void countsTheOpenOrdersOfEveryPipe() {
        line();
        b.outputOrders.add(new OutputOrder(C, batch("planks", 8), NOW));
        b.outputOrders.add(OutputOrder.extra(batch("sawdust", 2), NOW));

        assertThat(NetworkOverview.of(network, A).pipes()).extracting(NetworkOverview.Entry::openOrders).containsExactly(0, 2, 0);
    }

    @Test
    void addsUpTheEnergyOfThePipesThatCanBeReached() {
        line();
        a.power = 300;
        c.power = 50;
        island.power = 1000;

        assertThat(NetworkOverview.of(network, B).energy()).isEqualTo(350);
        assertThat(NetworkOverview.of(network, ISLAND).energy()).isEqualTo(1000);
    }

    @Test
    void lookingDoesNotChangeAnything() {
        line();
        a.power = 300;
        a.statistics.count(Kind.SENT, 4);

        NetworkOverview first = NetworkOverview.of(network, A);
        NetworkOverview second = NetworkOverview.of(network, A);

        assertThat(second).isEqualTo(first);
        assertThat(a.power).isEqualTo(300);
        assertThat(a.shipments).isEmpty();
    }

    @Test
    void aRouterTheNetworkDoesNotKnowHasNoOverview() {
        line();

        NetworkOverview overview = NetworkOverview.of(network, router(42));

        assertThat(overview.pipes()).isEmpty();
        assertThat(overview.energy()).isZero();
    }
}
