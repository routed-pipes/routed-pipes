package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.FirewallRule;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.statistics.PipeStatistics.Kind;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkRouteOfTest {

    private static final RouterId NEAR = router(1);
    private static final RouterId WALL = router(2);
    private static final RouterId FAR = router(3);
    private static final RouterId ISLAND = router(9);
    private static final long BUILT = -100;
    private static final long NOW = 0;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode near = new FakeNode(PipeKind.REQUEST);
    private final FakeNode wall = new FakeNode(PipeKind.FIREWALL);
    private final FakeNode far = new FakeNode(PipeKind.BASIC);
    private final FakeNode island = new FakeNode(PipeKind.BASIC);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    // near - wall - far in a row, the wall is a firewall that lets everything through, and one pipe off on its own
    private void connect() {
        wall.firewall = new FirewallRule(Set.of(), true, false, false, false, false);
        far.inventories.add(Side.UP);
        network.addRouter(NEAR, near, BUILT);
        network.addRouter(WALL, wall, BUILT);
        network.addRouter(FAR, far, BUILT);
        network.addRouter(ISLAND, island, BUILT);
        network.updateLinks(NEAR, List.of(new Link(WALL, Side.EAST, 1)), BUILT);
        network.updateLinks(WALL, List.of(new Link(NEAR, Side.WEST, 1), new Link(FAR, Side.EAST, 1)), BUILT);
        network.updateLinks(FAR, List.of(new Link(WALL, Side.WEST, 1)), BUILT);
    }

    // a long way round the firewall
    private void addDetour() {
        network.updateLinks(NEAR, List.of(new Link(WALL, Side.EAST, 1), new Link(FAR, Side.NORTH, 20)), BUILT);
        network.updateLinks(FAR, List.of(new Link(WALL, Side.WEST, 1), new Link(NEAR, Side.NORTH, 20)), BUILT);
    }

    private static TravellingItem sorted(String name, RouterId destination) {
        return new TravellingItem(batch(name, 4), Side.UP, destination, TransportMode.SORTED);
    }

    @Test
    void listsEveryRouterFromHereToTheDestination() {
        connect();

        assertThat(network.routeOf(NEAR, sorted("stone", FAR))).containsExactly(NEAR, WALL, FAR);
        assertThat(network.routeOf(WALL, sorted("stone", FAR))).containsExactly(WALL, FAR);
        assertThat(network.routeOf(FAR, sorted("stone", NEAR))).containsExactly(FAR, WALL, NEAR);
    }

    @Test
    void anItemThatHasArrivedHasOnlyItsDestinationLeft() {
        connect();

        assertThat(network.routeOf(FAR, sorted("stone", FAR))).containsExactly(FAR);
    }

    @Test
    void anItemWithoutADestinationHasNoRoute() {
        connect();

        assertThat(network.routeOf(NEAR, new TravellingItem(batch("stone", 4), Side.UP, null))).isEmpty();
    }

    @Test
    void aDestinationThatIsGoneOrOutOfReachGivesNoRoute() {
        connect();

        assertThat(network.routeOf(NEAR, sorted("stone", router(42)))).isEmpty();
        assertThat(network.routeOf(NEAR, sorted("stone", ISLAND))).isEmpty();
        assertThat(network.routeOf(router(42), sorted("stone", FAR))).isEmpty();
    }

    @Test
    void whatAFirewallStopsGoesAroundIt() {
        connect();
        addDetour();
        wall.firewall = new FirewallRule(Set.of(item("stone")), true, false, false, false, false);

        assertThat(network.routeOf(NEAR, sorted("stone", FAR))).containsExactly(NEAR, FAR);
        assertThat(network.routeOf(NEAR, sorted("dirt", FAR))).containsExactly(NEAR, WALL, FAR);
    }

    @Test
    void aFirewallWithNoWayAroundItLeavesNoRoute() {
        connect();
        wall.firewall = new FirewallRule(Set.of(item("stone")), true, false, false, false, false);

        assertThat(network.routeOf(NEAR, sorted("stone", FAR))).isEmpty();
        // the firewall pipe itself can still be reached
        assertThat(network.routeOf(NEAR, sorted("stone", WALL))).containsExactly(NEAR, WALL);
    }

    @Test
    void orderedItemsOnlyMindAFirewallThatStopsOrders() {
        connect();
        addDetour();
        // sorting is stopped, providing and crafting are not
        wall.firewall = new FirewallRule(Set.of(), true, false, false, true, false);
        TravellingItem ordered = new TravellingItem(batch("stone", 4), Side.UP, FAR, TransportMode.REQUESTED);

        assertThat(network.routeOf(NEAR, ordered)).containsExactly(NEAR, WALL, FAR);
        assertThat(network.routeOf(NEAR, sorted("stone", FAR))).containsExactly(NEAR, FAR);
    }

    // walks the item through the network the way the pipes do and writes down where it was
    private List<RouterId> walked(RouterId from, TravellingItem item) {
        List<RouterId> passed = new ArrayList<>();
        RouterId at = from;
        TravellingItem moving = item;
        for (int hops = 0; hops < 10; hops++) {
            passed.add(at);
            if (moving.destination().filter(at::equals).isPresent()) {
                break;
            }
            Side exit = network.chooseExit(at, moving, NOW).orElseThrow();
            at = moving.nextHop().orElseThrow();
            moving = moving.enteringFrom(exit.opposite());
        }
        return passed;
    }

    @Test
    void theRouteIsTheOneTheItemThenTakes() {
        connect();
        addDetour();
        wall.firewall = new FirewallRule(Set.of(item("stone")), true, false, false, false, false);

        List<RouterId> stone = network.routeOf(NEAR, sorted("stone", FAR));
        List<RouterId> dirt = network.routeOf(NEAR, sorted("dirt", FAR));

        assertThat(walked(NEAR, sorted("stone", FAR))).isEqualTo(stone);
        assertThat(walked(NEAR, sorted("dirt", FAR))).isEqualTo(dirt);
    }

    @Test
    void lookingUpARouteLeavesTheItemAndTheNetworkAlone() {
        connect();
        TravellingItem item = sorted("stone", FAR);
        int power = near.power + wall.power + far.power;

        network.routeOf(NEAR, item);

        assertThat(item.nextHop()).isEmpty();
        assertThat(item.destination()).contains(FAR);
        assertThat(item.mode()).isEqualTo(TransportMode.SORTED);
        assertThat(item.decided()).isFalse();
        assertThat(item.batch()).isEqualTo(batch("stone", 4));
        assertThat(near.power + wall.power + far.power).isEqualTo(power);
        for (FakeNode node : List.of(near, wall, far)) {
            for (Kind kind : Kind.values()) {
                assertThat(node.statistics.session(kind)).isZero();
            }
        }
    }
}
