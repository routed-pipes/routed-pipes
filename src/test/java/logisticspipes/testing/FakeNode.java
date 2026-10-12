package logisticspipes.testing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;

// a routed pipe with a pretend inventory that takes everything
public final class FakeNode implements RouterNode {

    public record Shipment(ItemBatch batch, RouterId destination) {
    }

    public final Set<Side> inventories = EnumSet.noneOf(Side.class);
    public final Map<ItemType, Integer> contents = new LinkedHashMap<>();
    public final List<Shipment> shipments = new ArrayList<>();
    public final Map<ItemType, Integer> supplyTargets = new LinkedHashMap<>();
    public final OutputOrders outputOrders = new OutputOrders();
    public Recipe recipe;
    // basic pipes take everything by default, like a fresh one in the game
    public ItemSink sink = new ItemSink(Set.of(), true);
    public int satelliteId;
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
        return EnumSet.copyOf(inventories);
    }

    @Override
    public Map<ItemType, Integer> contents() {
        return new LinkedHashMap<>(contents);
    }

    @Override
    public int send(ItemType type, int count, RouterId destination) {
        int sent = Math.min(count, contents.getOrDefault(type, 0));
        if (sent > 0) {
            contents.merge(type, -sent, Integer::sum);
            shipments.add(new Shipment(new ItemBatch(type, sent), destination));
        }
        return sent;
    }

    @Override
    public OptionalInt sinkPriority(ItemType type) {
        return kind == PipeKind.BASIC ? sink.priority(type) : OptionalInt.empty();
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
