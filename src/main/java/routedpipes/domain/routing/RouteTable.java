package routedpipes.domain.routing;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

// the shortest routes from one router to every router it can reach
public interface RouteTable {

    Optional<Route> route(RouterId destination);

    OptionalInt distance(RouterId destination);

    // everything reachable, nearest first, ties by id
    List<RouterId> byDistance();

    // a table backed by a plain map, for small cases and tests
    static RouteTable of(Map<RouterId, Route> routes) {
        Map<RouterId, Route> copy = Map.copyOf(routes);
        List<RouterId> ordered = copy.values().stream()
                .sorted(Comparator.comparingInt(Route::distance).thenComparing(route -> route.destination().value()))
                .map(Route::destination)
                .toList();
        return new RouteTable() {
            @Override
            public Optional<Route> route(RouterId destination) {
                return Optional.ofNullable(copy.get(destination));
            }

            @Override
            public OptionalInt distance(RouterId destination) {
                Route route = copy.get(destination);
                return route == null ? OptionalInt.empty() : OptionalInt.of(route.distance());
            }

            @Override
            public List<RouterId> byDistance() {
                return ordered;
            }
        };
    }
}
