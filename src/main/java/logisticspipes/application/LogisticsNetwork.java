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
import java.util.function.Predicate;

import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.Cleanup;
import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.DestinationSelector;
import logisticspipes.domain.logistics.Extraction;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.FirewallRule;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkBid;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.logistics.Supply;
import logisticspipes.domain.logistics.SinkOffer;
import logisticspipes.domain.request.CraftJob;
import logisticspipes.domain.request.Crafter;
import logisticspipes.domain.request.Provision;
import logisticspipes.domain.request.RequestPlan;
import logisticspipes.domain.request.RequestPlanner;
import logisticspipes.domain.request.Stock;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.Route;
import logisticspipes.domain.routing.RouteTable;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.RoutingNetwork;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.statistics.PipeStatistics;
import logisticspipes.domain.transport.TransportMode;
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
    // a supplier filling all the room it has orders no more than this at a time
    static final int SUPPLY_ROOM_CHECK = 64;
    static final int CLEANUP_AMOUNT = 64;

    // a sink that takes a batch and what it charges for that
    private record Claim(RouterId router, int energy, int maxItems) {
    }

    private final RoutingNetwork routing = new RoutingNetwork();
    // for firewalls: the links as they were reported, the firewalls, and the routes that lead out of none of them
    private final Map<RouterId, List<Link>> links = new HashMap<>();
    private final Set<RouterId> firewalls = new HashSet<>();
    private RoutingNetwork open;
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
    private final FluidLogistics fluids = new FluidLogistics(this);

    public FluidLogistics fluids() {
        return fluids;
    }

    // null for a router that is not there
    public RouterNode node(RouterId id) {
        return nodes.get(id);
    }

    RouteTable routes(RouterId from) {
        return routing.routesFrom(from);
    }

    public void addRouter(RouterId id, RouterNode node, long now) {
        nodes.put(id, node);
        routing.add(id);
        if (node.kind() == PipeKind.FIREWALL) {
            firewalls.add(id);
        }
        open = null;
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
        links.remove(id);
        firewalls.remove(id);
        open = null;
        pending.forget(id);
        fluids.forget(id);
        changedAt.remove(id);
        forgetSinks(id);
        lastChangeOfComponent.clear();
        lastChangeAnywhere = now;
    }

    public void updateLinks(RouterId id, List<Link> links, long now) {
        this.links.put(id, List.copyOf(links));
        open = null;
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
    private Optional<SinkBid> sinkBid(RouterId id, ItemType type, long now) {
        List<SinkRule> rules = sinkRules.get(id);
        RouterNode node = nodes.get(id);
        if (rules == null || node == null) {
            return Optional.empty();
        }
        Map<ItemType, Integer> contents = Map.of();
        if (rules.stream().anyMatch(SinkRule::needsContents)) {
            // what is already on its way here counts as if it were in the inventory
            contents = new HashMap<>(node.contents());
            contents.merge(type, pending.expected(id, type, now), Integer::sum);
        }
        Map<ItemType, Integer> seen = contents;
        return rules.stream().flatMap(rule -> rule.bid(type, seen).stream()).max(Comparator.comparingInt(SinkBid::priority));
    }

    // side an item has to leave the router through, picks a new destination if the current one does not work out
    // empty means there is nowhere to go right now, the pipe asks again for a while before it gives up
    public Optional<Side> chooseExit(RouterId at, TravellingItem item, long now) {
        Optional<Side> exit = exitFor(at, item, now);
        RouterNode node = nodes.get(at);
        if (node != null) {
            count(at, node, item, exit.isPresent());
        }
        return exit;
    }

    // an item that found its exit goes into the statistics of the pipe: delivered here, taken out of the inventory
    // of this pipe, or just passing through
    private static void count(RouterId at, RouterNode node, TravellingItem item, boolean leaves) {
        int items = item.batch().count();
        if (item.destination().filter(at::equals).isPresent()) {
            // an order for a pipe without an inventory drops out here, that is an arrival too
            if ((leaves || item.abandoned()) && item.markReceived()) {
                node.statistics().count(PipeStatistics.Kind.RECEIVED, items);
            }
        } else if (leaves) {
            boolean ownInventory = node.inventories().containsKey(item.origin());
            node.statistics().count(ownInventory ? PipeStatistics.Kind.SENT : PipeStatistics.Kind.RELAYED, items);
        }
    }

    private Optional<Side> exitFor(RouterId at, TravellingItem item, long now) {
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
            if (item.mode() == TransportMode.REQUESTED) {
                if (node != null && !node.hasInventory()) {
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
            // a sink is asked again on arrival, it may have filled up or want fewer by now
            Optional<SinkBid> bid = sinkBid(at, item.batch().type(), now);
            if (bid.isEmpty()) {
                item.clearDestination();
            } else if (bid.get().maxItems() > 0) {
                item.keepOnly(bid.get().maxItems());
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
        Optional<Claim> claim = findDestination(at, item.batch(), item.origin(), true, ItemSink.DEFAULT_ROUTE_PRIORITY, now)
                .filter(found -> usePower(at, found.energy()));
        if (claim.isEmpty()) {
            item.clearDestination();
            return Optional.empty();
        }
        // a sink that wants fewer gets fewer, the rest stays behind to find another one
        item.keepOnly(claim.get().maxItems());
        expectCapped(claim.get(), item.batch(), now);
        item.routeTo(claim.get().router());
        return exitTowards(at, claim.get().router(), item);
    }

    private Optional<Side> exitTowards(RouterId at, RouterId destination, TravellingItem item) {
        if (destination.equals(at)) {
            return deliverySides(at, item.batch(), item.origin()).stream().min(Comparator.naturalOrder());
        }
        if (!nodes.containsKey(destination)) {
            return Optional.empty();
        }
        Optional<Route> route = routing.route(at, destination);
        ItemType type = item.batch().type();
        boolean ordered = item.mode() == TransportMode.REQUESTED;
        if (route.isPresent() && stoppedOnTheWay(at, destination, rule -> ordered ? rule.stopsOrdered(type) : rule.stopsSorted(type))) {
            // a firewall is in the way, so the item takes a way around it if there is one
            route = open().route(at, destination);
        }
        route.ifPresent(found -> item.passTo(found.nextHop()));
        return route.map(Route::exit);
    }

    // the same network with nothing leading out of a firewall, so its routes are the ones no firewall has a say in
    private RoutingNetwork open() {
        if (open == null) {
            open = new RoutingNetwork();
            nodes.keySet().forEach(open::add);
            links.forEach((id, own) -> open.setLinks(id, firewalls.contains(id) ? List.of() : own));
        }
        return open;
    }

    // whether a firewall on the shortest way stops what the test describes
    private boolean stoppedOnTheWay(RouterId from, RouterId to, Predicate<FirewallRule> stops) {
        if (firewalls.isEmpty()) {
            return false;
        }
        RouterId at = from;
        for (int hops = 0; hops < nodes.size() && !at.equals(to); hops++) {
            if (firewalls.contains(at) && nodes.get(at).firewall().filter(stops).isPresent()) {
                return true;
            }
            Optional<Route> route = routing.route(at, to);
            if (route.isEmpty()) {
                return false;
            }
            at = route.get().nextHop();
        }
        return false;
    }

    // a firewall only counts when there is no way around it
    private boolean passes(RouterId from, RouterId to, Predicate<FirewallRule> stops) {
        return !stoppedOnTheWay(from, to, stops) || open().route(from, to).isPresent();
    }

    // best sink for a batch that is at the given router and came in through origin
    // only sinks that want it at least as much as minPriority count
    private Optional<Claim> findDestination(RouterId at, ItemBatch batch, Side origin, boolean mayStay, int minPriority,
            long now) {
        if (batch.type().packedFluid()) {
            // fluid pipes look after it, see FluidLogistics
            return Optional.empty();
        }
        Set<InventoryRef> cameFrom = sourceInventories(at, origin, mayStay);
        List<SinkOffer> offers = new ArrayList<>();
        Map<RouterId, SinkBid> bids = new HashMap<>();
        Set<RouterId> candidates = new HashSet<>(sinksForAnything);
        candidates.addAll(sinksByItem.getOrDefault(batch.type(), Set.of()));
        for (RouterId candidate : candidates) {
            Optional<SinkBid> bid = sinkBid(candidate, batch.type(), now);
            if (bid.isEmpty() || bid.get().priority() < minPriority
                    || !passes(at, candidate, rule -> rule.stopsSorted(batch.type()))) {
                continue;
            }
            boolean here = candidate.equals(at);
            // a second pipe on the inventory the item came out of would only put it straight back
            if (here ? mayStay : !sharesInventory(nodes.get(candidate), cameFrom)) {
                offers.add(new SinkOffer(candidate, bid.get().priority()));
                bids.put(candidate, bid.get());
            }
        }
        // looking into inventories is the expensive part, so only the best candidates are asked, in order
        for (RouterId candidate : DestinationSelector.rank(at, offers, routing.routesFrom(at))) {
            Set<Side> sides = candidate.equals(at)
                    ? deliverySides(at, batch, origin)
                    : nodes.get(candidate).inventoriesAccepting(batch);
            if (!sides.isEmpty()) {
                return Optional.of(new Claim(candidate, bids.get(candidate).energy(), bids.get(candidate).maxItems()));
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

    // how many of each item the network of the router holds for the taking, its own inventory included
    public Map<ItemType, Integer> amounts(RouterId at) {
        Map<ItemType, Integer> amounts = new LinkedHashMap<>();
        for (Stock stock : stockFor(at, true)) {
            stock.items().forEach((type, count) -> amounts.merge(type, count, Integer::sum));
        }
        return amounts;
    }

    // what the crafters in the network of the router still owe, by item
    public Map<ItemType, Integer> craftingBacklog(RouterId at) {
        Map<ItemType, Integer> backlog = new LinkedHashMap<>();
        List<RouterId> routers = new ArrayList<>(reachableFrom(at));
        if (nodes.containsKey(at)) {
            routers.add(0, at);
        }
        for (RouterId id : routers) {
            nodes.get(id).outputOrders().owedByType().forEach((type, count) -> backlog.merge(type, count, Integer::sum));
        }
        return backlog;
    }

    // plans the request and starts it right away if nothing is missing and there is power to move the items
    public RequestOutcome request(RouterId requester, ItemBatch wanted, long now) {
        return request(requester, wanted, now, 0, false);
    }

    // orders what the requester needs beyond what it has and what is already on its way to it, all of it or nothing
    // with simulate nothing is ordered and no power is used, the outcome only tells what would be missing
    public RequestOutcome requestShortfall(RouterId requester, Map<ItemType, Integer> needed, Map<ItemType, Integer> have,
            long now, boolean simulate) {
        List<ItemBatch> wanted = new ArrayList<>();
        needed.forEach((type, count) -> {
            int missing = count - have.getOrDefault(type, 0) - pending.expected(requester, type, now);
            if (missing > 0) {
                wanted.add(new ItemBatch(type, missing));
            }
        });
        return request(requester, wanted, now, 0, false, simulate);
    }

    private RequestOutcome request(RouterId requester, ItemBatch wanted, long now, int extraEnergy, boolean partial) {
        return request(requester, List.of(wanted), now, extraEnergy, partial, false);
    }

    // with partial set, whatever part of the order can be had is started
    private RequestOutcome request(RouterId requester, List<ItemBatch> wanted, long now, int extraEnergy, boolean partial,
            boolean simulate) {
        RequestPlan plan = new RequestPlanner(stockFor(requester), craftersFor(requester)).plan(requester, wanted);
        boolean nothing = plan.provisions().isEmpty() && plan.crafts().isEmpty();
        if (simulate || !plan.complete() && (!partial || nothing)) {
            return new RequestOutcome(plan, true);
        }
        int items = plan.provisions().stream().mapToInt(provision -> provision.batch().count()).sum();
        if (!usePower(requester, items * ENERGY_PER_PROVIDED_ITEM + extraEnergy)) {
            return new RequestOutcome(plan, false);
        }
        execute(plan, now);
        return new RequestOutcome(plan, true);
    }

    // whether some sink would take the batch if it were put into the pipe
    public boolean wanted(RouterId at, ItemBatch batch, long now) {
        return findDestination(at, batch, null, false, ItemSink.DEFAULT_ROUTE_PRIORITY, now).isPresent();
    }

    // an ordered item fell out of the pipes on its way, a crafter orders its ingredients again
    public void itemLost(RouterId destination, ItemBatch batch, long now) {
        pending.arrived(destination, batch.type(), batch.count());
        RouterNode node = nodes.get(destination);
        if (node != null && (node.kind() == PipeKind.SATELLITE || !node.crafts().isEmpty())) {
            requestUpTo(destination, batch.type(), batch.count(), now);
        }
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
    public boolean usePower(RouterId at, int amount) {
        List<RouterNode> sources = new ArrayList<>();
        if (nodes.containsKey(at)) {
            sources.add(nodes.get(at));
        }
        reachableFrom(at).stream().filter(id -> passes(at, id, FirewallRule::blockPower)).forEach(id -> sources.add(nodes.get(id)));
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
            // spare output of a crafting pipe is not held back by any provider setting
            int sent = provider.provides()
                    ? provider.provide(type, provision.batch().count(), provision.destination())
                    : provider.send(type, provision.batch().count(), provision.destination());
            pending.expect(provision.destination(), type, sent, now + PROVISION_TIMEOUT);
        }
        for (CraftJob craft : plan.crafts()) {
            RouterNode crafter = nodes.get(craft.crafter());
            if (crafter == null) {
                continue;
            }
            crafter.outputOrders().add(new OutputOrder(craft.destination(), craft.output(), now));
            // what else comes out with every run is expected too, and sent off as it appears
            for (CraftingSetup setup : crafter.crafts()) {
                if (setup.recipe().output().type().equals(craft.output().type()) && setup.byproduct().isPresent()) {
                    int perRun = setup.recipe().output().count();
                    int runs = (craft.output().count() + perRun - 1) / perRun;
                    ItemBatch extra = setup.byproduct().get();
                    crafter.outputOrders().add(OutputOrder.extra(new ItemBatch(extra.type(), extra.count() * runs), now));
                    break;
                }
            }
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
        if (!node.outputOrders().isEmpty() && now % CRAFT_INTERVAL == 0) {
            node.outputOrders().ship(now, CRAFT_TIMEOUT, order -> {
                // only pay for what is really there to send
                int ready = Math.min(order.batch().count(), node.contents().getOrDefault(order.batch().type(), 0));
                if (ready == 0 || !usePower(id, ready * ENERGY_PER_CRAFTED_ITEM)) {
                    return 0;
                }
                return node.send(order.batch().type(), ready, order.destination(),
                        order.isExtra() ? TransportMode.UNCLAIMED : TransportMode.REQUESTED);
            });
        } else if (now % CRAFT_INTERVAL == 0) {
            node.cleanup().ifPresent(cleanup -> cleanUp(id, node, cleanup));
        }
        if (now % SUPPLY_INTERVAL == 0) {
            for (Supply supply : node.supplies()) {
                supply(id, node, supply, now);
            }
        }
        if (!settling(id, now)) {
            for (Extraction extraction : node.extractions()) {
                if (now % extraction.interval() == 0) {
                    extract(id, node, extraction, now);
                }
            }
        }
    }

    // a stack of the first thing in the machine that should not be there goes off to whoever takes it
    private void cleanUp(RouterId id, RouterNode node, Cleanup cleanup) {
        for (Map.Entry<ItemType, Integer> stock : node.contents().entrySet()) {
            if (cleanup.removes(stock.getKey())) {
                int count = Math.min(CLEANUP_AMOUNT, stock.getValue());
                if (usePower(id, count * ENERGY_PER_CRAFTED_ITEM)) {
                    node.send(stock.getKey(), count, null, TransportMode.UNCLAIMED);
                }
                return;
            }
        }
    }

    private void supply(RouterId id, RouterNode node, Supply supply, long now) {
        Map<ItemType, Integer> contents = node.contents();
        supply.targets().forEach((type, wanted) -> {
            int amount = supply.mode().amount(wanted, contents.getOrDefault(type, 0), pending.expected(id, type, now),
                    node.roomFor(type, SUPPLY_ROOM_CHECK));
            if (amount > 0 && supply.mode().allOrNothing()) {
                request(id, new ItemBatch(type, amount), now, ENERGY_PER_SUPPLY_ORDER, false);
            } else if (amount > 0) {
                requestUpTo(id, type, amount, now);
            }
        });
    }

    // sends off the first kinds of items in the inventory that some other router wants and the network can pay for
    private void extract(RouterId id, RouterNode node, Extraction extraction, long now) {
        ExtractionMode mode = extraction.mode();
        int minPriority = mode.usesDefaultRoutes() ? ItemSink.DEFAULT_ROUTE_PRIORITY : ItemSink.DEFAULT_ROUTE_PRIORITY + 1;
        int itemsLeft = extraction.amount();
        int stacksLeft = extraction.stacks();
        for (Map.Entry<ItemType, Integer> stock : node.contents().entrySet()) {
            if (!extraction.takes(stock.getKey())) {
                continue;
            }
            ItemBatch batch = new ItemBatch(stock.getKey(), Math.min(stock.getValue(), itemsLeft));
            Optional<Claim> claim = findDestination(id, batch, null, false, minPriority, now);
            if (claim.isEmpty()) {
                continue;
            }
            int count = claim.get().maxItems() > 0 ? Math.min(batch.count(), claim.get().maxItems()) : batch.count();
            if (!usePower(id, claim.get().energy() + extraction.energyFor(count))) {
                return;
            }
            int sent = node.send(batch.type(), count, claim.get().router(), TransportMode.SORTED);
            expectCapped(claim.get(), new ItemBatch(batch.type(), sent), now);
            itemsLeft -= sent;
            if (--stacksLeft == 0 || itemsLeft <= 0) {
                return;
            }
        }
    }

    // a sink that counts what it gets has to know what is on its way, or it would take too much
    private void expectCapped(Claim claim, ItemBatch batch, long now) {
        if (claim.maxItems() > 0) {
            pending.expect(claim.router(), batch.type(), batch.count(), now + PROVISION_TIMEOUT);
        }
    }

    // like request, but settles for whatever part of the amount can be had
    private void requestUpTo(RouterId requester, ItemType type, int count, long now) {
        request(requester, new ItemBatch(type, count), now, ENERGY_PER_SUPPLY_ORDER, true);
    }

    // nearest first
    private List<RouterId> reachableFrom(RouterId requester) {
        return routing.routesFrom(requester).byDistance();
    }

    // what can be ordered right now: the inventories of providers, and finished items in crafting inventories
    // that are not owed to anyone
    private List<Stock> stockFor(RouterId requester) {
        return stockFor(requester, false);
    }

    // with ownToo what is at the requester itself counts as well, for looking at the network and not ordering from it
    private List<Stock> stockFor(RouterId requester, boolean ownToo) {
        List<Stock> stock = new ArrayList<>();
        // an inventory with two providers on it counts once, and never the requester's own
        Set<InventoryRef> counted = new HashSet<>();
        List<RouterId> routers = new ArrayList<>(reachableFrom(requester));
        if (nodes.containsKey(requester) && ownToo) {
            routers.add(0, requester);
        } else if (nodes.containsKey(requester)) {
            counted.addAll(nodes.get(requester).inventories().values());
        }
        for (RouterId id : routers) {
            RouterNode node = nodes.get(id);
            if (node.provides()) {
                if (!sharesInventory(node, counted) && passes(requester, id, FirewallRule::blockProviding)) {
                    Map<ItemType, Integer> provided = new LinkedHashMap<>(node.provided());
                    if (!firewalls.isEmpty()) {
                        provided.keySet().removeIf(type -> !passes(requester, id, rule -> rule.rejects(type)));
                    }
                    stock.add(new Stock(id, provided));
                    counted.addAll(node.inventories().values());
                }
            } else {
                Map<ItemType, Integer> spare = new LinkedHashMap<>();
                for (CraftingSetup craft : node.crafts()) {
                    ItemType made = craft.recipe().output().type();
                    int left = node.contents().getOrDefault(made, 0) - node.outputOrders().owed(made);
                    if (left > 0) {
                        spare.put(made, left);
                    }
                }
                if (!spare.isEmpty()) {
                    stock.add(new Stock(id, spare));
                }
            }
        }
        return stock;
    }

    private record Candidate(Crafter crafter, int priority) {
    }

    // crafters that can work for the requester: higher priority first, then the ones with the least to do
    private List<Crafter> craftersFor(RouterId requester) {
        List<RouterId> reachable = reachableFrom(requester);
        List<Candidate> candidates = new ArrayList<>();
        for (RouterId id : reachable) {
            RouterNode node = nodes.get(id);
            for (CraftingSetup craft : node.crafts()) {
                ItemType made = craft.recipe().output().type();
                if (!passes(requester, id, rule -> rule.blockCrafting() || rule.rejects(made))) {
                    continue;
                }
                int backlog = node.outputOrders().owedInTotal();
                Map<RouterId, Map<ItemType, Integer>> forSatellites = new LinkedHashMap<>();
                boolean allFound = true;
                for (Map.Entry<Integer, List<ItemBatch>> entry : craft.bySatellite().entrySet()) {
                    Optional<RouterId> found = satellite(reachable, entry.getKey());
                    allFound &= found.isPresent();
                    found.ifPresent(satellite -> entry.getValue().forEach(input -> forSatellites
                            .computeIfAbsent(satellite, key -> new LinkedHashMap<>()).merge(input.type(), input.count(), Integer::sum)));
                }
                // without its satellites the pipe cannot get its ingredients to the right place
                if (allFound) {
                    candidates.add(new Candidate(Crafter.of(id, craft.recipe(), forSatellites, backlog).withPriority(craft.priority()),
                            craft.priority()));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::priority).reversed()
                .thenComparingInt(candidate -> candidate.crafter().backlog()));
        return candidates.stream().map(Candidate::crafter).toList();
    }

    private Optional<RouterId> satellite(List<RouterId> reachable, int satelliteId) {
        return reachable.stream()
                .filter(id -> nodes.get(id).kind() == PipeKind.SATELLITE && nodes.get(id).satelliteId() == satelliteId)
                .findFirst();
    }
}
