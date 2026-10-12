package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.ConnectorLinks;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.domain.tube.TubeKind;
import logisticspipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class LogisticsNetworkConnectorTest {

    private static final UUID CHANNEL = new UUID(7, 7);
    private static final RouterId REQUEST = router(1);
    private static final RouterId HERE = router(2);
    private static final RouterId THERE = router(3);
    private static final RouterId PROVIDER = router(4);
    private static final int PIPE = TubeKind.PIPE_WEIGHT;
    private static final long BUILT = -100;
    private static final long NOW = 0;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode here = new FakeNode(PipeKind.INVENTORY_SYSTEM_CONNECTOR);
    private final FakeNode there = new FakeNode(PipeKind.INVENTORY_SYSTEM_CONNECTOR);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER).stock("coal", 64);

    // request - here and there - provider, two networks with a connector each
    private void build() {
        here.inventories.add(Side.UP);
        there.inventories.add(Side.DOWN);
        network.addRouter(REQUEST, request, BUILT);
        network.addRouter(HERE, here, BUILT);
        network.addRouter(THERE, there, BUILT);
        network.addRouter(PROVIDER, provider, BUILT);
        network.updateLinks(REQUEST, List.of(new Link(HERE, Side.EAST, PIPE)), BUILT);
        network.updateLinks(HERE, List.of(new Link(REQUEST, Side.WEST, PIPE)), BUILT);
        network.updateLinks(THERE, List.of(new Link(PROVIDER, Side.EAST, PIPE)), BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(THERE, Side.WEST, PIPE)), BUILT);
    }

    private void join() {
        network.setConnector(HERE, CHANNEL, Side.UP, 0, BUILT);
        network.setConnector(THERE, CHANNEL, Side.DOWN, 0, BUILT);
    }

    @Test
    void networksWithoutASharedChannelStayApart() {
        build();
        network.setConnector(HERE, CHANNEL, Side.UP, 0, BUILT);
        network.setConnector(THERE, new UUID(7, 8), Side.DOWN, 0, BUILT);

        assertThat(network.available(REQUEST)).isEmpty();
        assertThat(network.connectorState(HERE)).isEqualTo(ConnectorLinks.State.DISCONNECTED);
    }

    @Test
    void connectorsOnTheSameChannelJoinTheirNetworks() {
        build();
        join();

        assertThat(network.available(REQUEST)).containsEntry(batch("coal", 1).type(), 64);
        assertThat(network.connectorState(HERE)).isEqualTo(ConnectorLinks.State.CONNECTED);
        assertThat(network.connectorsLinked(HERE, Side.UP, THERE)).isTrue();
        assertThat(network.request(REQUEST, batch("coal", 8), NOW).started()).isTrue();
        assertThat(provider.shipments).containsExactly(new FakeNode.Shipment(batch("coal", 8), REQUEST));
    }

    @Test
    void anItemForTheOtherNetworkLeavesThroughTheInventoryOfTheConnector() {
        build();
        join();
        TravellingItem ordered = new TravellingItem(batch("coal", 8), Side.EAST, REQUEST);

        assertThat(network.chooseExit(THERE, ordered, NOW)).contains(Side.DOWN);
        assertThat(ordered.nextHop()).contains(HERE);
        assertThat(network.chooseExit(HERE, new TravellingItem(batch("coal", 8), Side.UP, REQUEST), NOW)).contains(Side.WEST);
    }

    @Test
    void powerDoesNotCrossTheInventory() {
        build();
        join();
        request.power = 0;
        here.power = 0;

        assertThat(network.usePower(REQUEST, 10)).isFalse();
        assertThat(network.usePower(PROVIDER, 10)).isTrue();
        assertThat(network.request(REQUEST, batch("coal", 8), NOW).powered()).isFalse();
    }

    @Test
    void aConnectorThatLosesItsInventoryCutsTheLink() {
        build();
        join();

        network.setConnector(THERE, CHANNEL, null, 0, NOW);

        assertThat(network.available(REQUEST)).isEmpty();
        assertThat(network.connectorState(THERE)).isEqualTo(ConnectorLinks.State.MISMATCHED);
    }

    @Test
    void aConnectorThatIsRemovedCutsTheLink() {
        build();
        join();

        network.removeRouter(THERE, NOW);

        assertThat(network.available(REQUEST)).isEmpty();
        assertThat(network.connectorState(HERE)).isEqualTo(ConnectorLinks.State.DISCONNECTED);
    }

    @Test
    void theLinkSurvivesThePipesOfAConnectorBeingLookedAtAgain() {
        build();
        join();

        network.updateLinks(HERE, List.of(new Link(REQUEST, Side.WEST, PIPE)), NOW);

        assertThat(network.available(REQUEST)).isNotEmpty();
    }

    @Test
    void resistanceMakesAWayThroughPipesWin() {
        build();
        // six pipes from the request pipe straight to the provider
        network.updateLinks(REQUEST, List.of(new Link(HERE, Side.EAST, PIPE), new Link(PROVIDER, Side.NORTH, 6 * PIPE)), BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(THERE, Side.WEST, PIPE), new Link(REQUEST, Side.NORTH, 6 * PIPE)), BUILT);
        join();
        assertThat(network.routes(PROVIDER).route(REQUEST).orElseThrow().exit()).isEqualTo(Side.WEST);

        network.setConnector(THERE, CHANNEL, Side.DOWN, 10, NOW);

        assertThat(network.routes(PROVIDER).route(REQUEST).orElseThrow().exit()).isEqualTo(Side.NORTH);
    }
}
