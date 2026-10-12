package logisticspipes.domain.routing;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class RouteCalculator {

    private RouteCalculator() {
    }

    // shortest routes from source to every reachable router, links are one way
    public static Map<RouterId, Route> calculate(RouterId source, Map<RouterId, List<Link>> links) {
        Map<RouterId, Route> routes = new HashMap<>();
        // ties are broken by router id so the result does not depend on map order
        PriorityQueue<Route> open = new PriorityQueue<>(Comparator
                .comparingInt(Route::distance)
                .thenComparing((Route route) -> route.destination().value())
                .thenComparing(Route::exit));
        for (Link link : links.getOrDefault(source, List.of())) {
            open.add(new Route(link.target(), link.exit(), link.distance()));
        }
        while (!open.isEmpty()) {
            Route route = open.poll();
            RouterId reached = route.destination();
            if (reached.equals(source) || routes.containsKey(reached)) {
                continue;
            }
            routes.put(reached, route);
            for (Link link : links.getOrDefault(reached, List.of())) {
                if (!routes.containsKey(link.target())) {
                    open.add(new Route(link.target(), route.exit(), route.distance() + link.distance()));
                }
            }
        }
        return routes;
    }
}
