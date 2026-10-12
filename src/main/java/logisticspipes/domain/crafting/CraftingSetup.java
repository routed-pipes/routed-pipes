package logisticspipes.domain.crafting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import logisticspipes.domain.item.ItemBatch;

// one recipe a pipe crafts: which ingredients go to which satellite instead of the pipe itself, how eagerly it is used
// and what else comes out of the machine with every run
public record CraftingSetup(Recipe recipe, Map<Integer, List<ItemBatch>> bySatellite, int priority, Optional<ItemBatch> byproduct) {

    public CraftingSetup {
        Map<Integer, List<ItemBatch>> copy = new LinkedHashMap<>();
        bySatellite.forEach((satellite, inputs) -> {
            // a satellite that is set has to be there even when nothing is listed for it
            if (satellite != 0) {
                copy.put(satellite, List.copyOf(inputs));
            }
        });
        bySatellite = java.util.Collections.unmodifiableMap(copy);
    }

    // one satellite for the given part of the recipe, 0 for none
    public CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, int satellite, int priority, Optional<ItemBatch> byproduct) {
        this(recipe, Map.of(satellite, satelliteInputs), priority, byproduct);
    }

    public CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, int satellite, int priority) {
        this(recipe, satelliteInputs, satellite, priority, Optional.empty());
    }

    public CraftingSetup withByproduct(Optional<ItemBatch> extra) {
        return new CraftingSetup(recipe, bySatellite, priority, extra);
    }
}
