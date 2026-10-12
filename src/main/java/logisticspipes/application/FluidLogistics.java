package logisticspipes.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.application.port.FluidNode;
import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.fluid.FluidBatch;
import logisticspipes.domain.fluid.FluidOrders;
import logisticspipes.domain.fluid.FluidSink;
import logisticspipes.domain.fluid.FluidSupply;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.PackedFluid;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.DestinationSelector;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkOffer;
import logisticspipes.domain.request.Provision;
import logisticspipes.domain.request.RequestPlan;
import logisticspipes.domain.request.RequestPlanner;
import logisticspipes.domain.request.Stock;
import logisticspipes.domain.routing.RouterId;

// the decisions the fluid pipes of a network make
// fluid travels packed as an item, so routing and power are those of the item network
public final class FluidLogistics {

    // sending packed fluid to a sink costs one energy for every so many mB
    public static final int MB_PER_INSERTION_ENERGY = 100;
    public static final int ENERGY_PER_REQUEST = 10;
    // a provider sends at most this much every so many ticks
    static final int PROVIDE_AMOUNT = 5000;
    static final int PROVIDE_INTERVAL = 6;
    // ticks between a satellite asking again for lost fluid, and between a supplier looking at its tank
    static final int REORDER_INTERVAL = 40;
    static final int SUPPLY_INTERVAL = 100;

    // fluid on its way to a pipe and how much of it
    public record Shipment(RouterId destination, int amount) {
    }

    private final LogisticsNetwork network;
    private final PendingDeliveries onRoute = new PendingDeliveries();
    // what each provider still has to send, gone when the world is left like the orders of the original
    private final Map<RouterId, FluidOrders> orders = new HashMap<>();
    // fluid that never made it into the tanks of a satellite, it asks for that again
    private final Map<RouterId, Map<FluidType, Integer>> owedToSatellites = new HashMap<>();

    FluidLogistics(LogisticsNetwork network) {
        this.network = network;
    }

    private Optional<FluidNode> node(RouterId id) {
        RouterNode node = network.node(id);
        return node == null ? Optional.empty() : node.fluidNode();
    }

    private Set<InventoryRef> tanksOf(RouterId id) {
        return node(id).map(FluidNode::tanks).orElse(Set.of());
    }

    private static boolean sharesTank(FluidNode node, Set<InventoryRef> tanks) {
        return node.tanks().stream().anyMatch(tanks::contains);
    }

    // the best pipe to take fluid nobody asked for: fluid sinks before terminus pipes, then the nearest
    private Optional<Shipment> sinkFor(RouterId from, FluidBatch batch, long now) {
        Set<InventoryRef> source = tanksOf(from);
        List<SinkOffer> offers = new ArrayList<>();
        Map<RouterId, Integer> amounts = new HashMap<>();
        for (RouterId id : network.routes(from).byDistance()) {
            Optional<FluidNode> node = node(id);
            Optional<FluidSink> sink = node.flatMap(FluidNode::sink);
            // a pipe on the tank the fluid came out of would only put it straight back
            if (sink.isEmpty() || sharesTank(node.get(), source)) {
                continue;
            }
            int amount = sink.get().amount(batch, node.get().sinkRoom(batch.type()), expected(id, batch.type(), now));
            if (amount > 0) {
                offers.add(new SinkOffer(id, sink.get().priority().ordinal()));
                amounts.put(id, amount);
            }
        }
        return DestinationSelector.select(from, offers, network.routes(from))
                .map(id -> new Shipment(id, amounts.get(id)));
    }

    // how much of the fluid is travelling to the pipe or waiting at a provider to be sent to it
    private int expected(RouterId destination, FluidType type, long now) {
        int ordered = orders.values().stream().mapToInt(queue -> queue.owedTo(destination, type)).sum();
        return ordered + onRoute.expected(destination, new PackedFluid(type), now);
    }

    private Optional<Shipment> expect(Optional<Shipment> shipment, FluidType type, long now) {
        shipment.ifPresent(found -> onRoute.expect(found.destination(), new PackedFluid(type), found.amount(),
                now + LogisticsNetwork.PROVISION_TIMEOUT));
        return shipment;
    }

    // where fluid that was pumped into a pipe goes and how much of it, empty if nobody takes it or there is no power
    // the caller has to send that much
    public Optional<Shipment> insert(RouterId from, FluidBatch batch, long now) {
        return expect(sinkFor(from, batch, now)
                .filter(found -> network.usePower(from, found.amount() / MB_PER_INSERTION_ENERGY)), batch.type(), now);
    }

    // another place for fluid that did not fit where it was sent or lost its way, the caller has to send that much
    public Optional<Shipment> reroute(RouterId from, FluidBatch batch, long now) {
        return expect(sinkFor(from, batch, now), batch.type(), now);
    }

    // takes energy from the network the pipe is in, all of it or nothing
    public boolean usePower(RouterId at, int amount) {
        return network.usePower(at, amount);
    }

    // packed fluid reached the pipe it was sent to, and that much of it did not fit into the tanks
    public void arrived(RouterId at, FluidBatch batch, int notUnpacked) {
        onRoute.arrived(at, new PackedFluid(batch.type()), batch.amount());
        missed(at, batch.type(), notUnpacked);
    }

    // packed fluid will never reach the pipe it was sent to
    public void lost(RouterId destination, FluidBatch batch) {
        onRoute.arrived(destination, new PackedFluid(batch.type()), batch.amount());
        missed(destination, batch.type(), batch.amount());
    }

    // a supplier sees by its tank what is missing, a satellite has to be told
    private void missed(RouterId destination, FluidType type, int amount) {
        RouterNode node = network.node(destination);
        if (amount > 0 && node != null && node.kind() == PipeKind.FLUID_SATELLITE) {
            owedToSatellites.computeIfAbsent(destination, satellite -> new LinkedHashMap<>()).merge(type, amount, Integer::sum);
        }
    }

    // an order for items found this fluid for one of its crafters, the provider sends it like any other order
    void order(RouterId provider, FluidBatch batch, RouterId destination) {
        orders.computeIfAbsent(provider, key -> new FluidOrders()).add(new FluidOrders.Order(batch.type(), batch.amount(), destination));
    }

    // what the providers the requester can reach hold and have not promised to anyone, never its own tank
    List<Stock> stockFor(RouterId requester) {
        List<Stock> stock = new ArrayList<>();
        Set<InventoryRef> counted = new HashSet<>(tanksOf(requester));
        for (RouterId id : network.routes(requester).byDistance()) {
            Optional<FluidNode> node = node(id).filter(FluidNode::provides);
            // a tank with two providers on it counts once
            if (node.isEmpty() || sharesTank(node.get(), counted)) {
                continue;
            }
            counted.addAll(node.get().tanks());
            Map<ItemType, Integer> fluids = new LinkedHashMap<>();
            node.get().providable().forEach((type, amount) -> {
                int free = amount - (orders.containsKey(id) ? orders.get(id).owed(type) : 0);
                if (free > 0) {
                    fluids.put(new PackedFluid(type), free);
                }
            });
            stock.add(new Stock(id, fluids));
        }
        return stock;
    }

    // every fluid the requester could order and how much of it
    public Map<FluidType, Integer> available(RouterId requester) {
        Map<FluidType, Integer> available = new LinkedHashMap<>();
        for (Stock stock : stockFor(requester)) {
            stock.items().forEach((type, amount) -> available.merge(((PackedFluid) type).fluid(), amount,
                    (first, second) -> (int) Math.min((long) first + second, Integer.MAX_VALUE)));
        }
        return available;
    }

    // orders fluid from the providers, all of it or nothing, the amounts of the outcome are in mB of packed fluid
    public RequestOutcome request(RouterId requester, FluidBatch wanted, long now) {
        return request(requester, wanted, now, ENERGY_PER_REQUEST, false);
    }

    // with partial set, whatever part of the order can be had is started
    private RequestOutcome request(RouterId requester, FluidBatch wanted, long now, int energy, boolean partial) {
        // fluid is never crafted
        RequestPlan plan = new RequestPlanner(stockFor(requester), List.of())
                .plan(requester, new ItemBatch(new PackedFluid(wanted.type()), wanted.amount()));
        if (!plan.complete() && (!partial || plan.provisions().isEmpty())) {
            return new RequestOutcome(plan, true);
        }
        if (!network.usePower(requester, energy)) {
            return new RequestOutcome(plan, false);
        }
        for (Provision provision : plan.provisions()) {
            orders.computeIfAbsent(provision.provider(), provider -> new FluidOrders())
                    .add(new FluidOrders.Order(wanted.type(), provision.batch().count(), provision.destination()));
        }
        return new RequestOutcome(plan, true);
    }

    // upkeep of one fluid pipe, called every tick
    public void tick(RouterId id, long now) {
        FluidOrders queue = orders.get(id);
        if (queue != null && now % PROVIDE_INTERVAL == 0) {
            node(id).ifPresent(node -> ship(node, queue, now));
            if (queue.isEmpty()) {
                orders.remove(id);
            }
        }
        if (now % REORDER_INTERVAL == 0 && owedToSatellites.containsKey(id)) {
            reorder(id, now);
        }
        if (now % SUPPLY_INTERVAL == 0) {
            node(id).ifPresent(node -> node.supply().ifPresent(supply -> supply(id, node, supply, now)));
        }
    }

    // a satellite takes whatever part of its lost fluid can be had and keeps asking for the rest
    private void reorder(RouterId satellite, long now) {
        Map<FluidType, Integer> owed = owedToSatellites.get(satellite);
        for (FluidType type : List.copyOf(owed.keySet())) {
            int amount = owed.get(type);
            int ordered = request(satellite, new FluidBatch(type, amount), now, 0, true).plan().provisions().stream()
                    .mapToInt(provision -> provision.batch().count()).sum();
            if (ordered >= amount) {
                owed.remove(type);
            } else {
                owed.put(type, amount - ordered);
            }
        }
        if (owed.isEmpty()) {
            owedToSatellites.remove(satellite);
        }
    }

    // every tank next to the supplier is filled up to the target, what is on its way counts for all of them
    private void supply(RouterId id, FluidNode node, FluidSupply supply, long now) {
        for (int stocked : node.stocked(supply.fluid())) {
            int amount = supply.order(stocked, expected(id, supply.fluid(), now));
            if (amount > 0) {
                request(id, new FluidBatch(supply.fluid(), amount), now, LogisticsNetwork.ENERGY_PER_SUPPLY_ORDER, supply.partial());
            }
        }
    }

    // a provider works on its oldest order, one that it cannot serve at all is dropped
    private void ship(FluidNode provider, FluidOrders queue, long now) {
        FluidOrders.Order order = queue.first().orElseThrow();
        int sent = provider.provide(order.fluid(), Math.min(order.amount(), PROVIDE_AMOUNT), order.destination());
        if (sent == 0) {
            queue.giveUp();
            missed(order.destination(), order.fluid(), order.amount());
            return;
        }
        queue.sent(sent);
        onRoute.expect(order.destination(), new PackedFluid(order.fluid()), sent, now + LogisticsNetwork.PROVISION_TIMEOUT);
    }

    void forget(RouterId id) {
        onRoute.forget(id);
        orders.remove(id);
        owedToSatellites.remove(id);
    }
}
