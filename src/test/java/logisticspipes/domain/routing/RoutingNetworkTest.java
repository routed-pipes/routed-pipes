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

        assertThat(network.route(router(1), router(2))).contains(new Route(router(2), Side.EAST, router(2), 3));
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

        assertThat(network.routesFrom(router(1)).byDistance()).isEmpty();
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
        assertThat(network.routesFrom(router(1)).byDistance()).isEmpty();
    }

    @Test
    void rejectsLinksForUnknownRouter() {
        assertThatThrownBy(() -> network.setLinks(router(1), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsWhetherLinksChanged() {
        network.add(router(1));
        network.add(router(2));

        assertThat(network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 3)))).isTrue();
        assertThat(network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 3)))).isFalse();
        assertThat(network.setLinks(router(1), List.of())).isTrue();
    }

    // the fast search inside the network has to agree with the simple reference search on every route
    @Test
    void agreesWithTheReferenceSearchOnRandomNetworks() {
        java.util.Random random = new java.util.Random(7);
        for (int round = 0; round < 60; round++) {
            RoutingNetwork fast = new RoutingNetwork();
            int routers = 2 + random.nextInt(40);
            java.util.Map<RouterId, List<Link>> links = new java.util.HashMap<>();
            for (int i = 0; i < routers; i++) {
                fast.add(router(i));
            }
            for (int i = 0; i < routers; i++) {
                List<Link> out = new java.util.ArrayList<>();
                for (int link = random.nextInt(4); link > 0; link--) {
                    // a few links lead to routers that do not exist, as after a chunk unloaded
                    out.add(new Link(router(random.nextInt(routers + 2)), Side.values()[random.nextInt(6)], 1 + random.nextInt(3)));
                }
                links.put(router(i), out);
                fast.setLinks(router(i), out);
            }
            for (int source = 0; source < routers; source++) {
                java.util.Map<RouterId, Route> expected = RouteCalculator.calculate(router(source), links);
                expected.keySet().retainAll(links.keySet());
                RouteTable actual = fast.routesFrom(router(source));
                assertThat(actual.byDistance()).containsExactlyInAnyOrderElementsOf(expected.keySet());
                for (RouterId destination : expected.keySet()) {
                    assertThat(actual.route(destination)).as("round %s from %s to %s", round, source, destination)
                            .contains(expected.get(destination));
                    assertThat(actual.distance(destination)).hasValue(expected.get(destination).distance());
                }
            }
        }
    }

    @Test
    void listsReachableRoutersNearestFirst() {
        network.add(router(1));
        network.add(router(2));
        network.add(router(3));
        network.add(router(4));
        network.setLinks(router(1), List.of(new Link(router(3), Side.EAST, 5), new Link(router(2), Side.UP, 5), new Link(router(4), Side.DOWN, 1)));

        assertThat(network.routesFrom(router(1)).byDistance()).containsExactly(router(4), router(2), router(3));
    }

    @Test
    void groupsRoutersThatAreConnectedInEitherDirection() {
        for (int i = 1; i <= 5; i++) {
            network.add(router(i));
        }
        network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 1)));
        network.setLinks(router(3), List.of(new Link(router(2), Side.WEST, 1)));
        network.setLinks(router(4), List.of(new Link(router(5), Side.EAST, 1)));

        assertThat(network.component(router(1))).isEqualTo(network.component(router(2))).isEqualTo(network.component(router(3)));
        assertThat(network.component(router(4))).isEqualTo(network.component(router(5)));
        assertThat(network.component(router(1))).isNotEqualTo(network.component(router(4)));
        assertThat(network.component(router(9))).isEmpty();
    }
}
