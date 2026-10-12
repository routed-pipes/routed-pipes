package logisticspipes.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;

// what the network needs to know about a routed pipe and the world around it
public interface RouterNode {

    PipeKind kind();

    boolean hasInventory();

    // which inventory is on which side, empty for sides without one
    default Map<Side, InventoryRef> inventories() {
        return Map.of();
    }

    // sides with an inventory that has room for at least part of the batch
    Set<Side> inventoriesAccepting(ItemBatch batch);

    // everything that can be taken out of the inventories next to the pipe
    Map<ItemType, Integer> contents();

    // takes items out of the inventories and sends them on their way, returns how many went out
    int send(ItemType type, int count, RouterId destination);

    // whether the contents of the inventory can be ordered
    default boolean provides() {
        return kind() == PipeKind.PROVIDER;
    }

    // takes energy from power junctions next to the pipe, returns how much it got
    default int drawPower(int amount, boolean simulate) {
        return 0;
    }

    // the ways the pipe pushes the contents of its inventory to other pipes
    default List<ExtractionMode> extractions() {
        return List.of();
    }

    // how much the pipe wants an item nobody asked for, empty for not at all
    default OptionalInt sinkPriority(ItemType type) {
        return OptionalInt.empty();
    }

    // crafting pipes only
    default Optional<Recipe> recipe() {
        return Optional.empty();
    }

    // crafting pipes only: the part of the recipe that is delivered to the satellite
    default List<ItemBatch> satelliteInputs() {
        return List.of();
    }

    // crafting pipes only, outlives the network so the pipe has to keep it
    default OutputOrders outputOrders() {
        return new OutputOrders();
    }

    // own id for satellites, the chosen satellite for crafting pipes, 0 for none
    default int satelliteId() {
        return 0;
    }

    // supplier pipes only, how many of each item the inventory should hold
    default Map<ItemType, Integer> supplyTargets() {
        return Map.of();
    }
}
