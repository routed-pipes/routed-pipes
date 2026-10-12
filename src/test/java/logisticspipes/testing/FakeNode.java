package logisticspipes.testing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TransportMode;

// a routed pipe with a pretend inventory that takes everything
public final class FakeNode implements RouterNode {

    public record Shipment(ItemBatch batch, RouterId destination) {
    }

    public final Set<Side> inventories = EnumSet.noneOf(Side.class);
    // which inventory each side touches, only set by tests about shared inventories
    public final Map<Side, InventoryRef> inventoryRefs = new LinkedHashMap<>();
    public final List<ItemBatch> satelliteInputs = new ArrayList<>();
    // false makes the inventory refuse everything
    public boolean room = true;
    public final Map<ItemType, Integer> contents = new LinkedHashMap<>();
    public final List<Shipment> shipments = new ArrayList<>();
    public final Map<ItemType, Integer> supplyTargets = new LinkedHashMap<>();
    public final OutputOrders outputOrders = new OutputOrders();
    public Recipe recipe;
    // basic pipes take everything by default, like a fresh one in the game
    public SinkRule sink = new ItemSink(Set.of(), true);
    public int satelliteId;
    public TransportMode lastMode;
    public int extractionBoost = 1;
    // how often the network looked into the inventories of this pipe
    public int probes;
    // energy in junctions next to this pipe, plenty unless a test says otherwise
    public int power = 1_000_000;
    public boolean provides;
    public final List<ExtractionMode> extractions = new ArrayList<>();
    private final PipeKind kind;

    public FakeNode(PipeKind kind) {
        this.kind = kind;
    }

    public FakeNode stock(String name, int count) {
        contents.put(new Fixtures.TestItem(name), count);
        inventories.add(Side.UP);
        return this;
    }

    @Override
    public PipeKind kind() {
        return kind;
    }

    @Override
    public boolean hasInventory() {
        return !inventories.isEmpty();
    }

    @Override
    public Set<Side> inventoriesAccepting(ItemBatch batch) {
        probes++;
        return room ? EnumSet.copyOf(inventories) : EnumSet.noneOf(Side.class);
    }

    @Override
    public Map<Side, InventoryRef> inventories() {
        return inventoryRefs;
    }

    @Override
    public List<ItemBatch> satelliteInputs() {
        return satelliteInputs;
    }

    @Override
    public Map<ItemType, Integer> contents() {
        return new LinkedHashMap<>(contents);
    }

    @Override
    public int send(ItemType type, int count, RouterId destination, TransportMode mode) {
        lastMode = mode;
        int sent = Math.min(count, contents.getOrDefault(type, 0));
        if (sent > 0) {
            contents.merge(type, -sent, Integer::sum);
            shipments.add(new Shipment(new ItemBatch(type, sent), destination));
        }
        return sent;
    }

    @Override
    public int drawPower(int amount, boolean simulate) {
        int drawn = Math.min(amount, power);
        if (!simulate) {
            power -= drawn;
        }
        return drawn;
    }

    @Override
    public boolean provides() {
        return provides || kind == PipeKind.PROVIDER;
    }

    @Override
    public List<ExtractionMode> extractions() {
        return extractions;
    }

    @Override
    public int extractionBoost() {
        return extractionBoost;
    }

    @Override
    public List<SinkRule> sinkRules() {
        return kind == PipeKind.BASIC || kind == PipeKind.CHASSIS ? List.of(sink) : List.of();
    }

    @Override
    public Optional<Recipe> recipe() {
        return Optional.ofNullable(recipe);
    }

    @Override
    public OutputOrders outputOrders() {
        return outputOrders;
    }

    @Override
    public int satelliteId() {
        return satelliteId;
    }

    @Override
    public Map<ItemType, Integer> supplyTargets() {
        return supplyTargets;
    }
}
