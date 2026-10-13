package routedpipes.application;

import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import routedpipes.domain.logistics.FirewallRule;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

// whether a pipe shows the lit or the dark frame
class LogisticsNetworkPowerLookTest {

    private static final RouterId NEAR = router(1);
    private static final RouterId MIDDLE = router(2);
    private static final RouterId FAR = router(3);
    private static final RouterId ALONE = router(4);
    private static final long BUILT = -100;
    private static final long NOW = 0;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode near = new FakeNode(PipeKind.BASIC);
    private final FakeNode far = new FakeNode(PipeKind.BASIC);
    private final FakeNode alone = new FakeNode(PipeKind.BASIC);

    // near - middle - far in a row and one router on its own, only the far end has energy
    private void connect(FakeNode middle) {
        near.power = 0;
        middle.power = 0;
        alone.power = 0;
        network.addRouter(NEAR, near, BUILT);
        network.addRouter(MIDDLE, middle, BUILT);
        network.addRouter(FAR, far, BUILT);
        network.addRouter(ALONE, alone, BUILT);
        network.updateLinks(NEAR, List.of(new Link(MIDDLE, Side.EAST, 1)), BUILT);
        network.updateLinks(MIDDLE, List.of(new Link(NEAR, Side.WEST, 1), new Link(FAR, Side.EAST, 1)), BUILT);
        network.updateLinks(FAR, List.of(new Link(MIDDLE, Side.WEST, 1)), BUILT);
    }

    @Test
    void everyRouterConnectedToEnergyHasPowerAndNoOther() {
        connect(new FakeNode(PipeKind.BASIC));

        assertThat(network.hasPower(NEAR, NOW)).contains(true);
        assertThat(network.hasPower(MIDDLE, NOW)).contains(true);
        assertThat(network.hasPower(FAR, NOW)).contains(true);
        assertThat(network.hasPower(ALONE, NOW)).contains(false);
    }

    @Test
    void aRouterThatIsNotThereHasNoPower() {
        assertThat(network.hasPower(NEAR, NOW)).contains(false);
    }

    @Test
    void theAnswerIsKeptForAWhileSoThatAskingIsCheap() {
        connect(new FakeNode(PipeKind.BASIC));
        assertThat(network.hasPower(NEAR, NOW)).contains(true);

        far.power = 0;

        assertThat(network.hasPower(NEAR, NOW + LogisticsNetwork.POWER_LOOK_INTERVAL - 1)).contains(true);
        assertThat(network.hasPower(NEAR, NOW + LogisticsNetwork.POWER_LOOK_INTERVAL)).contains(false);
    }

    @Test
    void aChangeToTheNetworkIsSeenAtOnce() {
        connect(new FakeNode(PipeKind.BASIC));
        assertThat(network.hasPower(NEAR, NOW)).contains(true);

        network.updateLinks(MIDDLE, List.of(new Link(NEAR, Side.WEST, 1)), NOW + 1);
        network.updateLinks(FAR, List.of(), NOW + 1);

        assertThat(network.hasPower(NEAR, NOW + 2)).contains(false);
        assertThat(network.hasPower(FAR, NOW + 2)).contains(true);
    }

    @Test
    void whileRoutersAreStillRegisteringThereIsNoTelling() {
        connect(new FakeNode(PipeKind.BASIC));

        assertThat(network.hasPower(NEAR, BUILT)).isEmpty();
        assertThat(network.hasPower(NEAR, BUILT + 1)).contains(true);
    }

    @Test
    void aFirewallKeepsTheEnergyBehindItToItself() {
        FakeNode wall = new FakeNode(PipeKind.FIREWALL);
        wall.firewall = FirewallRule.DEFAULT;
        connect(wall);

        assertThat(network.hasPower(NEAR, NOW)).contains(false);
        assertThat(network.hasPower(FAR, NOW)).contains(true);
    }

    @Test
    void aFirewallThatLetsEnergyThroughLightsBothSides() {
        FakeNode wall = new FakeNode(PipeKind.FIREWALL);
        wall.firewall = new FirewallRule(java.util.Set.of(), true, false, false, false, false);
        connect(wall);

        assertThat(network.hasPower(NEAR, NOW)).contains(true);
    }
}
