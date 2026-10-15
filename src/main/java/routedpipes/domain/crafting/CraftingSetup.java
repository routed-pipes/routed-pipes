package routedpipes.domain.crafting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;

// one recipe a pipe crafts: which ingredients go to which satellite instead of the pipe itself, how eagerly it is used
// and what else comes out of the machine with every run. satellites go by their name
// fluids are what a run takes on top of the items of the recipe, each goes to a fluid satellite
// held is how many of its kept ingredients the machine already has
public record CraftingSetup(Recipe recipe, Map<String, List<ItemBatch>> bySatellite, int priority, Optional<ItemBatch> byproduct,
        List<FluidInput> fluids, Map<ItemType, Integer> held) {

    public CraftingSetup {
        fluids = List.copyOf(fluids);
        held = Map.copyOf(held);
        Map<String, List<ItemBatch>> copy = new LinkedHashMap<>();
        bySatellite.forEach((satellite, inputs) -> {
            // a satellite that is set has to be there even when nothing is listed for it
            if (!satellite.isEmpty()) {
                copy.put(satellite, List.copyOf(inputs));
            }
        });
        bySatellite = java.util.Collections.unmodifiableMap(copy);
    }

    public CraftingSetup(Recipe recipe, Map<String, List<ItemBatch>> bySatellite, int priority, Optional<ItemBatch> byproduct,
            List<FluidInput> fluids) {
        this(recipe, bySatellite, priority, byproduct, fluids, Map.of());
    }

    // a recipe without fluids
    public CraftingSetup(Recipe recipe, Map<String, List<ItemBatch>> bySatellite, int priority, Optional<ItemBatch> byproduct) {
        this(recipe, bySatellite, priority, byproduct, List.of());
    }

    // one satellite for the given part of the recipe, an empty name for none
    public CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, String satellite, int priority) {
        this(recipe, Map.of(satellite, satelliteInputs), priority, Optional.empty());
    }

    // the same with the number satellites used to have, 0 for none
    public CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, int satellite, int priority, Optional<ItemBatch> byproduct) {
        this(recipe, Map.of(SatelliteName.ofNumber(satellite), satelliteInputs), priority, byproduct);
    }

    public CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, int satellite, int priority) {
        this(recipe, satelliteInputs, satellite, priority, Optional.empty());
    }

    public CraftingSetup withKept(Recipe other, Map<ItemType, Integer> inMachine) {
        return new CraftingSetup(other, bySatellite, priority, byproduct, fluids, inMachine);
    }

    public CraftingSetup withByproduct(Optional<ItemBatch> extra) {
        return new CraftingSetup(recipe, bySatellite, priority, extra, fluids, held);
    }

    public CraftingSetup withFluids(List<FluidInput> inputs) {
        return new CraftingSetup(recipe, bySatellite, priority, byproduct, inputs, held);
    }
}
