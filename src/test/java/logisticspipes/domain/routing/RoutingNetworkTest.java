package logisticspipes.domain.routing;

import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class RoutingNetworkTest {

    private final RoutingNetwork network = new RoutingNetwork();

    @Test
    void findsRouteBetweenLinkedRouters() {
        network.add(router(1));
        network.add(router(2));
        network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 3)));

        assertThat(network.route(router(1), router(2))).contains(new Route(router(2), Side.EAST, 3));
        assertThat(network.route(router(2), router(1))).isEmpty();
    }

    @Test
    void recalculatesAfterLinksChange() {
        network.add(router(1));
        network.add(router(2));
        network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 3)));
        assertThat(network.route(router(1), router(2))).isPresent();

        network.setLinks(router(1), List.of());

        assertThat(network.route(router(1), router(2))).isEmpty();
    }

    @Test
    void ignoresLinksToRoutersThatAreNotRegistered() {
        network.add(router(1));
        network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 3)));

        assertThat(network.routesFrom(router(1))).isEmpty();
    }

    @Test
    void dropsRoutesThroughRemovedRouter() {
        network.add(router(1));
        network.add(router(2));
        network.add(router(3));
        network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 1)));
        network.setLinks(router(2), List.of(new Link(router(3), Side.EAST, 1)));
        assertThat(network.route(router(1), router(3))).isPresent();

        network.remove(router(2));

        assertThat(network.contains(router(2))).isFalse();
        assertThat(network.routesFrom(router(1))).isEmpty();
    }

    @Test
    void rejectsLinksForUnknownRouter() {
        assertThatThrownBy(() -> network.setLinks(router(1), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
