package routedpipes.domain.routing;

import static routedpipes.testing.Fixtures.router;
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

    private void twoBases() {
        for (int i = 1; i <= 5; i++) {
            network.add(router(i));
        }
        network.setLinks(router(1), List.of(new Link(router(2), Side.EAST, 1)));
        network.setLinks(router(2), List.of(new Link(router(1), Side.WEST, 1)));
        network.setLinks(router(4), List.of(new Link(router(5), Side.EAST, 1)));
        network.setLinks(router(5), List.of(new Link(router(4), Side.WEST, 1)));
    }

    @Test
    void aChangeInOneBaseLeavesTheTablesOfAnotherAlone() {
        twoBases();
        RouteTable before = network.routesFrom(router(4));
        int group = network.component(router(4)).getAsInt();

        network.setLinks(router(2), List.of(new Link(router(1), Side.WEST, 1), new Link(router(3), Side.UP, 1)));
        network.remove(router(1));
        network.add(router(7));

        assertThat(network.stale(router(4))).isFalse();
        assertThat(network.hasRoutesFrom(router(4))).isTrue();
        assertThat(network.routesFrom(router(4))).isSameAs(before);
        assertThat(network.component(router(4))).hasValue(group);
    }

    @Test
    void whatChangedIsStaleUntilItIsAskedAbout() {
        twoBases();
        network.component(router(1));

        network.setLinks(router(2), List.of());
        network.setLinks(router(1), List.of());

        assertThat(network.stale(router(1))).isTrue();
        assertThat(network.stale(router(2))).isTrue();
        assertThat(network.hasRoutesFrom(router(1))).isFalse();
        assertThat(network.route(router(1), router(2))).isEmpty();
        assertThat(network.stale(router(1))).isFalse();
        assertThat(network.component(router(1))).isNotEqualTo(network.component(router(2)));
    }

    @Test
    void aLinkToAnotherBaseJoinsTheTwo() {
        twoBases();
        network.routesFrom(router(1));
        network.routesFrom(router(4));

        network.setLinks(router(2), List.of(new Link(router(1), Side.WEST, 1), new Link(router(4), Side.UP, 3)));

        assertThat(network.stale(router(5))).isTrue();
        assertThat(network.route(router(1), router(5))).contains(new Route(router(5), Side.EAST, router(2), 5));
        assertThat(network.component(router(1))).isEqualTo(network.component(router(5)));
    }

    @Test
    void aRouterThatComesBackJoinsTheOnesThatStillLinkToIt() {
        twoBases();
        network.remove(router(2));
        assertThat(network.route(router(1), router(2))).isEmpty();

        network.add(router(2));

        assertThat(network.route(router(1), router(2))).contains(new Route(router(2), Side.EAST, router(2), 1));
        assertThat(network.component(router(1))).isEqualTo(network.component(router(2)));
    }

    // whatever happened before, the answers are those of a network put together from scratch
    @Test
    void afterAnyChangesItAnswersLikeAFreshNetwork() {
        java.util.Random random = new java.util.Random(11);
        for (int round = 0; round < 40; round++) {
            RoutingNetwork changed = new RoutingNetwork();
            java.util.Map<RouterId, List<Link>> links = new java.util.HashMap<>();
            int routers = 4 + random.nextInt(20);
            for (int step = 0; step < 150; step++) {
                RouterId id = router(random.nextInt(routers));
                int what = random.nextInt(10);
                if (what < 2) {
                    changed.add(id);
                    links.putIfAbsent(id, List.of());
                } else if (what < 3) {
                    changed.remove(id);
                    links.remove(id);
                } else if (what < 7 && links.containsKey(id)) {
                    List<Link> out = new java.util.ArrayList<>();
                    for (int link = random.nextInt(3); link > 0; link--) {
                        out.add(new Link(router(random.nextInt(routers)), Side.values()[random.nextInt(6)], 1 + random.nextInt(3)));
                    }
                    changed.setLinks(id, out);
                    links.put(id, out);
                } else if (what < 9) {
                    // asking in between makes it put islands together that later changes break up again
                    changed.routesFrom(id);
                    changed.component(router(random.nextInt(routers)));
                    continue;
                } else {
                    continue;
                }
                if (random.nextInt(4) != 0) {
                    continue;
                }
                RoutingNetwork fresh = new RoutingNetwork();
                links.keySet().forEach(fresh::add);
                links.forEach(fresh::setLinks);
                for (int from = 0; from < routers; from++) {
                    assertThat(changed.contains(router(from))).isEqualTo(fresh.contains(router(from)));
                    assertThat(changed.routesFrom(router(from)).byDistance()).as("round %s step %s from %s", round, step, from)
                            .isEqualTo(fresh.routesFrom(router(from)).byDistance());
                    for (int to = 0; to < routers; to++) {
                        assertThat(changed.route(router(from), router(to))).as("round %s step %s from %s to %s", round, step, from, to)
                                .isEqualTo(fresh.route(router(from), router(to)));
                        assertThat(changed.component(router(from)).equals(changed.component(router(to)))
                                && changed.component(router(from)).isPresent())
                                .as("round %s step %s whether %s and %s belong together", round, step, from, to)
                                .isEqualTo(fresh.component(router(from)).equals(fresh.component(router(to)))
                                        && fresh.component(router(from)).isPresent());
                    }
                }
            }
        }
    }
}
