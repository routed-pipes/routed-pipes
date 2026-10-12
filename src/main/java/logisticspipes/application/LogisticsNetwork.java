package logisticspipes.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.DestinationSelector;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.ItemSink;
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

    // in ticks, after that an item is assumed lost and may be ordered again
    static final long PROVISION_TIMEOUT = 1200;
    static final long CRAFT_TIMEOUT = 24000;
    // how long after a change to the network no new destinations are handed out
    static final long SETTLE_TICKS = 10;
    // moving an item on purpose costs energy, sorting what passes by is free
    public static final int ENERGY_PER_ITEM = 10;

    private final RoutingNetwork routing = new RoutingNetwork();
    private final Map<RouterId, RouterNode> nodes = new HashMap<>();
    private final PendingDeliveries pending = new PendingDeliveries();
    private long lastChange = Long.MIN_VALUE / 2;

    public void addRouter(RouterId id, RouterNode node, long now) {
        nodes.put(id, node);
        routing.add(id);
        lastChange = now;
    }

    public void removeRouter(RouterId id, long now) {
        nodes.remove(id);
        routing.remove(id);
        pending.forget(id);
        lastChange = now;
    }

    public void updateLinks(RouterId id, List<Link> links, long now) {
        if (routing.setLinks(id, links)) {
            lastChange = now;
        }
    }

    // side an item has to leave the router through, picks a new destination if the current one does not work out
    // empty means there is nowhere to go right now, the pipe asks again for a while before it gives up
    public Optional<Side> chooseExit(RouterId at, TravellingItem item, long now) {
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
        if (now - lastChange < SETTLE_TICKS) {
            // routers are still finding each other, a choice made now could be the wrong one
            return Optional.empty();
        }
        Optional<RouterId> destination = findDestination(at, item.batch(), item.origin(), true, ItemSink.DEFAULT_ROUTE_PRIORITY);
        destination.ifPresentOrElse(item::routeTo, item::clearDestination);
        return destination.flatMap(found -> exitTowards(at, found, item));
    }

    private Optional<Side> exitTowards(RouterId at, RouterId destination, TravellingItem item) {
        if (destination.equals(at)) {
            return deliverySides(at, item.batch(), item.origin()).stream().min(Comparator.naturalOrder());
        }
        if (!nodes.containsKey(destination)) {
            return Optional.empty();
        }
        Optional<Route> route = routing.route(at, destination);
        route.ifPresent(found -> item.passTo(found.nextHop()));
        return route.map(Route::exit);
    }

    // best sink for a batch that is at the given router and came in through origin
    // only sinks that want it at least as much as minPriority count
    private Optional<RouterId> findDestination(RouterId at, ItemBatch batch, Side origin, boolean mayStay, int minPriority) {
        List<SinkOffer> offers = new ArrayList<>();
        for (Map.Entry<RouterId, RouterNode> entry : nodes.entrySet()) {
            OptionalInt priority = entry.getValue().sinkPriority(batch.type());
            if (priority.isPresent() && priority.getAsInt() >= minPriority && (mayStay || !entry.getKey().equals(at))) {
                offers.add(new SinkOffer(entry.getKey(), priority.getAsInt()));
            }
        }
        // looking into inventories is the expensive part, so only the best candidates are asked, in order
        for (RouterId candidate : DestinationSelector.rank(at, offers, routing.routesFrom(at))) {
            Set<Side> sides = candidate.equals(at)
                    ? deliverySides(at, batch, origin)
                    : nodes.get(candidate).inventoriesAccepting(batch);
            if (!sides.isEmpty()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // never hand an item back to the inventory it just came out of
    private Set<Side> deliverySides(RouterId at, ItemBatch batch, Side origin) {
        RouterNode node = nodes.get(at);
        if (node == null) {
            return Set.of();
        }
        Set<Side> sides = EnumSet.noneOf(Side.class);
        sides.addAll(node.inventoriesAccepting(batch));
        sides.remove(origin);
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

    // plans the request and starts it right away if nothing is missing and there is power to move the items
    public RequestOutcome request(RouterId requester, ItemBatch wanted, long now) {
        RequestPlan plan = new RequestPlanner(stockFor(requester), craftersFor(requester)).plan(requester, wanted);
        if (!plan.complete()) {
            return new RequestOutcome(plan, true);
        }
        int items = plan.provisions().stream().mapToInt(provision -> provision.batch().count()).sum();
        if (!usePower(requester, items * ENERGY_PER_ITEM)) {
            return new RequestOutcome(plan, false);
        }
        execute(plan, now);
        return new RequestOutcome(plan, true);
    }

    // takes energy from the junctions of the network the router is in, all of it or nothing
    private boolean usePower(RouterId at, int amount) {
        List<RouterNode> sources = new ArrayList<>();
        if (nodes.containsKey(at)) {
            sources.add(nodes.get(at));
        }
        reachableFrom(at).forEach(id -> sources.add(nodes.get(id)));
        int available = 0;
        for (RouterNode source : sources) {
            available += source.drawPower(amount - available, true);
            if (available >= amount) {
                break;
            }
        }
        if (available < amount) {
            return false;
        }
        int left = amount;
        for (RouterNode source : sources) {
            left -= source.drawPower(left, false);
        }
        return true;
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

    // regular upkeep of one router: crafting pipes ship finished items, suppliers reorder, extractors push items out
    public void tick(RouterId id, long now) {
        RouterNode node = nodes.get(id);
        if (node == null) {
            return;
        }
        if (node.kind() == PipeKind.CRAFTING) {
            node.outputOrders().ship(order -> {
                // only pay for what is really there to send
                int ready = Math.min(order.batch().count(), node.contents().getOrDefault(order.batch().type(), 0));
                if (ready == 0 || !usePower(id, ready * ENERGY_PER_ITEM)) {
                    return 0;
                }
                return node.send(order.batch().type(), ready, order.destination());
            });
        } else if (node.kind() == PipeKind.SUPPLIER) {
            Map<ItemType, Integer> contents = node.contents();
            node.supplyTargets().forEach((type, wanted) -> {
                int have = contents.getOrDefault(type, 0) + pending.expected(id, type, now);
                if (have < wanted) {
                    requestUpTo(id, type, wanted - have, now);
                }
            });
        }
        if (now - lastChange >= SETTLE_TICKS) {
            node.extractions().forEach(mode -> extract(id, node, mode));
        }
    }

    // sends off the first kind of item in the inventory that some other router wants
    private void extract(RouterId id, RouterNode node, ExtractionMode mode) {
        int minPriority = mode.usesDefaultRoutes() ? ItemSink.DEFAULT_ROUTE_PRIORITY : ItemSink.DEFAULT_ROUTE_PRIORITY + 1;
        for (Map.Entry<ItemType, Integer> stock : node.contents().entrySet()) {
            ItemBatch batch = new ItemBatch(stock.getKey(), Math.min(stock.getValue(), mode.amount()));
            Optional<RouterId> destination = findDestination(id, batch, null, false, minPriority);
            if (destination.isPresent()) {
                if (usePower(id, batch.count() * ENERGY_PER_ITEM)) {
                    node.send(batch.type(), batch.count(), destination.get());
                }
                return;
            }
        }
    }

    // like request, but settles for whatever part of the amount is in stock
    private void requestUpTo(RouterId requester, ItemType type, int count, long now) {
        RequestOutcome outcome = request(requester, new ItemBatch(type, count), now);
        if (outcome.plan().complete()) {
            return;
        }
        int obtainable = count - outcome.missing().getOrDefault(type, count);
        if (obtainable > 0 && outcome.missing().size() == 1) {
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
            if (node.provides()) {
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
