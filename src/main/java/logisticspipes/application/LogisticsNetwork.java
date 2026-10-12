package logisticspipes.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.DestinationSelector;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkOffer;
import logisticspipes.domain.request.CraftJob;
import logisticspipes.domain.request.Crafter;
import logisticspipes.domain.request.Provision;
import logisticspipes.domain.request.RequestPlan;
import logisticspipes.domain.request.RequestPlanner;
import logisticspipes.domain.request.Stock;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.Route;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.RoutingNetwork;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;

// the routed pipes of one world and the decisions they make for passing, requesting and crafting items
public final class LogisticsNetwork {

    private static final int DEFAULT_ROUTE_PRIORITY = 0;
    // in ticks, after that an item is assumed lost and may be ordered again
    static final long PROVISION_TIMEOUT = 1200;
    static final long CRAFT_TIMEOUT = 24000;

    private final RoutingNetwork routing = new RoutingNetwork();
    private final Map<RouterId, RouterNode> nodes = new HashMap<>();
    private final PendingDeliveries pending = new PendingDeliveries();

    public void addRouter(RouterId id, RouterNode node) {
        nodes.put(id, node);
        routing.add(id);
    }

    public void removeRouter(RouterId id) {
        nodes.remove(id);
        routing.remove(id);
        pending.forget(id);
    }

    public void updateLinks(RouterId id, List<Link> links) {
        routing.setLinks(id, links);
    }

    // side an item has to leave the router through, picks a new destination if the current one does not work out
    public Optional<Side> chooseExit(RouterId at, TravellingItem item) {
        if (item.destination().filter(at::equals).isPresent()) {
            if (item.markArrived()) {
                pending.arrived(at, item.batch().type(), item.batch().count());
            }
            RouterNode node = nodes.get(at);
            if (node != null && node.kind() != PipeKind.BASIC && !node.hasInventory()) {
                // ordered to a pipe without an inventory, so it pops out right here
                return Optional.empty();
            }
        }
        Optional<Side> exit = item.destination().flatMap(destination -> exitTowards(at, destination, item));
        if (exit.isPresent()) {
            return exit;
        }
        Optional<RouterId> destination = findDestination(at, item);
        destination.ifPresentOrElse(item::routeTo, item::clearDestination);
        return destination.flatMap(found -> exitTowards(at, found, item));
    }

    private Optional<Side> exitTowards(RouterId at, RouterId destination, TravellingItem item) {
        if (destination.equals(at)) {
            return deliverySides(at, item).stream().min(Comparator.naturalOrder());
        }
        if (!nodes.containsKey(destination)) {
            return Optional.empty();
        }
        Optional<Route> route = routing.route(at, destination);
        route.ifPresent(found -> item.passTo(found.nextHop()));
        return route.map(Route::exit);
    }

    private Optional<RouterId> findDestination(RouterId at, TravellingItem item) {
        List<SinkOffer> offers = new ArrayList<>();
        for (Map.Entry<RouterId, RouterNode> entry : nodes.entrySet()) {
            if (entry.getValue().kind() != PipeKind.BASIC) {
                continue;
            }
            Set<Side> sides = entry.getKey().equals(at)
                    ? deliverySides(at, item)
                    : entry.getValue().inventoriesAccepting(item.batch());
            if (!sides.isEmpty()) {
                offers.add(new SinkOffer(entry.getKey(), DEFAULT_ROUTE_PRIORITY));
            }
        }
        return DestinationSelector.select(at, offers, routing.routesFrom(at));
    }

    // never hand an item back to the inventory it just came out of
    private Set<Side> deliverySides(RouterId at, TravellingItem item) {
        RouterNode node = nodes.get(at);
        if (node == null) {
            return Set.of();
        }
        Set<Side> sides = EnumSet.noneOf(Side.class);
        sides.addAll(node.inventoriesAccepting(item.batch()));
        sides.remove(item.origin());
        return sides;
    }

    // everything the requester could order, craftable items that are out of stock show up with 0
    public Map<ItemType, Integer> available(RouterId requester) {
        Map<ItemType, Integer> available = new LinkedHashMap<>();
        for (Stock stock : stockFor(requester)) {
            stock.items().forEach((type, count) -> available.merge(type, count, Integer::sum));
        }
        for (Crafter crafter : craftersFor(requester)) {
            available.putIfAbsent(crafter.recipe().output().type(), 0);
        }
        return available;
    }

    // plans the request and, if nothing is missing, starts it right away
    public RequestPlan request(RouterId requester, ItemBatch wanted, long now) {
        RequestPlan plan = new RequestPlanner(stockFor(requester), craftersFor(requester)).plan(requester, wanted);
        if (plan.complete()) {
            execute(plan, now);
        }
        return plan;
    }

    private void execute(RequestPlan plan, long now) {
        for (Provision provision : plan.provisions()) {
            RouterNode provider = nodes.get(provision.provider());
            if (provider == null) {
                continue;
            }
            ItemType type = provision.batch().type();
            int sent = provider.send(type, provision.batch().count(), provision.destination());
            pending.expect(provision.destination(), type, sent, now + PROVISION_TIMEOUT);
        }
        for (CraftJob craft : plan.crafts()) {
            RouterNode crafter = nodes.get(craft.crafter());
            if (crafter == null) {
                continue;
            }
            crafter.outputOrders().add(new OutputOrder(craft.destination(), craft.output()));
            pending.expect(craft.destination(), craft.output().type(), craft.output().count(), now + CRAFT_TIMEOUT);
        }
    }

    // regular upkeep of one router, crafting pipes ship finished items and suppliers reorder
    public void tick(RouterId id, long now) {
        RouterNode node = nodes.get(id);
        if (node == null) {
            return;
        }
        if (node.kind() == PipeKind.CRAFTING) {
            node.outputOrders().ship(order -> node.send(order.batch().type(), order.batch().count(), order.destination()));
        } else if (node.kind() == PipeKind.SUPPLIER) {
            Map<ItemType, Integer> contents = node.contents();
            node.supplyTargets().forEach((type, wanted) -> {
                int have = contents.getOrDefault(type, 0) + pending.expected(id, type, now);
                if (have < wanted) {
                    requestUpTo(id, type, wanted - have, now);
                }
            });
        }
    }

    // like request, but settles for whatever part of the amount is in stock
    private void requestUpTo(RouterId requester, ItemType type, int count, long now) {
        RequestPlan plan = request(requester, new ItemBatch(type, count), now);
        if (plan.complete()) {
            return;
        }
        int obtainable = count - plan.missing().getOrDefault(type, count);
        if (obtainable > 0 && plan.missing().size() == 1) {
            request(requester, new ItemBatch(type, obtainable), now);
        }
    }

    private List<RouterId> reachableFrom(RouterId requester) {
        Map<RouterId, Route> routes = routing.routesFrom(requester);
        return routes.values().stream()
                .sorted(Comparator.comparingInt(Route::distance).thenComparing(route -> route.destination().value()))
                .map(Route::destination)
                .filter(nodes::containsKey)
                .toList();
    }

    private List<Stock> stockFor(RouterId requester) {
        List<Stock> stock = new ArrayList<>();
        for (RouterId id : reachableFrom(requester)) {
            RouterNode node = nodes.get(id);
            if (node.kind() == PipeKind.PROVIDER) {
                stock.add(new Stock(id, node.contents()));
            }
        }
        return stock;
    }

    private List<Crafter> craftersFor(RouterId requester) {
        List<RouterId> reachable = reachableFrom(requester);
        List<Crafter> crafters = new ArrayList<>();
        for (RouterId id : reachable) {
            RouterNode node = nodes.get(id);
            if (node.kind() != PipeKind.CRAFTING || node.recipe().isEmpty()) {
                continue;
            }
            Optional<RouterId> target = node.satelliteId() == 0
                    ? Optional.of(id)
                    : satellite(reachable, node.satelliteId());
            target.ifPresent(found -> crafters.add(new Crafter(id, node.recipe().get(), found)));
        }
        return crafters;
    }

    private Optional<RouterId> satellite(List<RouterId> reachable, int satelliteId) {
        return reachable.stream()
                .filter(id -> nodes.get(id).kind() == PipeKind.SATELLITE && nodes.get(id).satelliteId() == satelliteId)
                .findFirst();
    }
}
