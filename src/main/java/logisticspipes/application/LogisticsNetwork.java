package logisticspipes.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import logisticspipes.application.port.PowerSource;
import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.Cleanup;
import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.FluidInput;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.fluid.FluidBatch;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.logistics.BulkStorageSink;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.DestinationSelector;
import logisticspipes.domain.logistics.Extraction;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.FirewallRule;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PatternSupply;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkBid;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.logistics.Supply;
import logisticspipes.domain.logistics.SinkOffer;
import logisticspipes.domain.request.CraftJob;
import logisticspipes.domain.request.Crafter;
import logisticspipes.domain.request.FuzzyMatch;
import logisticspipes.domain.request.Provision;
import logisticspipes.domain.request.RequestPlan;
import logisticspipes.domain.request.RequestPlanner;
import logisticspipes.domain.request.RequestSummary;
import logisticspipes.domain.request.Stock;
import logisticspipes.domain.routing.ConnectorLinks;
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
    public static final int ENERGY_PER_SYSTEM_ENTRY = 10;
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
    // per set of firewalls that say no: the network without the ways out of those, built when first needed
    private final Map<Set<RouterId>, RoutingNetwork> detours = new HashMap<>();
    // providers that handed out less than planned, left out while the rest is ordered elsewhere
    private final Set<RouterId> cameShort = new HashSet<>();
    // for inventory system connectors: who is on which channel, and the network as it is without their links
    private final ConnectorLinks connectors = new ConnectorLinks();
    private RoutingNetwork wired;
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
    private final BulkStorage bulkStorage = new BulkStorage();
    private final FluidLogistics fluids = new FluidLogistics(this);
    private final SubSystemPower subSystemPower = new SubSystemPower(this);
    // how long the answer to which groups of routers have energy is good for, in ticks
    public static final int POWER_LOOK_INTERVAL = 20;
    private final Set<Integer> poweredComponents = new HashSet<>();
    // the energy stores of every group of connected routers, forgotten when the network changes and with every look
    private final Map<Integer, List<PowerSource>> powerSourcesOf = new HashMap<>();
    private long powerLookedAt = Long.MIN_VALUE / 2;
    private long powerLookedAfter = Long.MIN_VALUE / 2;

    public FluidLogistics fluids() {
        return fluids;
    }

    public SubSystemPower subSystemPower() {
        return subSystemPower;
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
        detours.clear();
        powerSourcesOf.clear();
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
        detours.clear();
        powerSourcesOf.clear();
        wired = null;
        relink(connectors.remove(id), now);
        pending.forget(id);
        fluids.forget(id);
        subSystemPower.forget(id);
        changedAt.remove(id);
        forgetSinks(id);
        bulkStorage.forget(id);
        lastChangeOfComponent.clear();
        lastChangeAnywhere = now;
    }

    public void updateLinks(RouterId id, List<Link> links, long now) {
        this.links.put(id, List.copyOf(links));
        detours.clear();
        powerSourcesOf.clear();
        wired = null;
        if (routing.setLinks(id, withConnectors(id, links))) {
            changed(id, now);
        }
    }

    // a connector joined or left a channel, or the inventory beside it or its resistance changed
    // channel is null for one that is on none, inventory for one that touches none
    public void setConnector(RouterId id, UUID channel, Side inventory, int resistance, long now) {
        relink(connectors.set(id, channel, inventory, resistance), now);
    }

    public ConnectorLinks.State connectorState(RouterId id) {
        return connectors.state(id);
    }

    // whether what the first connector puts into the inventory on that side is picked up by the second
    public boolean connectorsLinked(RouterId from, Side exit, RouterId to) {
        return connectors.linked(from, exit, to);
    }

    private List<Link> withConnectors(RouterId id, List<Link> pipes) {
        List<Link> viaChannel = connectors.linksOf(id);
        if (viaChannel.isEmpty()) {
            return pipes;
        }
        List<Link> all = new ArrayList<>(pipes);
        all.addAll(viaChannel);
        return all;
    }

    // works out the links of connectors again after something about their channel changed
    private void relink(Set<RouterId> touched, long now) {
        for (RouterId id : touched) {
            if (nodes.containsKey(id)) {
                updateLinks(id, links.getOrDefault(id, List.of()), now);
            }
        }
    }

    // power does not cross the inventory between two connectors, so this asks for a way through pipes alone
    private boolean wired(RouterId from, RouterId to) {
        if (connectors.isEmpty()) {
            return true;
        }
        if (wired == null) {
            wired = new RoutingNetwork();
            nodes.keySet().forEach(wired::add);
            links.forEach(wired::setLinks);
        }
        return wired.component(from).equals(wired.component(to));
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
        bulkStorage.forget(id);
        RouterNode node = nodes.get(id);
        if (node == null) {
            return;
        }
        List<SinkRule> rules = List.copyOf(node.sinkRules());
        bulkStorage.rulesChanged(id, rules);
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
        if (bulkStorage.holding(type).contains(id) && bulkStorageAt(id)) {
            // the index says it holds the item, so there is no need to look, see BulkStorage
            return Optional.of(BulkStorageSink.STORED);
        }
        Map<ItemType, Integer> contents = Map.of();
        if (rules.stream().anyMatch(SinkRule::needsContents)) {
            // what is already on its way here counts as if it were in the inventory
            contents = new HashMap<>(node.held());
            contents.merge(type, pending.expected(id, type, now), Integer::sum);
        }
        Map<ItemType, Integer> seen = contents;
        return rules.stream().flatMap(rule -> rule.bid(type, seen).stream()).max(Comparator.comparingInt(SinkBid::priority));
    }

    // whether a bulk storage sink module is in the network of the router
    private boolean bulkStorageAt(RouterId id) {
        if (bulkStorage.modules().isEmpty()) {
            return false;
        }
        OptionalInt component = routing.component(id);
        return component.isPresent()
                && bulkStorage.modules().stream().anyMatch(module -> routing.component(module).equals(component));
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
                // an ordered item may go back where it came from: a pipe crafting an ingredient of its own other recipe
                // takes it out of the machine and puts it in again
                Optional<Side> side = node == null ? Optional.empty()
                        : node.inventoriesAccepting(item.batch()).stream().min(Comparator.naturalOrder());
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
        Optional<Side> paired = throughEntrance(at, node, item);
        if (paired.isPresent()) {
            return paired;
        }
        // a sink only takes the item if the network can pay for it
        if (lostDestination) {
            // waited long enough, whoever ordered it gets told and it is sorted like anything else
            item.giveUp();
        }
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

    // a system entrance sends what has no destination to the nearest destination pipe with the same frequency card
    // empty when this is no entrance, no such pipe can be reached or there is no power to send the item
    private Optional<Side> throughEntrance(RouterId at, RouterNode node, TravellingItem item) {
        ItemType type = item.batch().type();
        if (node == null || node.kind() != PipeKind.SYSTEM_ENTRANCE || node.frequency().isEmpty() || type.packedFluid()) {
            return Optional.empty();
        }
        for (RouterId id : reachableFrom(at)) {
            RouterNode other = nodes.get(id);
            if (other.kind() == PipeKind.SYSTEM_DESTINATION && other.frequency().equals(node.frequency())
                    && passes(at, id, rule -> rule.stopsOrdered(type))) {
                if (!usePower(at, ENERGY_PER_SYSTEM_ENTRY)) {
                    return Optional.empty();
                }
                item.orderTo(id);
                return exitTowards(at, id, item);
            }
        }
        return Optional.empty();
    }

    private Optional<Side> exitTowards(RouterId at, RouterId destination, TravellingItem item) {
        if (destination.equals(at)) {
            return deliverySides(at, item.batch(), item.origin()).stream().min(Comparator.naturalOrder());
        }
        if (!nodes.containsKey(destination)) {
            return Optional.empty();
        }
        Optional<Route> route = wayTowards(at, destination, item);
        route.ifPresent(found -> item.passTo(found.nextHop()));
        return route.map(Route::exit);
    }

    // the next step of the item from one router towards another, the item itself is left alone
    private Optional<Route> wayTowards(RouterId at, RouterId destination, TravellingItem item) {
        Optional<Route> route = routing.route(at, destination);
        ItemType type = item.batch().type();
        boolean ordered = item.mode() == TransportMode.REQUESTED;
        Predicate<FirewallRule> stops = rule -> ordered ? rule.stopsOrdered(type) : rule.stopsSorted(type);
        if (route.isPresent() && stoppedOnTheWay(at, destination, stops)) {
            // a firewall is in the way, so the item takes a way around it if there is one
            route = detour(stops).route(at, destination);
        }
        return route;
    }

    // the routers the item passes from the given one to its destination, both ends included, the way chooseExit sends it
    // empty for an item without a destination or with none it can get to from there right now
    public List<RouterId> routeOf(RouterId from, TravellingItem item) {
        Optional<RouterId> destination = item.destination();
        if (destination.isEmpty() || !nodes.containsKey(from) || !nodes.containsKey(destination.get())) {
            return List.of();
        }
        List<RouterId> passed = new ArrayList<>();
        passed.add(from);
        RouterId at = from;
        while (!at.equals(destination.get())) {
            Optional<Route> route = wayTowards(at, destination.get(), item);
            if (route.isEmpty() || passed.size() > nodes.size()) {
                return List.of();
            }
            at = route.get().nextHop();
            passed.add(at);
        }
        return List.copyOf(passed);
    }

    // the network without the ways out of the firewalls that stop what the test describes, the others still let it through
    private RoutingNetwork detour(Predicate<FirewallRule> stops) {
        Set<RouterId> closed = new HashSet<>();
        for (RouterId firewall : firewalls) {
            if (nodes.get(firewall).firewall().filter(stops).isPresent()) {
                closed.add(firewall);
            }
        }
        return detours.computeIfAbsent(closed, key -> {
            RoutingNetwork network = new RoutingNetwork();
            nodes.keySet().forEach(network::add);
            links.forEach((id, own) -> network.setLinks(id, key.contains(id) ? List.of() : withConnectors(id, own)));
            return network;
        });
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
        return !stoppedOnTheWay(from, to, stops) || detour(stops).route(from, to).isPresent();
    }

    // best sink for a batch that is at the given router and came in through origin
    // only sinks that want it at least as much as minPriority count
    private Optional<Claim> findDestination(RouterId at, ItemBatch batch, Side origin, boolean mayStay, int minPriority,
            long now) {
        if (batch.type().packedFluid()) {
            // fluid pipes look after it, see FluidLogistics
            return Optional.empty();
        }
        if (bulkStorageAt(at)) {
            // with bulk storage most sinks are default routes, far too many to go through for every item
            // whoever wants the item more than those is asked first, and that is the usual end of it
            Set<RouterId> keen = bulkStorage.keenOn(batch.type());
            keen.addAll(sinksByItem.getOrDefault(batch.type(), Set.of()));
            Optional<Claim> claim = claimAmong(keen, at, batch, origin, mayStay,
                    Math.max(minPriority, ItemSink.DEFAULT_ROUTE_PRIORITY + 1), now);
            if (claim.isPresent()) {
                return claim;
            }
        }
        Set<RouterId> candidates = new HashSet<>(sinksForAnything);
        candidates.addAll(sinksByItem.getOrDefault(batch.type(), Set.of()));
        return claimAmong(candidates, at, batch, origin, mayStay, minPriority, now);
    }

    private Optional<Claim> claimAmong(Set<RouterId> candidates, RouterId at, ItemBatch batch, Side origin, boolean mayStay,
            int minPriority, long now) {
        Set<InventoryRef> cameFrom = sourceInventories(at, origin, mayStay);
        List<SinkOffer> offers = new ArrayList<>();
        Map<RouterId, SinkBid> bids = new HashMap<>();
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
            available.putIfAbsent(crafter.output().type().exact(), 0);
        }
        return available;
    }

    // what the crafters the requester reaches make
    public Set<ItemType> craftable(RouterId requester) {
        Set<ItemType> craftable = new LinkedHashSet<>();
        craftersFor(requester).forEach(crafter -> craftable.add(crafter.output().type()));
        return craftable;
    }

    // like request, but the item itself is crafted even when providers hold it, its ingredients come from wherever
    public RequestOutcome requestCrafted(RouterId requester, ItemBatch wanted, long now) {
        List<Stock> stock = new ArrayList<>();
        for (Stock entry : stockFor(requester)) {
            Map<ItemType, Integer> items = new LinkedHashMap<>(entry.items());
            items.remove(wanted.type());
            stock.add(new Stock(entry.provider(), items));
        }
        FluidIngredients fluidIngredients = fluidIngredients(requester);
        stock.addAll(fluidIngredients.stock(fluids.stockFor(requester)));
        RequestPlan plan = new RequestPlanner(stock, craftersFor(requester)).plan(requester, wanted);
        if (!plan.complete()) {
            return new RequestOutcome(plan, true);
        }
        if (!usePower(requester, providedItems(plan) * ENERGY_PER_PROVIDED_ITEM)) {
            return new RequestOutcome(plan, false);
        }
        execute(plan, fluidIngredients, now);
        return new RequestOutcome(plan, true);
    }

    // the routers the given one has a way to, nearest first
    public List<RouterId> reachable(RouterId from) {
        return reachableFrom(from);
    }

    // what was sent to a router and has not arrived there yet
    public Map<ItemType, Integer> onTheWayTo(RouterId destination, long now) {
        return pending.expectedAt(destination, now);
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
            nodes.get(id).outputOrders().owedByType().forEach((type, count) -> backlog.merge(type.exact(), count, Integer::sum));
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

    // orders several items in one go, all of them or nothing
    public RequestOutcome requestList(RouterId requester, List<ItemBatch> wanted, long now) {
        return request(requester, wanted, now, 0, false, false);
    }

    // what a request would take from stock, craft and cost if it were made now, nothing is ordered and no power is used
    public RequestSummary preview(RouterId requester, ItemBatch wanted, long now) {
        RequestPlan plan = request(requester, List.of(wanted), now, 0, false, true).plan();
        return RequestSummary.of(plan, ENERGY_PER_PROVIDED_ITEM, ENERGY_PER_CRAFTED_ITEM);
    }

    private RequestOutcome request(RouterId requester, ItemBatch wanted, long now, int extraEnergy, boolean partial) {
        return request(requester, List.of(wanted), now, extraEnergy, partial, false);
    }

    // with partial set, whatever part of the order can be had is started
    private RequestOutcome request(RouterId requester, List<ItemBatch> wanted, long now, int extraEnergy, boolean partial,
            boolean simulate) {
        FluidIngredients fluidIngredients = fluidIngredients(requester);
        List<Stock> stock = new ArrayList<>(stockFor(requester));
        stock.addAll(fluidIngredients.stock(fluids.stockFor(requester)));
        RequestPlan plan = new RequestPlanner(stock, craftersFor(requester)).plan(requester, wanted);
        boolean nothing = plan.provisions().isEmpty() && plan.crafts().isEmpty();
        if (simulate || !plan.complete() && (!partial || nothing)) {
            return new RequestOutcome(plan, true);
        }
        // what a provider could not hand out after all was paid for with the first order
        int forItems = cameShort.isEmpty() ? providedItems(plan) * ENERGY_PER_PROVIDED_ITEM : 0;
        if (!usePower(requester, forItems + extraEnergy)) {
            return new RequestOutcome(plan, false);
        }
        execute(plan, fluidIngredients, now);
        return new RequestOutcome(plan, true);
    }

    // fluid for a crafter is not paid for, fluid providers send for free
    private static int providedItems(RequestPlan plan) {
        return plan.provisions().stream().filter(provision -> !provision.batch().type().packedFluid())
                .mapToInt(provision -> provision.batch().count()).sum();
    }

    // the fluids the crafters within reach take, empty in a network without fluid crafting
    private FluidIngredients fluidIngredients(RouterId requester) {
        FluidIngredients ingredients = new FluidIngredients();
        for (RouterId id : reachableFrom(requester)) {
            for (CraftingSetup craft : nodes.get(id).crafts()) {
                craft.fluids().forEach(ingredients::add);
            }
        }
        return ingredients;
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
        long energy = 0;
        for (PowerSource source : powerSourcesFor(id)) {
            energy += source.draw(Integer.MAX_VALUE, true);
        }
        return new RouterStatus(reachable.size(), (int) Math.min(energy, Integer.MAX_VALUE));
    }

    // takes energy from the junctions of the network the router is in, all of it or nothing
    public boolean usePower(RouterId at, int amount) {
        List<PowerSource> sources = powerSourcesFor(at);
        int available = 0;
        for (PowerSource source : sources) {
            available += source.draw(amount - available, true);
            if (available >= amount) {
                break;
            }
        }
        if (available < amount) {
            return false;
        }
        int left = amount;
        for (PowerSource source : sources) {
            if (left > 0) {
                left -= source.draw(left, false);
            }
        }
        return left <= 0;
    }

    // every energy store the router may draw from, each once however many routers touch it
    private List<PowerSource> powerSourcesFor(RouterId at) {
        Map<Object, PowerSource> sources = new LinkedHashMap<>();
        if (nodes.containsKey(at)) {
            // its own come first
            nodes.get(at).powerSources().forEach(source -> sources.putIfAbsent(source.key(), source));
        }
        OptionalInt component = routing.component(at);
        if (firewalls.isEmpty() && connectors.isEmpty() && component.isPresent()) {
            // nothing keeps energy apart, so one walk over the routers answers for all that are connected
            for (PowerSource source : powerSourcesOf.computeIfAbsent(component.getAsInt(), key -> List.copyOf(powerSourcesNear(at).values()))) {
                sources.putIfAbsent(source.key(), source);
            }
        } else {
            sources.putAll(powerSourcesNear(at));
        }
        return List.copyOf(sources.values());
    }

    // the energy stores of the routers the given one may draw from, nearest first
    private Map<Object, PowerSource> powerSourcesNear(RouterId at) {
        Map<Object, PowerSource> sources = new LinkedHashMap<>();
        for (RouterId id : reachableFrom(at)) {
            if (wired(at, id) && passes(at, id, FirewallRule::blockPower)) {
                for (PowerSource source : nodes.get(id).powerSources()) {
                    sources.putIfAbsent(source.key(), source);
                }
            }
        }
        return sources;
    }

    // an energy store appeared or went away next to a router
    public void powerSourcesChanged() {
        powerSourcesOf.clear();
    }

    // whether the router could use any energy right now, for the look of its pipe
    // every routed pipe keeps asking, so one walk over all routers answers for all of them until the network
    // changes or POWER_LOOK_INTERVAL ticks have passed. empty when there is no telling in this tick
    public Optional<Boolean> hasPower(RouterId at, long now) {
        if (now == lastChangeAnywhere) {
            // routers are still registering, see settling
            return Optional.empty();
        }
        OptionalInt component = routing.component(at);
        if (component.isEmpty()) {
            return Optional.of(false);
        }
        if (now - powerLookedAt >= POWER_LOOK_INTERVAL || powerLookedAfter != lastChangeAnywhere) {
            poweredComponents.clear();
            powerSourcesOf.clear();
            nodes.forEach((id, node) -> routing.component(id).ifPresent(group -> {
                if (!poweredComponents.contains(group) && node.drawPower(1, true) > 0) {
                    poweredComponents.add(group);
                }
            }));
            powerLookedAt = now;
            powerLookedAfter = lastChangeAnywhere;
        }
        if (!poweredComponents.contains(component.getAsInt())) {
            return Optional.of(false);
        }
        if (firewalls.stream().noneMatch(firewall -> routing.component(firewall).equals(component))
                || nodes.get(at).drawPower(1, true) > 0) {
            return Optional.of(true);
        }
        // a firewall may keep the energy on its other side from this router
        if (!mayRouteFrom(at, now)) {
            return Optional.empty();
        }
        return Optional.of(reachableFrom(at).stream()
                .anyMatch(id -> passes(at, id, FirewallRule::blockPower) && nodes.get(id).drawPower(1, true) > 0));
    }

    private void execute(RequestPlan plan, FluidIngredients fluidIngredients, long now) {
        // what a provider could not hand out after all, its inventory changed since the plan was made
        List<Provision> shortfalls = new ArrayList<>();
        for (Provision provision : plan.provisions()) {
            RouterNode provider = nodes.get(provision.provider());
            if (provider == null) {
                continue;
            }
            ItemType type = provision.batch().type();
            Optional<FluidType> fluid = fluidIngredients.fluidOf(type);
            if (fluid.isPresent()) {
                // fluid for a crafter is an order to a fluid provider, it sends it to the fluid satellite at its own pace
                fluids.order(provision.provider(), new FluidBatch(fluid.get(), provision.batch().count()), provision.destination());
                continue;
            }
            // spare output of a crafting pipe is not held back by any provider setting
            int sent = provider.provides()
                    ? provider.provide(type, provision.batch().count(), provision.destination())
                    : provider.send(type, provision.batch().count(), provision.destination());
            pending.expect(provision.destination(), type, sent, now + PROVISION_TIMEOUT);
            if (sent < provision.batch().count()) {
                shortfalls.add(new Provision(provision.provider(), provision.destination(),
                        new ItemBatch(type, provision.batch().count() - sent)));
            }
        }
        // the rest is ordered again once, from wherever else it can still be had
        if (!shortfalls.isEmpty() && cameShort.isEmpty()) {
            shortfalls.forEach(shortfall -> cameShort.add(shortfall.provider()));
            try {
                for (Provision shortfall : shortfalls) {
                    request(shortfall.destination(), shortfall.batch(), now, 0, true);
                }
            } finally {
                cameShort.clear();
            }
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
        if (bulkStorage.modules().contains(id)) {
            bulkStorage.look(nodes::get);
        }
        if (!node.outputOrders().isEmpty() && now % CRAFT_INTERVAL == 0) {
            boolean progress = node.outputOrders().ship(now, CRAFT_TIMEOUT, order -> {
                // a result with fuzzy flags is whatever matching item the machine holds most of
                ItemType made = FuzzyMatch.largest(order.batch().type(), node.contents());
                // only pay for what is really there to send
                int ready = Math.min(order.batch().count(), node.contents().getOrDefault(made, 0));
                if (ready == 0 || !usePower(id, ready * ENERGY_PER_CRAFTED_ITEM)) {
                    return 0;
                }
                return node.send(made, ready, order.destination(),
                        order.isExtra() ? TransportMode.UNCLAIMED : TransportMode.REQUESTED);
            });
            if (progress) {
                // whoever still waits for this crafter waits longer, or a supplier would order the rest a second time
                for (OutputOrder order : node.outputOrders().all()) {
                    if (!order.isExtra()) {
                        pending.extend(order.destination(), order.batch().type(), now + CRAFT_TIMEOUT);
                    }
                }
            }
        } else if (now % CRAFT_INTERVAL == 0) {
            node.cleanup().ifPresent(cleanup -> cleanUp(id, node, cleanup));
        }
        if (now % SUPPLY_INTERVAL == 0) {
            for (Supply supply : node.supplies()) {
                supply(id, node, supply, now);
            }
            for (PatternSupply pattern : node.patternSupplies()) {
                supplyPattern(id, node, pattern, now);
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
        // what is in the inventory counts whether it can be taken out or not
        Map<ItemType, Integer> contents = node.held();
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

    // a supplier with a pattern orders what each of its slots is short of
    private void supplyPattern(RouterId id, RouterNode node, PatternSupply pattern, long now) {
        for (ItemBatch order : pattern.orders(node.slots(), type -> pending.expected(id, type, now))) {
            if (pattern.mode().allOrNothing()) {
                request(id, order, now, ENERGY_PER_SUPPLY_ORDER, false);
            } else {
                requestUpTo(id, order.type(), order.count(), now);
            }
        }
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
        // finished items crafting pipes owe, per inventory, nobody else may hand those out
        Map<InventoryRef, Map<ItemType, Integer>> owedAt = new HashMap<>();
        for (RouterId id : routers) {
            RouterNode node = nodes.get(id);
            if (!node.outputOrders().isEmpty()) {
                for (InventoryRef inventory : node.inventories().values()) {
                    Map<ItemType, Integer> owed = owedAt.computeIfAbsent(inventory, key -> new HashMap<>());
                    node.outputOrders().all().forEach(order -> owed.merge(order.batch().type().exact(), order.batch().count(), Integer::sum));
                }
            }
        }
        // most routers of a large base have nothing to give, so the cheap questions come first
        for (RouterId id : routers) {
            RouterNode node = nodes.get(id);
            if (node.provides()) {
                if (cameShort.contains(id) || !passes(requester, id, FirewallRule::blockProviding)) {
                    continue;
                }
                Collection<InventoryRef> inventories = node.inventories().values();
                if (!Collections.disjoint(inventories, counted)) {
                    continue;
                }
                Map<ItemType, Integer> provided = new LinkedHashMap<>(node.provided());
                owedHere(node, inventories, owedAt)
                        .forEach((type, count) -> provided.computeIfPresent(type, (key, have) -> have > count ? have - count : null));
                if (!firewalls.isEmpty()) {
                    provided.keySet().removeIf(type -> !passes(requester, id, rule -> rule.rejects(type)));
                }
                stock.add(new Stock(id, provided));
                counted.addAll(inventories);
                continue;
            }
            List<CraftingSetup> crafts = node.crafts();
            if (crafts.isEmpty()) {
                continue;
            }
            Collection<InventoryRef> inventories = node.inventories().values();
            if (Collections.disjoint(inventories, counted)) {
                // a machine with two crafting pipes on it is counted once, and what any of them owes is not spare
                Map<ItemType, Integer> owedHere = owedHere(node, inventories, owedAt);
                Map<ItemType, Integer> spare = new LinkedHashMap<>();
                Map<ItemType, Integer> contents = node.contents();
                for (CraftingSetup craft : crafts) {
                    ItemType made = craft.recipe().output().type();
                    int owed = owedHere.getOrDefault(made.exact(), 0);
                    // a fuzzy result is whatever the machine holds that matches it
                    for (Map.Entry<ItemType, Integer> held : contents.entrySet()) {
                        if (!made.accepts(held.getKey())) {
                            continue;
                        }
                        int left = Math.max(0, held.getValue() - owed);
                        owed = Math.max(0, owed - held.getValue());
                        // spare output counts as providing, so a firewall that stops that or the item stops it too
                        if (left > 0 && passes(requester, id, rule -> rule.blockProviding() || rule.rejects(held.getKey()))) {
                            spare.put(held.getKey(), left);
                        }
                    }
                }
                if (!spare.isEmpty()) {
                    stock.add(new Stock(id, spare));
                    counted.addAll(inventories);
                }
            }
        }
        return stock;
    }

    // what the crafting pipes on the inventories of a router still have to send out of them
    private static Map<ItemType, Integer> owedHere(RouterNode node, Collection<InventoryRef> inventories,
            Map<InventoryRef, Map<ItemType, Integer>> owedAt) {
        Map<ItemType, Integer> owed = new HashMap<>();
        inventories.forEach(inventory -> owedAt.getOrDefault(inventory, Map.of())
                .forEach((type, count) -> owed.merge(type, count, Integer::sum)));
        if (inventories.isEmpty()) {
            // without a known inventory at least its own debts count
            node.outputOrders().all().forEach(order -> owed.merge(order.batch().type().exact(), order.batch().count(), Integer::sum));
        }
        return owed;
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
                for (Map.Entry<String, List<ItemBatch>> entry : craft.bySatellite().entrySet()) {
                    Optional<RouterId> found = satellite(reachable, PipeKind.SATELLITE, entry.getKey());
                    allFound &= found.isPresent();
                    found.ifPresent(satellite -> entry.getValue().forEach(input -> forSatellites
                            .computeIfAbsent(satellite, key -> new LinkedHashMap<>()).merge(input.type(), input.count(), Integer::sum)));
                }
                for (FluidInput input : craft.fluids()) {
                    Optional<RouterId> found = satellite(reachable, PipeKind.FLUID_SATELLITE, input.satellite());
                    allFound &= found.isPresent();
                    found.ifPresent(satellite -> forSatellites.computeIfAbsent(satellite, key -> new LinkedHashMap<>())
                            .merge(input.asItem(), input.amount(), Integer::sum));
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

    // the nearest satellite of that kind with that name
    private Optional<RouterId> satellite(List<RouterId> reachable, PipeKind kind, String name) {
        return reachable.stream()
                .filter(id -> nodes.get(id).kind() == kind && nodes.get(id).satelliteName().equals(name))
                .findFirst();
    }

    // the names of the satellites of that kind the router has a way to, nearest first
    public List<String> satelliteNames(RouterId from, PipeKind kind) {
        if (!nodes.containsKey(from)) {
            return List.of();
        }
        return reachableFrom(from).stream().filter(id -> nodes.get(id).kind() == kind)
                .map(id -> nodes.get(id).satelliteName()).filter(name -> !name.isEmpty()).distinct().toList();
    }
}
