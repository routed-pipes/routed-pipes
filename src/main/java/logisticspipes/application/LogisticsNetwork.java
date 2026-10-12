package logisticspipes.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
import logisticspipes.domain.logistics.InventoryRef;
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
    // how long an item waits for a destination that has gone missing before it is sent somewhere else
    static final int REROUTE_PATIENCE = 20;
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
        RouterNode node = nodes.get(at);
        if (item.destination().filter(at::equals).isPresent()) {
            if (item.markArrived()) {
                pending.arrived(at, item.batch().type(), item.batch().count());
            }
            if (node != null && node.kind() != PipeKind.BASIC) {
                if (!node.hasInventory()) {
                    // ordered to a pipe without an inventory, so it pops out right here
                    return Optional.empty();
                }
                Optional<Side> side = deliverySides(at, item.batch(), item.origin()).stream().min(Comparator.naturalOrder());
                if (side.isEmpty()) {
                    // ordered here and there is no room yet, like ore queueing for a furnace: it waits in the pipe
                    item.stayPatient();
                }
                return side;
            }
        }
        Optional<Side> exit = item.destination().flatMap(destination -> exitTowards(at, destination, item));
        if (exit.isPresent()) {
            return exit;
        }
        if (now - lastChange < SETTLE_TICKS) {
            // routers are still finding each other, a choice made now could be the wrong one
            item.stayPatient();
            return Optional.empty();
        }
        boolean lostDestination = item.destination().filter(destination -> !destination.equals(at)).isPresent();
        if (lostDestination && item.waited() < REROUTE_PATIENCE) {
            // the destination may just be in a chunk that is loading
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
        Set<InventoryRef> cameFrom = sourceInventories(at, origin, mayStay);
        List<SinkOffer> offers = new ArrayList<>();
        for (Map.Entry<RouterId, RouterNode> entry : nodes.entrySet()) {
            OptionalInt priority = entry.getValue().sinkPriority(batch.type());
            if (priority.isEmpty() || priority.getAsInt() < minPriority) {
                continue;
            }
            boolean here = entry.getKey().equals(at);
            // a second pipe on the inventory the item came out of would only put it straight back
            if (here ? mayStay : !sharesInventory(entry.getValue(), cameFrom)) {
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

    // the inventories an item at this router came out of: the one on its origin side, or for extraction all of them
    private Set<InventoryRef> sourceInventories(RouterId at, Side origin, boolean passingThrough) {
        RouterNode node = nodes.get(at);
        if (node == null) {
            return Set.of();
        }
        Map<Side, InventoryRef> inventories = node.inventories();
        if (!passingThrough) {
            return Set.copyOf(inventories.values());
        }
        return origin != null && inventories.containsKey(origin) ? Set.of(inventories.get(origin)) : Set.of();
    }

    private static boolean sharesInventory(RouterNode node, Set<InventoryRef> inventories) {
        return !inventories.isEmpty() && node.inventories().values().stream().anyMatch(inventories::contains);
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
            available.putIfAbsent(crafter.output().type(), 0);
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

    public RouterStatus status(RouterId id) {
        List<RouterId> reachable = reachableFrom(id);
        long energy = nodes.containsKey(id) ? nodes.get(id).drawPower(Integer.MAX_VALUE, true) : 0;
        for (RouterId other : reachable) {
            energy += nodes.get(other).drawPower(Integer.MAX_VALUE, true);
        }
        return new RouterStatus(reachable.size(), (int) Math.min(energy, Integer.MAX_VALUE));
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
            crafter.outputOrders().add(new OutputOrder(craft.destination(), craft.output(), now));
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
            node.outputOrders().ship(now, CRAFT_TIMEOUT, order -> {
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
                // reorders once the stock is down to half, and only if the inventory can take any of it
                if (have * 2 <= wanted && !node.inventoriesAccepting(new ItemBatch(type, wanted - have)).isEmpty()) {
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

    // like request, but settles for whatever part of the amount is in stock when not all of it can be had
    private void requestUpTo(RouterId requester, ItemType type, int count, long now) {
        if (request(requester, new ItemBatch(type, count), now).plan().complete()) {
            return;
        }
        int inStock = stockFor(requester).stream().mapToInt(stock -> stock.items().getOrDefault(type, 0)).sum();
        if (inStock > 0) {
            request(requester, new ItemBatch(type, Math.min(count, inStock)), now);
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

    // what can be ordered right now: the inventories of providers, and finished items in crafting inventories
    // that are not owed to anyone
    private List<Stock> stockFor(RouterId requester) {
        List<Stock> stock = new ArrayList<>();
        // an inventory with two providers on it counts once, and never the requester's own
        Set<InventoryRef> counted = new HashSet<>();
        if (nodes.containsKey(requester)) {
            counted.addAll(nodes.get(requester).inventories().values());
        }
        for (RouterId id : reachableFrom(requester)) {
            RouterNode node = nodes.get(id);
            if (node.provides()) {
                if (!sharesInventory(node, counted)) {
                    stock.add(new Stock(id, node.contents()));
                    counted.addAll(node.inventories().values());
                }
            } else if (node.kind() == PipeKind.CRAFTING && node.recipe().isPresent()) {
                ItemType made = node.recipe().get().output().type();
                int spare = node.contents().getOrDefault(made, 0) - node.outputOrders().owed(made);
                if (spare > 0) {
                    stock.add(new Stock(id, Map.of(made, spare)));
                }
            }
        }
        return stock;
    }

    // crafting pipes that can work for the requester, the ones with the least to do first
    private List<Crafter> craftersFor(RouterId requester) {
        List<RouterId> reachable = reachableFrom(requester);
        List<Crafter> crafters = new ArrayList<>();
        for (RouterId id : reachable) {
            RouterNode node = nodes.get(id);
            if (node.kind() != PipeKind.CRAFTING || node.recipe().isEmpty()) {
                continue;
            }
            int backlog = node.outputOrders().owedInTotal();
            if (node.satelliteId() == 0) {
                crafters.add(Crafter.of(id, node.recipe().get(), null, Map.of(), backlog));
                continue;
            }
            Map<ItemType, Integer> forSatellite = new LinkedHashMap<>();
            node.satelliteInputs().forEach(input -> forSatellite.merge(input.type(), input.count(), Integer::sum));
            // without its satellite the pipe cannot get its ingredients to the right place
            satellite(reachable, node.satelliteId()).ifPresent(found ->
                    crafters.add(Crafter.of(id, node.recipe().get(), found, forSatellite, backlog)));
        }
        crafters.sort(Comparator.comparingInt(Crafter::backlog));
        return crafters;
    }

    private Optional<RouterId> satellite(List<RouterId> reachable, int satelliteId) {
        return reachable.stream()
                .filter(id -> nodes.get(id).kind() == PipeKind.SATELLITE && nodes.get(id).satelliteId() == satelliteId)
                .findFirst();
    }
}
