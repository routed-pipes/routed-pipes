package routedpipes.application;

import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import routedpipes.domain.logistics.FirewallRule;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeNode;
import routedpipes.testing.FakePowerNode;
import org.junit.jupiter.api.Test;

class SubSystemPowerTest {

    private static final RouterId WEST = router(1);
    private static final RouterId MIDDLE = router(2);
    private static final RouterId EAST = router(3);
    private static final RouterId DETOUR = router(4);
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final SubSystemPower power = network.subSystemPower();
    private final Map<RouterId, FakeNode> nodes = new HashMap<>();

    private FakePowerNode add(RouterId id) {
        return add(id, PipeKind.BASIC);
    }

    private FakePowerNode add(RouterId id, PipeKind kind) {
        FakeNode node = new FakeNode(kind);
        node.subSystemPower = new FakePowerNode();
        // no junctions unless the test puts energy there
        node.power = 0;
        nodes.put(id, node);
        network.addRouter(id, node, BUILT);
        return (FakePowerNode) node.subSystemPower;
    }

    private static FakePowerNode.Provider provider(FakePowerNode beside, int stored) {
        FakePowerNode.Provider provider = new FakePowerNode.Provider(stored);
        beside.providers.add(provider);
        return provider;
    }

    @Test
    void aProviderDeliversToThePipeNextToIt() {
        FakePowerNode west = add(WEST);

        assertThat(power.deliver(List.of(WEST), WEST, 700)).isTrue();

        assertThat(west.received).isEqualTo(700);
    }

    @Test
    void nothingIsDeliveredToAPipeThatIsGone() {
        assertThat(power.deliver(List.of(WEST), WEST, 700)).isFalse();
    }

    @Test
    void nothingIsDeliveredToAPipeThatDoesNotLetTheEnergyIn() {
        FakePowerNode west = add(WEST);
        west.passes = false;

        assertThat(power.deliver(List.of(WEST), WEST, 700)).isFalse();

        assertThat(west.received).isZero();
    }

    @Test
    void nothingIsDeliveredToAPipeWithoutThePowerPart() {
        network.addRouter(WEST, new FakeNode(PipeKind.BASIC), BUILT);

        assertThat(power.deliver(List.of(WEST), WEST, 700)).isFalse();
    }

    @Test
    void nothingIsDeliveredToAPipeTheProviderHasNoWayTo() {
        add(WEST);
        FakePowerNode middle = add(MIDDLE);

        assertThat(power.deliver(List.of(WEST), MIDDLE, 700)).isFalse();

        assertThat(middle.received).isZero();
    }

    @Test
    void aSupplyingPipeOrdersWhatItMissesAndTheNetworkPaysForIt() {
        FakePowerNode.Provider provider = provider(add(WEST), 1_000_000);
        nodes.get(WEST).power = 500;

        power.refill(WEST, 10_000);

        assertThat(provider.destinations).containsExactly(WEST);
        assertThat(provider.amounts).containsExactly(10_000);
        assertThat(nodes.get(WEST).power).isEqualTo(400);
    }

    @Test
    void severalProvidersAreAskedInProportionToWhatTheyHold() {
        FakePowerNode west = add(WEST);
        FakePowerNode.Provider big = provider(west, 3_000_000);
        FakePowerNode.Provider small = provider(west, 1_000_000);
        nodes.get(WEST).power = 500;

        power.refill(WEST, 8000);

        assertThat(big.amounts).containsExactly(6000);
        assertThat(small.amounts).containsExactly(2000);
        assertThat(nodes.get(WEST).power).isEqualTo(420);
    }

    @Test
    void nothingIsOrderedOrPaidWhileTheProvidersAreEmpty() {
        FakePowerNode.Provider provider = provider(add(WEST), 0);
        nodes.get(WEST).power = 500;

        power.refill(WEST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(nodes.get(WEST).power).isEqualTo(500);
    }

    @Test
    void nothingIsOrderedWhenTheNetworkCannotPay() {
        FakePowerNode.Provider provider = provider(add(WEST), 1_000_000);
        nodes.get(WEST).power = 99;

        power.refill(WEST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(nodes.get(WEST).power).isEqualTo(99);
    }

    @Test
    void smallOrdersAreFree() {
        FakePowerNode.Provider provider = provider(add(WEST), 1_000_000);

        power.refill(WEST, 99);

        assertThat(provider.amounts).containsExactly(99);
    }

    // west - middle - east in a straight row, a provider at the west end and a junction at the east end
    private FakePowerNode.Provider row(PipeKind middle) {
        FakePowerNode.Provider provider = provider(add(WEST), 1_000_000);
        add(MIDDLE, middle);
        add(EAST);
        nodes.get(EAST).power = 1000;
        power.updateStraightLinks(WEST, Set.of(MIDDLE));
        power.updateStraightLinks(MIDDLE, Set.of(WEST, EAST));
        power.updateStraightLinks(EAST, Set.of(MIDDLE));
        return provider;
    }

    private static FirewallRule firewall(boolean blockPower) {
        return new FirewallRule(Set.of(), true, false, false, false, blockPower);
    }

    @Test
    void energyGoesAlongARowOfPipesThatLetItPass() {
        FakePowerNode.Provider provider = row(PipeKind.BASIC);

        power.refill(EAST, 10_000);

        assertThat(power.sourcesFor(EAST)).containsExactly(EAST, MIDDLE, WEST);
        assertThat(provider.destinations).containsExactly(EAST);
        assertThat(provider.amounts).containsExactly(10_000);
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isTrue();
        assertThat(((FakePowerNode) nodes.get(EAST).subSystemPower).received).isEqualTo(10_000);
    }

    @Test
    void aPipeOnTheWayThatDoesNotLetItPassStopsTheEnergy() {
        FakePowerNode.Provider provider = row(PipeKind.BASIC);
        ((FakePowerNode) nodes.get(MIDDLE).subSystemPower).passes = false;

        power.refill(EAST, 10_000);

        assertThat(power.sourcesFor(EAST)).containsExactly(EAST);
        assertThat(provider.amounts).isEmpty();
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isFalse();
    }

    @Test
    void thePipeNextToTheProviderHasToLetTheEnergyPassToo() {
        FakePowerNode.Provider provider = row(PipeKind.BASIC);
        ((FakePowerNode) nodes.get(WEST).subSystemPower).passes = false;

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isFalse();
    }

    // routed for items, but the pipes between them make a turn
    // provider at west, then a teleport pair, then the pipe that wants the energy at east
    private FakePowerNode.Provider acrossAPair(boolean direct) {
        FakePowerNode.Provider provider = provider(add(WEST), 1_000_000);
        add(MIDDLE, PipeKind.TELEPORT);
        add(DETOUR, PipeKind.TELEPORT);
        add(EAST);
        nodes.get(EAST).power = 1000;
        power.updateStraightLinks(WEST, Set.of(MIDDLE));
        power.updateStraightLinks(MIDDLE, Set.of(WEST));
        power.updateStraightLinks(DETOUR, Set.of(EAST));
        power.updateStraightLinks(EAST, Set.of(DETOUR));
        UUID channel = UUID.randomUUID();
        if (direct) {
            network.setDirectConnector(MIDDLE, channel, Side.UP, 0, BUILT);
            network.setDirectConnector(DETOUR, channel, Side.UP, 0, BUILT);
        } else {
            network.setConnector(MIDDLE, channel, Side.UP, 0, BUILT);
            network.setConnector(DETOUR, channel, Side.UP, 0, BUILT);
        }
        return provider;
    }

    @Test
    void energyCrossesATeleportLink() {
        FakePowerNode.Provider provider = acrossAPair(true);

        power.refill(EAST, 10_000);

        assertThat(provider.destinations).containsExactly(EAST);
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isTrue();
    }

    @Test
    void aTeleportPipeThatDoesNotLetItPassStopsTheEnergy() {
        FakePowerNode.Provider provider = acrossAPair(true);
        ((FakePowerNode) nodes.get(DETOUR).subSystemPower).passes = false;

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isFalse();
    }

    @Test
    void energyDoesNotCrossAnInventoryConnector() {
        FakePowerNode.Provider provider = acrossAPair(false);

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isFalse();
    }

    @Test
    void energyDoesNotGoRoundCorners() {
        FakePowerNode.Provider provider = provider(add(WEST), 1_000_000);
        add(EAST);
        network.updateLinks(WEST, List.of(new Link(EAST, Side.EAST, 3)), BUILT);
        network.updateLinks(EAST, List.of(new Link(WEST, Side.NORTH, 3)), BUILT);
        power.updateStraightLinks(WEST, Set.of());
        power.updateStraightLinks(EAST, Set.of());

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isFalse();
    }

    @Test
    void aFirewallThatBlocksPowerStopsTheEnergy() {
        FakePowerNode.Provider provider = row(PipeKind.FIREWALL);
        nodes.get(MIDDLE).firewall = firewall(true);

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isFalse();
    }

    @Test
    void aFirewallThatAllowsPowerLetsTheEnergyThrough() {
        FakePowerNode.Provider provider = row(PipeKind.FIREWALL);
        nodes.get(MIDDLE).firewall = firewall(false);

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).containsExactly(10_000);
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isTrue();
    }

    @Test
    void aFirewallOnlyCountsWhenThereIsNoStraightWayAroundIt() {
        FakePowerNode.Provider provider = row(PipeKind.FIREWALL);
        nodes.get(MIDDLE).firewall = firewall(true);
        add(DETOUR);
        power.updateStraightLinks(EAST, Set.of(MIDDLE, DETOUR));
        power.updateStraightLinks(DETOUR, Set.of(EAST, WEST));
        power.updateStraightLinks(WEST, Set.of(MIDDLE, DETOUR));

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).containsExactly(10_000);
        assertThat(power.deliver(List.of(WEST), EAST, 10_000)).isTrue();
    }

    @Test
    void aProviderNextToABlockingFirewallStillGivesItsEnergy() {
        row(PipeKind.FIREWALL);
        nodes.get(MIDDLE).firewall = firewall(true);
        FakePowerNode.Provider atTheWall = provider((FakePowerNode) nodes.get(MIDDLE).subSystemPower, 500_000);

        power.refill(EAST, 10_000);

        assertThat(atTheWall.amounts).containsExactly(10_000);
        assertThat(power.deliver(List.of(MIDDLE), EAST, 10_000)).isTrue();
    }

    @Test
    void aProviderNextToTwoPipesIsAskedOnce() {
        FakePowerNode.Provider provider = row(PipeKind.BASIC);
        ((FakePowerNode) nodes.get(MIDDLE).subSystemPower).providers.add(provider);

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).containsExactly(10_000);
    }

    @Test
    void theNetworkPaysWithEnergyFromAnywhereInIt() {
        FakePowerNode.Provider provider = row(PipeKind.BASIC);
        network.updateLinks(EAST, List.of(new Link(MIDDLE, Side.WEST, 1)), BUILT);
        network.updateLinks(MIDDLE, List.of(new Link(EAST, Side.EAST, 1)), BUILT);
        nodes.get(EAST).power = 0;
        nodes.get(MIDDLE).power = 300;

        power.refill(EAST, 10_000);

        assertThat(provider.amounts).containsExactly(10_000);
        assertThat(nodes.get(MIDDLE).power).isEqualTo(200);
    }

    @Test
    void aRouterThatIsGoneCarriesNothing() {
        FakePowerNode.Provider provider = row(PipeKind.BASIC);
        network.removeRouter(MIDDLE, BUILT);

        power.refill(EAST, 10_000);

        assertThat(power.sourcesFor(EAST)).containsExactly(EAST);
        assertThat(provider.amounts).isEmpty();
    }

    @Test
    void aPipeThatDoesNotLetTheEnergyInOrdersNothing() {
        FakePowerNode west = add(WEST);
        west.passes = false;
        FakePowerNode.Provider provider = provider(west, 1_000_000);
        nodes.get(WEST).power = 500;

        power.refill(WEST, 10_000);

        assertThat(provider.amounts).isEmpty();
        assertThat(nodes.get(WEST).power).isEqualTo(500);
    }
}
