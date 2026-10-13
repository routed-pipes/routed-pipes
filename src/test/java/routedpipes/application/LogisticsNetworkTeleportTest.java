package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import routedpipes.domain.logistics.FirewallRule;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.ConnectorLinks;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.domain.tube.TubeKind;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class LogisticsNetworkTeleportTest {

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
    private final FakeNode here = new FakeNode(PipeKind.TELEPORT);
    private final FakeNode there = new FakeNode(PipeKind.TELEPORT);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER).stock("coal", 64);

    // request - here and there - provider, two networks with a teleport pipe each and no inventory at those
    private void build() {
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
        network.setDirectConnector(HERE, CHANNEL, Side.DOWN, 0, BUILT);
        network.setDirectConnector(THERE, CHANNEL, Side.UP, 0, BUILT);
    }

    @Test
    void aRequestIsServedAcrossTwoTeleportPipes() {
        build();
        join();

        assertThat(network.available(REQUEST)).containsEntry(batch("coal", 1).type(), 64);
        assertThat(network.connectorState(HERE)).isEqualTo(ConnectorLinks.State.CONNECTED);
        assertThat(network.request(REQUEST, batch("coal", 8), NOW).started()).isTrue();
        assertThat(provider.shipments).containsExactly(new FakeNode.Shipment(batch("coal", 8), REQUEST));
    }

    @Test
    void anItemForTheOtherNetworkLeavesThroughTheFreeSideAndGoesOnFromThePartner() {
        build();
        join();
        TravellingItem ordered = new TravellingItem(batch("coal", 8), Side.EAST, REQUEST);

        assertThat(network.chooseExit(THERE, ordered, NOW)).contains(Side.UP);
        assertThat(ordered.nextHop()).contains(HERE);
        assertThat(network.connectorsLinked(THERE, Side.UP, HERE)).isTrue();
        // it comes in through the free side of the partner and keeps where it is going
        TravellingItem arrived = ordered.enteringFrom(Side.DOWN);
        assertThat(network.chooseExit(HERE, arrived, NOW)).contains(Side.WEST);
        assertThat(arrived.destination()).contains(REQUEST);
        assertThat(arrived.mode()).isEqualTo(TransportMode.REQUESTED);
    }

    @Test
    void aRequestIsPaidFromAJunctionOnTheOtherSide() {
        build();
        join();
        request.power = 0;
        here.power = 0;
        there.power = 0;
        provider.power = 100;

        RequestOutcome outcome = network.request(REQUEST, batch("coal", 8), NOW);

        assertThat(outcome.powered()).isTrue();
        assertThat(outcome.started()).isTrue();
        assertThat(provider.power).isEqualTo(100 - 8 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM);
        assertThat(network.status(REQUEST).energy()).isEqualTo(provider.power);
        assertThat(network.hasPower(REQUEST, NOW)).contains(true);
    }

    @Test
    void withoutTheLinkThePowerOfTheOtherSideIsOutOfReach() {
        build();
        request.power = 0;
        here.power = 0;
        assertThat(network.usePower(REQUEST, 10)).isFalse();
        join();
        assertThat(network.usePower(REQUEST, 10)).isTrue();

        network.setDirectConnector(THERE, CHANNEL, null, 0, NOW);

        assertThat(network.usePower(REQUEST, 10)).isFalse();
        assertThat(network.usePower(PROVIDER, 10)).isTrue();
    }

    @Test
    void aFirewallThatBlocksPowerStillKeepsItOnItsSide() {
        build();
        join();
        request.power = 0;
        here.power = 0;
        there.power = 0;
        FakeNode firewall = new FakeNode(PipeKind.FIREWALL);
        firewall.power = 0;
        firewall.firewall = FirewallRule.DEFAULT;
        RouterId wall = router(5);
        // provider and its power move behind a firewall: there - firewall - provider
        network.addRouter(wall, firewall, BUILT);
        network.updateLinks(THERE, List.of(new Link(wall, Side.EAST, PIPE)), BUILT);
        network.updateLinks(wall, List.of(new Link(THERE, Side.WEST, PIPE), new Link(PROVIDER, Side.EAST, PIPE)), BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(wall, Side.WEST, PIPE)), BUILT);

        assertThat(network.usePower(REQUEST, 10)).isFalse();
        assertThat(network.usePower(PROVIDER, 10)).isTrue();
    }

    @Test
    void aTeleportPipeWithoutAFreeSideCutsTheLink() {
        build();
        join();

        network.setDirectConnector(THERE, CHANNEL, null, 0, NOW);

        assertThat(network.available(REQUEST)).isEmpty();
        assertThat(network.connectorState(THERE)).isEqualTo(ConnectorLinks.State.MISMATCHED);
    }

    @Test
    void aTeleportPipeAndAnInventoryConnectorOnOneChannelStayApart() {
        build();
        there.inventories.add(Side.UP);
        network.setDirectConnector(HERE, CHANNEL, Side.DOWN, 0, BUILT);
        network.setConnector(THERE, CHANNEL, Side.UP, 0, BUILT);

        assertThat(network.available(REQUEST)).isEmpty();
        assertThat(network.connectorState(HERE)).isEqualTo(ConnectorLinks.State.DISCONNECTED);
        assertThat(network.connectorState(THERE)).isEqualTo(ConnectorLinks.State.DISCONNECTED);
    }

    @Test
    void aTeleportPipeThatIsRemovedCutsTheLink() {
        build();
        join();

        network.removeRouter(THERE, NOW);

        assertThat(network.available(REQUEST)).isEmpty();
        assertThat(network.connectorState(HERE)).isEqualTo(ConnectorLinks.State.DISCONNECTED);
    }
}
