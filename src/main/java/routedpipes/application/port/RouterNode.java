package routedpipes.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import routedpipes.domain.crafting.Cleanup;
import routedpipes.domain.crafting.CraftingSetup;
import routedpipes.domain.crafting.OutputOrders;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.Extraction;
import routedpipes.domain.logistics.FirewallRule;
import routedpipes.domain.logistics.InventoryRef;
import routedpipes.domain.logistics.PatternSupply;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.SinkRule;
import routedpipes.domain.logistics.Supply;
import routedpipes.domain.logistics.SupplyMode;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.statistics.PipeStatistics;
import routedpipes.domain.transport.TransportMode;

// what the network needs to know about a routed pipe and the world around it
public interface RouterNode {

    PipeKind kind();

    boolean hasInventory();

    // shifts the rounds of this pipe against those of others, so that a base does not do all its upkeep in the same tick
    default int phase() {
        return 0;
    }

    // which inventory is on which side, empty for sides without one
    default Map<Side, InventoryRef> inventories() {
        return Map.of();
    }

    // sides with an inventory that has room for at least part of the batch
    Set<Side> inventoriesAccepting(ItemBatch batch);

    // everything that can be taken out of the inventories next to the pipe
    Map<ItemType, Integer> contents();

    // everything the inventories next to the pipe hold, also what cannot be taken out, like the input of a machine
    default Map<ItemType, Integer> held() {
        return contents();
    }

    // takes items out of the inventories and sends them on their way, returns how many went out
    // ordered items wait at a full destination, sorted ones go looking for another
    int send(ItemType type, int count, RouterId destination, TransportMode mode);

    default int send(ItemType type, int count, RouterId destination) {
        return send(type, count, destination, TransportMode.REQUESTED);
    }

    // whether the contents of the inventory can be ordered
    // what a provider hands out of its inventories, which may be less than what is in them
    default Map<ItemType, Integer> provided() {
        return contents();
    }

    // like send for an order, but takes only what the provider hands out
    default int provide(ItemType type, int count, RouterId destination) {
        return send(type, count, destination);
    }

    // how many of the item still fit into the inventories, counted no further than upTo
    default int roomFor(ItemType type, int upTo) {
        return inventoriesAccepting(new ItemBatch(type, upTo)).isEmpty() ? 0 : upTo;
    }

    default SupplyMode supplyMode() {
        return SupplyMode.BULK50;
    }

    // every recipe this pipe crafts
    default List<CraftingSetup> crafts() {
        if (kind() != PipeKind.CRAFTING || recipe().isEmpty()) {
            return List.of();
        }
        return List.of(new CraftingSetup(recipe().get(), satelliteInputs(), satelliteName(), 0));
    }

    // only firewall pipes have one
    default Optional<FirewallRule> firewall() {
        return Optional.empty();
    }

    // what the pipe clears out of its machine while it has nothing to craft
    default Optional<Cleanup> cleanup() {
        return Optional.empty();
    }

    // every stock this pipe keeps up
    default List<Supply> supplies() {
        return kind() == PipeKind.SUPPLIER ? List.of(new Supply(supplyTargets(), supplyMode())) : List.of();
    }

    // suppliers with a pattern upgrade: what they keep in which slot of the inventory
    default List<PatternSupply> patternSupplies() {
        return List.of();
    }

    // the inventory a pattern is about slot by slot, null for an empty slot
    default List<ItemBatch> slots() {
        return List.of();
    }

    default boolean provides() {
        return kind() == PipeKind.PROVIDER;
    }

    // takes energy from power junctions next to the pipe, returns how much it got
    // the energy stores next to the pipe, the network asks each store once even when several pipes touch it
    default List<PowerSource> powerSources() {
        RouterNode node = this;
        return List.of(new PowerSource() {
            @Override
            public Object key() {
                return node;
            }

            @Override
            public int draw(int amount, boolean simulate) {
                return node.drawPower(amount, simulate);
            }
        });
    }

    default int drawPower(int amount, boolean simulate) {
        return 0;
    }

    // the ways the pipe pushes the contents of its inventory to other pipes
    default List<Extraction> extractions() {
        return List.of();
    }

    // how the pipe takes items nobody asked for, empty if it does not
    // the network keeps an index of these, so it has to be told when they change, see LogisticsNetwork.sinksChanged
    default List<SinkRule> sinkRules() {
        return List.of();
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

    // counts of the items that went through this pipe, kept by the pipe because part of them is saved with it
    default PipeStatistics statistics() {
        return new PipeStatistics();
    }

    // own name for satellites, the chosen satellite for crafting pipes, empty for none
    default String satelliteName() {
        return "";
    }

    // supplier pipes only, how many of each item the inventory should hold
    default Map<ItemType, Integer> supplyTargets() {
        return Map.of();
    }

    // system entrance and destination pipes: the id on their frequency card, the same on both pairs them
    default Optional<UUID> frequency() {
        return Optional.empty();
    }

    // fluid pipes only
    default Optional<FluidNode> fluidNode() {
        return Optional.empty();
    }

    // empty for pipes that never carry the energy of power providers
    default Optional<PowerNode> powerNode() {
        return Optional.empty();
    }
}
