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
import logisticspipes.domain.logistics.SinkBid;
import logisticspipes.domain.logistics.SinkRule;
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
    static final int REROUTE_PATIENCE = 100;
    // energy in FE, the original mod's costs at its rate of 2 RF per unit
    // sinks charge per stack on top of this, see SinkRule.energy and ExtractionMode
    public static final int ENERGY_PER_PROVIDED_ITEM = 2;
    public static final int ENERGY_PER_CRAFTED_ITEM = 20;
    public static final int ENERGY_PER_SUPPLY_ORDER = 20;
    // ticks between rounds of shipping crafted items and of suppliers checking their stock
    static final int CRAFT_INTERVAL = 5;
    static final int SUPPLY_INTERVAL = 20;
    static final int MAX_NEW_TABLES_PER_TICK = 40;

    // a sink that takes a batch and what it charges for that
    private record Claim(RouterId router, int energy) {
    }

    private final RoutingNetwork routing = new RoutingNetwork();
    private final Map<RouterId, RouterNode> nodes = new HashMap<>();
    private final PendingDeliveries pending = new PendingDeliveries();
    private final Map<RouterId, Long> changedAt = new HashMap<>();
    // newest change per group of connected routers, worked out when asked and dropped on any change
    private final Map<Integer, Long> lastChangeOfComponent = new HashMap<>();
    private long lastChangeAnywhere = Long.MIN_VALUE;
    private long tablesTick = Long.MIN_VALUE;
    private int tablesThisTick;
    // which routers take which items, so that sorting an item does not have to ask every router of a large base
    private final Map<RouterId, List<SinkRule>> sinkRules = new HashMap<>();
    private final Map<ItemType, Set<RouterId>> sinksByItem = new HashMap<>();
    private final Set<RouterId> sinksForAnything = new HashSet<>();

    public void addRouter(RouterId id, RouterNode node, long now) {
        nodes.put(id, node);
        routing.add(id);
        sinksChanged(id);
        changed(id, now);
    }

    public void removeRouter(RouterId id, long now) {
        // everything that was connected to it has to find its way again
        OptionalInt component = routing.component(id);
        if (component.isPresent()) {
            for (RouterId other : changedAt.keySet()) {
                if (routing.component(other).equals(component)) {
                    changedAt.put(other, now);
                }
            }
        }
        nodes.remove(id);
        routing.remove(id);
        pending.forget(id);
        changedAt.remove(id);
        forgetSinks(id);
        lastChangeOfComponent.clear();
        lastChangeAnywhere = now;
    }

    public void updateLinks(RouterId id, List<Link> links, long now) {
        if (routing.setLinks(id, links)) {
            changed(id, now);
        }
    }

    private void changed(RouterId id, long now) {
        changedAt.put(id, now);
        lastChangeOfComponent.clear();
        lastChangeAnywhere = now;
    }

    // whether the routers this one is connected to are still finding each other
    // pipes being built or loaded somewhere else in the world do not count
    private boolean settling(RouterId at, long now) {
        if (now == lastChangeAnywhere) {
            // when a large base loads, hundreds of routers register in one tick
            // working out who is connected to whom after each of them would freeze the game, so everyone waits for the next tick
            return true;
        }
        if (lastChangeOfComponent.isEmpty()) {
            changedAt.forEach((id, when) -> routing.component(id).ifPresent(
                    component -> lastChangeOfComponent.merge(component, when, Math::max)));
        }
        OptionalInt component = routing.component(at);
        return component.isPresent() && now - lastChangeOfComponent.getOrDefault(component.getAsInt(), Long.MIN_VALUE / 2) < SETTLE_TICKS;
    }

    // after a change every router that passes an item on needs a new route table
    // in a large base that is thousands of tables, so only so many are worked out per tick and the other items wait
    private boolean mayRouteFrom(RouterId at, long now) {
        if (routing.hasRoutesFrom(at)) {
            return true;
        }
        if (tablesTick != now) {
            tablesTick = now;
            tablesThisTick = 0;
        }
        if (tablesThisTick >= MAX_NEW_TABLES_PER_TICK) {
            return false;
        }
        tablesThisTick++;
        routing.routesFrom(at);
        return true;
    }

    // has to be called whenever what a router takes in changes: its filters, modules or default route
    public void sinksChanged(RouterId id) {
        forgetSinks(id);
        RouterNode node = nodes.get(id);
        if (node == null) {
            return;
        }
        List<SinkRule> rules = List.copyOf(node.sinkRules());
        if (rules.isEmpty()) {
            return;
        }
        sinkRules.put(id, rules);
        for (SinkRule rule : rules) {
            if (rule.takesUnlisted()) {
                sinksForAnything.add(id);
            }
            rule.wants().forEach(type -> sinksByItem.computeIfAbsent(type, key -> new HashSet<>()).add(id));
        }
    }

    private void forgetSinks(RouterId id) {
        List<SinkRule> rules = sinkRules.remove(id);
        if (rules == null) {
            return;
        }
        sinksForAnything.remove(id);
        for (SinkRule rule : rules) {
            for (ItemType type : rule.wants()) {
                Set<RouterId> sinks = sinksByItem.get(type);
                if (sinks != null) {
                    sinks.remove(id);
                    if (sinks.isEmpty()) {
                        sinksByItem.remove(type);
                    }
                }
            }
        }
    }

    // what the router bids for an item, its keenest rule counts
    private Optional<SinkBid> sinkBid(RouterId id, ItemType type) {
        List<SinkRule> rules = sinkRules.get(id);
        RouterNode node = nodes.get(id);
        if (rules == null || node == null) {
            return Optional.empty();
        }
        Map<ItemType, Integer> contents = rules.stream().anyMatch(SinkRule::needsContents) ? node.contents() : Map.of();
        return rules.stream().flatMap(rule -> rule.bid(type, contents).stream()).max(Comparator.comparingInt(SinkBid::priority));
    }

    // side an item has to leave the router through, picks a new destination if the current one does not work out
    // empty means there is nowhere to go right now, the pipe asks again for a while before it gives up
    public Optional<Side> chooseExit(RouterId at, TravellingItem item, long now) {
        RouterNode node = nodes.get(at);
        if (now == lastChangeAnywhere || !mayRouteFrom(at, now)) {
            // routes are worked out again next tick, see settling
            item.stayPatient();
            return Optional.empty();
        }
        if (item.destination().filter(at::equals).isPresent()) {
            if (item.markArrived()) {
                pending.arrived(at, item.batch().type(), item.batch().count());
            }
            if (node != null && node.kind() != PipeKind.BASIC) {
                if (!node.hasInventory()) {
                    // ordered to a pipe without an inventory, so it pops out right here
                    item.abandon();
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
        if (settling(at, now)) {
            // routers are still finding each other, a choice made now could be the wrong one
            item.stayPatient();
            return Optional.empty();
        }
        boolean lostDestination = item.destination().filter(destination -> !destination.equals(at)).isPresent();
        if (lostDestination && item.waited() < REROUTE_PATIENCE) {
            // the destination may just be in a chunk that is loading
            return Optional.empty();
        }
        // a sink only takes the item if the network can pay for it
        Optional<RouterId> destination = findDestination(at, item.batch(), item.origin(), true, ItemSink.DEFAULT_ROUTE_PRIORITY)
                .filter(claim -> usePower(at, claim.energy()))
                .map(Claim::router);
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
    private Optional<Claim> findDestination(RouterId at, ItemBatch batch, Side origin, boolean mayStay, int minPriority) {
        Set<InventoryRef> cameFrom = sourceInventories(at, origin, mayStay);
        List<SinkOffer> offers = new ArrayList<>();
        Map<RouterId, Integer> energy = new HashMap<>();
        Set<RouterId> candidates = new HashSet<>(sinksForAnything);
        candidates.addAll(sinksByItem.getOrDefault(batch.type(), Set.of()));
        for (RouterId candidate : candidates) {
            Optional<SinkBid> bid = sinkBid(candidate, batch.type());
            if (bid.isEmpty() || bid.get().priority() < minPriority) {
                continue;
            }
            boolean here = candidate.equals(at);
            // a second pipe on the inventory the item came out of would only put it straight back
            if (here ? mayStay : !sharesInventory(nodes.get(candidate), cameFrom)) {
                offers.add(new SinkOffer(candidate, bid.get().priority()));
                energy.put(candidate, bid.get().energy());
            }
        }
        // looking into inventories is the expensive part, so only the best candidates are asked, in order
        for (RouterId candidate : DestinationSelector.rank(at, offers, routing.routesFrom(at))) {
            Set<Side> sides = candidate.equals(at)
                    ? deliverySides(at, batch, origin)
                    : nodes.get(candidate).inventoriesAccepting(batch);
            if (!sides.isEmpty()) {
                return Optional.of(new Claim(candidate, energy.get(candidate)));
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
        return request(requester, wanted, now, 0);
    }

    private RequestOutcome request(RouterId requester, ItemBatch wanted, long now, int extraEnergy) {
        RequestPlan plan = new RequestPlanner(stockFor(requester), craftersFor(requester)).plan(requester, wanted);
        if (!plan.complete()) {
            return new RequestOutcome(plan, true);
        }
        int items = plan.provisions().stream().mapToInt(provision -> provision.batch().count()).sum();
        if (!usePower(requester, items * ENERGY_PER_PROVIDED_ITEM + extraEnergy)) {
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

    // upkeep of one router, called every tick: crafting pipes ship finished items, suppliers reorder,
    // extractors push items out, each at its own pace
    public void tick(RouterId id, long now) {
        RouterNode node = nodes.get(id);
        if (node == null || now == lastChangeAnywhere) {
            return;
        }
        if (node.kind() == PipeKind.CRAFTING && now % CRAFT_INTERVAL == 0) {
            node.outputOrders().ship(now, CRAFT_TIMEOUT, order -> {
                // only pay for what is really there to send
                int ready = Math.min(order.batch().count(), node.contents().getOrDefault(order.batch().type(), 0));
                if (ready == 0 || !usePower(id, ready * ENERGY_PER_CRAFTED_ITEM)) {
                    return 0;
                }
                return node.send(order.batch().type(), ready, order.destination());
            });
        } else if (node.kind() == PipeKind.SUPPLIER && now % SUPPLY_INTERVAL == 0) {
            Map<ItemType, Integer> contents = node.contents();
            node.supplyTargets().forEach((type, wanted) -> {
                int have = contents.getOrDefault(type, 0) + pending.expected(id, type, now);
                // reorders once the stock is down to half, and only if the inventory can take any of it
                if (have * 2 <= wanted && !node.inventoriesAccepting(new ItemBatch(type, wanted - have)).isEmpty()) {
                    requestUpTo(id, type, wanted - have, now);
                }
            });
        }
        if (!settling(id, now)) {
            for (ExtractionMode mode : node.extractions()) {
                if (now % mode.interval() == 0) {
                    extract(id, node, mode);
                }
            }
        }
    }

    // sends off the first kind of item in the inventory that some other router wants and the network can pay for
    private void extract(RouterId id, RouterNode node, ExtractionMode mode) {
        int minPriority = mode.usesDefaultRoutes() ? ItemSink.DEFAULT_ROUTE_PRIORITY : ItemSink.DEFAULT_ROUTE_PRIORITY + 1;
        int amount = mode.amount() * node.extractionBoost();
        for (Map.Entry<ItemType, Integer> stock : node.contents().entrySet()) {
            ItemBatch batch = new ItemBatch(stock.getKey(), Math.min(stock.getValue(), amount));
            Optional<Claim> claim = findDestination(id, batch, null, false, minPriority);
            if (claim.isPresent()) {
                if (usePower(id, claim.get().energy() + mode.energyFor(batch.count()))) {
                    node.send(batch.type(), batch.count(), claim.get().router());
                }
                return;
            }
        }
    }

    // like request, but settles for whatever part of the amount is in stock when not all of it can be had
    private void requestUpTo(RouterId requester, ItemType type, int count, long now) {
        if (request(requester, new ItemBatch(type, count), now, ENERGY_PER_SUPPLY_ORDER).plan().complete()) {
            return;
        }
        int inStock = stockFor(requester).stream().mapToInt(stock -> stock.items().getOrDefault(type, 0)).sum();
        if (inStock > 0) {
            request(requester, new ItemBatch(type, Math.min(count, inStock)), now, ENERGY_PER_SUPPLY_ORDER);
        }
    }

    // nearest first
    private List<RouterId> reachableFrom(RouterId requester) {
        return routing.routesFrom(requester).byDistance();
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
