package logisticspipes.domain.routing;

import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import logisticspipes.domain.routing.ConnectorLinks.State;
import logisticspipes.domain.tube.TubeKind;
import org.junit.jupiter.api.Test;

class ConnectorLinksTest {

    private static final UUID CHANNEL = new UUID(1, 1);
    private static final UUID OTHER_CHANNEL = new UUID(1, 2);
    private static final RouterId A = router(1);
    private static final RouterId B = router(2);
    private static final RouterId C = router(3);

    private final ConnectorLinks connectors = new ConnectorLinks();

    @Test
    void twoConnectorsOnAChannelThatBothHaveAnInventoryAreLinkedThroughIt() {
        connectors.set(A, CHANNEL, Side.EAST, 0);
        connectors.set(B, CHANNEL, Side.UP, 0);

        assertThat(connectors.linksOf(A)).containsExactly(new Link(B, Side.EAST, TubeKind.PIPE_WEIGHT));
        assertThat(connectors.linksOf(B)).containsExactly(new Link(A, Side.UP, TubeKind.PIPE_WEIGHT));
        assertThat(connectors.linked(A, Side.EAST, B)).isTrue();
        assertThat(connectors.linked(A, Side.WEST, B)).isFalse();
    }

    @Test
    void aConnectorAloneOnItsChannelIsDisconnected() {
        connectors.set(A, CHANNEL, Side.EAST, 0);
        connectors.set(B, OTHER_CHANNEL, Side.EAST, 0);

        assertThat(connectors.linksOf(A)).isEmpty();
        assertThat(connectors.state(A)).isEqualTo(State.DISCONNECTED);
        assertThat(connectors.state(C)).isEqualTo(State.DISCONNECTED);
    }

    @Test
    void withoutAnInventoryOnBothEndsThereIsNoLink() {
        connectors.set(A, CHANNEL, Side.EAST, 0);
        connectors.set(B, CHANNEL, null, 0);

        assertThat(connectors.linksOf(A)).isEmpty();
        assertThat(connectors.linksOf(B)).isEmpty();
        assertThat(connectors.state(A)).isEqualTo(State.CONNECTED);
        assertThat(connectors.state(B)).isEqualTo(State.MISMATCHED);
    }

    @Test
    void everyConnectorOfAChannelIsLinkedToAllTheOthers() {
        connectors.set(A, CHANNEL, Side.EAST, 0);
        connectors.set(B, CHANNEL, Side.EAST, 0);
        connectors.set(C, CHANNEL, Side.EAST, 0);

        assertThat(connectors.linksOf(A)).extracting(Link::target).containsExactly(B, C);
        assertThat(connectors.linksOf(C)).extracting(Link::target).containsExactly(A, B);
    }

    @Test
    void resistanceMakesTheLinkOfItsOwnConnectorLonger() {
        connectors.set(A, CHANNEL, Side.EAST, 3);
        connectors.set(B, CHANNEL, Side.EAST, 0);

        assertThat(connectors.linksOf(A)).extracting(Link::distance).containsExactly(4 * TubeKind.PIPE_WEIGHT);
        assertThat(connectors.linksOf(B)).extracting(Link::distance).containsExactly(TubeKind.PIPE_WEIGHT);
    }

    @Test
    void resistanceStaysWithinItsLimits() {
        connectors.set(A, CHANNEL, Side.EAST, -5);
        connectors.set(B, CHANNEL, Side.EAST, Integer.MAX_VALUE);

        assertThat(connectors.linksOf(A)).extracting(Link::distance).containsExactly(TubeKind.PIPE_WEIGHT);
        assertThat(connectors.linksOf(B)).extracting(Link::distance)
                .containsExactly((1 + ConnectorLinks.MAX_RESISTANCE) * TubeKind.PIPE_WEIGHT);
    }

    @Test
    void settingTellsWhoseLinksMayHaveChanged() {
        assertThat(connectors.set(A, CHANNEL, Side.EAST, 0)).containsExactly(A);
        assertThat(connectors.set(B, CHANNEL, Side.EAST, 0)).containsExactlyInAnyOrder(A, B);
        assertThat(connectors.set(B, CHANNEL, Side.EAST, 0)).isEmpty();
        assertThat(connectors.set(C, OTHER_CHANNEL, Side.EAST, 0)).containsExactly(C);
        // moving to another channel touches the old partners and the new ones
        assertThat(connectors.set(B, OTHER_CHANNEL, Side.EAST, 0)).containsExactlyInAnyOrder(A, B, C);
    }

    @Test
    void aConnectorThatLeavesTakesItsLinksWithIt() {
        connectors.set(A, CHANNEL, Side.EAST, 0);
        connectors.set(B, CHANNEL, Side.EAST, 0);

        assertThat(connectors.set(B, null, Side.EAST, 0)).containsExactlyInAnyOrder(A, B);

        assertThat(connectors.linksOf(A)).isEmpty();
        assertThat(connectors.state(A)).isEqualTo(State.DISCONNECTED);
        assertThat(connectors.remove(A)).containsExactly(A);
        assertThat(connectors.isEmpty()).isTrue();
        assertThat(connectors.remove(A)).isEmpty();
    }
}
