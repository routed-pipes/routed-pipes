package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.FirewallRule;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.testing.FakeNode;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkFirewallTest {

    private static final RouterId NEAR = router(1);
    private static final RouterId WALL = router(2);
    private static final RouterId FAR = router(3);
    private static final long BUILT = -100;
    private static final long NOW = 0;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode near = new FakeNode(PipeKind.REQUEST);
    private final FakeNode wall = new FakeNode(PipeKind.FIREWALL);
    private final FakeNode far = new FakeNode(PipeKind.BASIC);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    private static FirewallRule rule(Set<ItemType> listed, boolean blacklist, boolean providing, boolean crafting, boolean sorting,
            boolean power) {
        return new FirewallRule(listed, blacklist, providing, crafting, sorting, power);
    }

    // near - wall - far in a row, the far end has an inventory and takes anything
    private void connect() {
        wall.firewall = rule(Set.of(), true, false, false, false, false);
        far.inventories.add(Side.UP);
        network.addRouter(NEAR, near, BUILT);
        network.addRouter(WALL, wall, BUILT);
        network.addRouter(FAR, far, BUILT);
        network.updateLinks(NEAR, List.of(new Link(WALL, Side.EAST, 1)), BUILT);
        network.updateLinks(WALL, List.of(new Link(NEAR, Side.WEST, 1), new Link(FAR, Side.EAST, 1)), BUILT);
        network.updateLinks(FAR, List.of(new Link(WALL, Side.WEST, 1)), BUILT);
    }

    // a long way round the firewall
    private void addDetour() {
        network.updateLinks(NEAR, List.of(new Link(WALL, Side.EAST, 1), new Link(FAR, Side.NORTH, 20)), BUILT);
        network.updateLinks(FAR, List.of(new Link(WALL, Side.WEST, 1), new Link(NEAR, Side.NORTH, 20)), BUILT);
    }

    private static TravellingItem loose(String name) {
        return new TravellingItem(batch(name, 4), Side.UP, null);
    }

    @Test
    void anOpenFirewallLetsEverythingThrough() {
        connect();

        assertThat(network.chooseExit(NEAR, loose("stone"), NOW)).contains(Side.EAST);
    }

    @Test
    void blacklistedItemsFindNoSinkBehindTheFirewall() {
        connect();
        wall.firewall = rule(Set.of(item("stone")), true, false, false, false, false);
        TravellingItem stone = loose("stone");

        assertThat(network.chooseExit(NEAR, stone, NOW)).isEmpty();
        assertThat(network.chooseExit(NEAR, loose("dirt"), NOW)).contains(Side.EAST);
    }

    @Test
    void withAWhitelistOnlyListedItemsPass() {
        connect();
        wall.firewall = rule(Set.of(item("stone")), false, false, false, false, false);

        assertThat(network.chooseExit(NEAR, loose("stone"), NOW)).contains(Side.EAST);
        assertThat(network.chooseExit(NEAR, loose("dirt"), NOW)).isEmpty();
    }

    @Test
    void sortingCanBeStoppedAltogether() {
        connect();
        wall.firewall = rule(Set.of(), true, false, false, true, false);

        assertThat(network.chooseExit(NEAR, loose("stone"), NOW)).isEmpty();
    }

    @Test
    void aWayAroundTheFirewallIsTakenWhenTheFirewallSaysNo() {
        connect();
        addDetour();
        wall.firewall = rule(Set.of(item("stone")), true, false, false, false, false);

        assertThat(network.chooseExit(NEAR, loose("stone"), NOW)).contains(Side.NORTH);
        assertThat(network.chooseExit(NEAR, loose("dirt"), NOW)).contains(Side.EAST);
    }

    @Test
    void theFirewallOnlyFiltersWhatLeavesIt() {
        connect();
        wall.firewall = rule(Set.of(), true, false, false, true, false);
        TravellingItem item = new TravellingItem(batch("stone", 4), Side.EAST, WALL, TransportMode.REQUESTED);

        // an item for the firewall pipe itself still gets there
        assertThat(network.chooseExit(FAR, item, NOW)).contains(Side.WEST);
    }

    @Test
    void providingThroughTheFirewallCanBeStopped() {
        connect();
        FakeNode provider = far;
        provider.provides = true;
        provider.stock("coal", 32);
        assertThat(network.available(NEAR)).containsEntry(item("coal"), 32);

        wall.firewall = rule(Set.of(), true, true, false, false, false);

        assertThat(network.available(NEAR)).isEmpty();
        assertThat(network.request(NEAR, batch("coal", 4), NOW).started()).isFalse();
    }

    @Test
    void providersBehindTheFirewallDoNotOfferRejectedItems() {
        connect();
        far.provides = true;
        far.stock("coal", 32);
        far.stock("iron", 8);
        wall.firewall = rule(Set.of(item("coal")), true, false, false, false, false);

        assertThat(network.available(NEAR)).containsOnlyKeys(item("iron"));
    }

    @Test
    void craftingThroughTheFirewallCanBeStopped() {
        connect();
        FakeNode crafter = new FakeNode(PipeKind.CRAFTING);
        crafter.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        RouterId crafterId = router(4);
        network.addRouter(crafterId, crafter, BUILT);
        network.updateLinks(crafterId, List.of(new Link(FAR, Side.WEST, 1)), BUILT);
        network.updateLinks(FAR, List.of(new Link(WALL, Side.WEST, 1), new Link(crafterId, Side.EAST, 1)), BUILT);
        assertThat(network.available(NEAR)).containsKey(item("planks"));

        wall.firewall = rule(Set.of(), true, false, true, false, false);

        assertThat(network.available(NEAR)).doesNotContainKey(item("planks"));
    }

    @Test
    void powerBehindTheFirewallIsOutOfReachByDefault() {
        connect();
        near.power = 0;
        wall.power = 0;
        far.provides = true;
        far.stock("coal", 32);

        assertThat(network.request(NEAR, batch("coal", 4), NOW).started()).isTrue();

        wall.firewall = FirewallRule.DEFAULT;
        assertThat(network.request(NEAR, batch("coal", 4), NOW).powered()).isFalse();
    }

    @Test
    void ruleTellsOrderedAndSortedItemsApart() {
        FirewallRule noSorting = rule(Set.of(), true, false, false, true, false);
        FirewallRule noProviding = rule(Set.of(), true, true, false, false, false);
        FirewallRule neither = rule(Set.of(), true, true, true, false, false);

        assertThat(noSorting.stopsSorted(item("stone"))).isTrue();
        assertThat(noSorting.stopsOrdered(item("stone"))).isFalse();
        assertThat(noProviding.stopsOrdered(item("stone"))).isFalse();
        assertThat(neither.stopsOrdered(item("stone"))).isTrue();
    }

    // the crafter has four planks it made earlier and nobody ordered
    private FakeNode crafterWithSpare() {
        FakeNode crafter = new FakeNode(PipeKind.CRAFTING);
        crafter.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        crafter.stock("planks", 4);
        RouterId crafterId = router(4);
        network.addRouter(crafterId, crafter, BUILT);
        network.updateLinks(crafterId, List.of(new Link(FAR, Side.WEST, 1)), BUILT);
        network.updateLinks(FAR, List.of(new Link(WALL, Side.WEST, 1), new Link(crafterId, Side.EAST, 1)), BUILT);
        return crafter;
    }

    @Test
    void spareCraftedItemsBehindAFirewallThatRejectsThemAreNotOffered() {
        connect();
        FakeNode crafter = crafterWithSpare();
        wall.firewall = rule(Set.of(item("planks")), true, false, false, false, false);

        assertThat(network.request(NEAR, batch("planks", 4), NOW).started()).isFalse();
        assertThat(crafter.shipments).isEmpty();
    }

    @Test
    void spareCraftedItemsBehindAFirewallThatStopsProvidingAreNotOffered() {
        connect();
        FakeNode crafter = crafterWithSpare();
        wall.firewall = rule(Set.of(), true, true, false, false, false);

        network.request(NEAR, batch("planks", 4), NOW);
        assertThat(crafter.shipments).isEmpty();
    }

    @Test
    void spareCraftedItemsPassAnOpenFirewall() {
        connect();
        FakeNode crafter = crafterWithSpare();

        assertThat(network.request(NEAR, batch("planks", 4), NOW).started()).isTrue();
        assertThat(crafter.shipments).hasSize(1);
    }

    // the short way crosses a firewall that blocks stone, the long way a second firewall that lets it through
    private FakeNode secondWallOnTheLongWay() {
        FakeNode second = new FakeNode(PipeKind.FIREWALL);
        second.firewall = rule(Set.of(), true, false, false, false, false);
        RouterId secondId = router(5);
        network.addRouter(secondId, second, BUILT);
        network.updateLinks(secondId, List.of(new Link(NEAR, Side.WEST, 10), new Link(FAR, Side.EAST, 10)), BUILT);
        network.updateLinks(NEAR, List.of(new Link(WALL, Side.EAST, 1), new Link(secondId, Side.NORTH, 10)), BUILT);
        network.updateLinks(FAR, List.of(new Link(WALL, Side.WEST, 1), new Link(secondId, Side.NORTH, 10)), BUILT);
        wall.firewall = rule(Set.of(item("stone")), true, false, false, false, false);
        return second;
    }

    @Test
    void theWayAroundMayCrossAFirewallThatLetsTheItemThrough() {
        connect();
        secondWallOnTheLongWay();

        assertThat(network.chooseExit(NEAR, loose("stone"), NOW)).contains(Side.NORTH);
        assertThat(network.chooseExit(NEAR, loose("dirt"), NOW)).contains(Side.EAST);
    }

    @Test
    void theWayAroundIsClosedWhenTheSecondFirewallBlocksTheItemToo() {
        connect();
        FakeNode second = secondWallOnTheLongWay();
        second.firewall = rule(Set.of(item("stone")), true, false, false, false, false);

        assertThat(network.chooseExit(NEAR, loose("stone"), NOW)).isEmpty();
    }
}
