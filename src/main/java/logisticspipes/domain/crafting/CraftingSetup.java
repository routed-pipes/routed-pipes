package logisticspipes.domain.crafting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import logisticspipes.domain.item.ItemBatch;

// one recipe a pipe crafts: which ingredients go to which satellite instead of the pipe itself, how eagerly it is used
// and what else comes out of the machine with every run. satellites go by their name
// fluids are what a run takes on top of the items of the recipe, each goes to a fluid satellite
public record CraftingSetup(Recipe recipe, Map<String, List<ItemBatch>> bySatellite, int priority, Optional<ItemBatch> byproduct,
        List<FluidInput> fluids) {

    public CraftingSetup {
        fluids = List.copyOf(fluids);
        Map<String, List<ItemBatch>> copy = new LinkedHashMap<>();
        bySatellite.forEach((satellite, inputs) -> {
            // a satellite that is set has to be there even when nothing is listed for it
            if (!satellite.isEmpty()) {
                copy.put(satellite, List.copyOf(inputs));
            }
        });
        bySatellite = java.util.Collections.unmodifiableMap(copy);
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

    public CraftingSetup withByproduct(Optional<ItemBatch> extra) {
        return new CraftingSetup(recipe, bySatellite, priority, extra, fluids);
    }

    public CraftingSetup withFluids(List<FluidInput> inputs) {
        return new CraftingSetup(recipe, bySatellite, priority, byproduct, inputs);
    }
}
