package routedpipes.domain.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import routedpipes.testing.Fixtures;
import org.junit.jupiter.api.Test;

class RouteCalculatorTest {

    private static final RouterId A = Fixtures.router(1);
    private static final RouterId B = Fixtures.router(2);
    private static final RouterId C = Fixtures.router(3);
    private static final RouterId D = Fixtures.router(4);

    @Test
    void routesToDirectNeighbourThroughItsExit() {
        var routes = RouteCalculator.calculate(A, Map.of(A, List.of(new Link(B, Side.EAST, 5))));

        assertThat(routes).containsOnly(Map.entry(B, new Route(B, Side.EAST, B, 5)));
    }

    @Test
    void keepsFirstHopExitAcrossMultipleHops() {
        var routes = RouteCalculator.calculate(A, Map.of(
                A, List.of(new Link(B, Side.NORTH, 2)),
                B, List.of(new Link(C, Side.UP, 3)),
                C, List.of(new Link(D, Side.WEST, 4))));

        assertThat(routes.get(D)).isEqualTo(new Route(D, Side.NORTH, B, 9));
    }

    @Test
    void prefersShorterDetourOverLongDirectLink() {
        var routes = RouteCalculator.calculate(A, Map.of(
                A, List.of(new Link(C, Side.SOUTH, 10), new Link(B, Side.EAST, 2)),
                B, List.of(new Link(C, Side.DOWN, 3))));

        assertThat(routes.get(C)).isEqualTo(new Route(C, Side.EAST, B, 5));
    }

    @Test
    void leavesOutUnreachableRouters() {
        var routes = RouteCalculator.calculate(A, Map.of(
                A, List.of(new Link(B, Side.EAST, 1)),
                C, List.of(new Link(D, Side.EAST, 1))));

        assertThat(routes).containsOnlyKeys(B);
    }

    @Test
    void doesNotFollowLinksBackwards() {
        var routes = RouteCalculator.calculate(A, Map.of(B, List.of(new Link(A, Side.WEST, 1))));

        assertThat(routes).isEmpty();
    }

    @Test
    void neverRoutesToItself() {
        var routes = RouteCalculator.calculate(A, Map.of(
                A, List.of(new Link(B, Side.EAST, 1)),
                B, List.of(new Link(A, Side.WEST, 1))));

        assertThat(routes).containsOnlyKeys(B);
    }

    @Test
    void breaksTiesByExitSoResultIsStable() {
        var routes = RouteCalculator.calculate(A, Map.of(
                A, List.of(new Link(B, Side.EAST, 4), new Link(B, Side.UP, 4))));

        assertThat(routes.get(B)).isEqualTo(new Route(B, Side.UP, B, 4));
    }

    @Test
    void rejectsNonPositiveDistance() {
        assertThatThrownBy(() -> new Link(B, Side.EAST, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesTheFirstRouterOnTheWay() {
        // B and C hang off the same side of A, as they do behind a fork of plain pipes
        var routes = RouteCalculator.calculate(A, Map.of(
                A, List.of(new Link(B, Side.EAST, 3), new Link(C, Side.EAST, 4)),
                C, List.of(new Link(D, Side.UP, 2))));

        assertThat(routes.get(D)).isEqualTo(new Route(D, Side.EAST, C, 6));
        assertThat(routes.get(B)).isEqualTo(new Route(B, Side.EAST, B, 3));
    }
}
