package logisticspipes.testing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.application.port.FluidNode;
import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.Cleanup;
import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.Extraction;
import logisticspipes.domain.logistics.FirewallRule;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.logistics.Supply;
import logisticspipes.domain.logistics.SupplyMode;
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
    // set by tests about pipes with several recipes or stocks, like a chassis with modules
    public List<CraftingSetup> crafts;
    public List<Supply> supplies;
    public Cleanup cleanup;
    public FirewallRule firewall;
    // basic pipes take everything by default, like a fresh one in the game
    public SinkRule sink = new ItemSink(Set.of(), true);
    public int satelliteId;
    public TransportMode lastMode;
    // upgrades of the extractors
    public int actionSpeed;
    public int itemExtraction;
    public int stackExtraction;
    // how often the network looked into the inventories of this pipe
    public int probes;
    // energy in junctions next to this pipe, plenty unless a test says otherwise
    public int power = 1_000_000;
    public boolean provides;
    // empty means the provider hands out all it has
    public Map<ItemType, Integer> provided;
    public SupplyMode supplyMode = SupplyMode.BULK50;
    public int roomLeft = Integer.MAX_VALUE;
    public final List<ExtractionMode> extractions = new ArrayList<>();
    // extractors take everything unless a test lists items
    public Set<ItemType> extractionList = Set.of();
    public boolean extractionExcludes = true;
    // set by tests about fluid pipes
    public FluidNode fluid;
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
    public Optional<FluidNode> fluidNode() {
        return Optional.ofNullable(fluid);
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
    public Map<ItemType, Integer> provided() {
        return provided == null ? contents() : provided;
    }

    @Override
    public int provide(ItemType type, int count, RouterId destination) {
        return send(type, Math.min(count, provided().getOrDefault(type, 0)), destination, TransportMode.REQUESTED);
    }

    @Override
    public int roomFor(ItemType type, int upTo) {
        return room ? Math.min(upTo, roomLeft) : 0;
    }

    @Override
    public SupplyMode supplyMode() {
        return supplyMode;
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
    public List<Extraction> extractions() {
        return extractions.stream().map(mode -> new Extraction(mode, extractionList, extractionExcludes)
                .with(actionSpeed, itemExtraction, stackExtraction)).toList();
    }

    @Override
    public List<SinkRule> sinkRules() {
        return kind == PipeKind.BASIC || kind == PipeKind.CHASSIS ? List.of(sink) : List.of();
    }

    @Override
    public List<CraftingSetup> crafts() {
        return crafts == null ? RouterNode.super.crafts() : crafts;
    }

    @Override
    public Optional<FirewallRule> firewall() {
        return Optional.ofNullable(firewall);
    }

    @Override
    public Optional<Cleanup> cleanup() {
        return Optional.ofNullable(cleanup);
    }

    @Override
    public List<Supply> supplies() {
        return supplies == null ? RouterNode.super.supplies() : supplies;
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
