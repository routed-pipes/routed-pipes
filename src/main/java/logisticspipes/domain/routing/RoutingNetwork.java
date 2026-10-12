package logisticspipes.domain.routing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// all routers of one world with their links, route tables are cached until something changes
public final class RoutingNetwork {

    private final Map<RouterId, List<Link>> links = new HashMap<>();
    private final Map<RouterId, Map<RouterId, Route>> routes = new HashMap<>();

    public void add(RouterId router) {
        links.putIfAbsent(router, List.of());
        routes.clear();
    }

    public void remove(RouterId router) {
        links.remove(router);
        routes.clear();
    }

    public boolean contains(RouterId router) {
        return links.containsKey(router);
    }

    public void setLinks(RouterId router, List<Link> routerLinks) {
        if (!links.containsKey(router)) {
            throw new IllegalArgumentException("unknown router: " + router);
        }
        links.put(router, List.copyOf(routerLinks));
        routes.clear();
    }

    public Map<RouterId, Route> routesFrom(RouterId source) {
        return routes.computeIfAbsent(source, this::calculate);
    }

    public Optional<Route> route(RouterId from, RouterId to) {
        return Optional.ofNullable(routesFrom(from).get(to));
    }

    private Map<RouterId, Route> calculate(RouterId source) {
        Map<RouterId, Route> calculated = RouteCalculator.calculate(source, links);
        // links can still point at routers that are gone, for example in unloaded chunks
        calculated.keySet().retainAll(links.keySet());
        return Map.copyOf(calculated);
    }
}
