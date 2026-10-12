package logisticspipes.domain.crafting;

import java.util.List;
import java.util.Optional;

import logisticspipes.domain.item.ItemBatch;

// one recipe a pipe crafts: which part of it goes to which satellite, 0 for none, how eagerly it is used
// and what else comes out of the machine with every run
public record CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, int satellite, int priority, Optional<ItemBatch> byproduct) {

    public CraftingSetup {
        satelliteInputs = List.copyOf(satelliteInputs);
    }

    public CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, int satellite, int priority) {
        this(recipe, satelliteInputs, satellite, priority, Optional.empty());
    }

    public CraftingSetup withByproduct(Optional<ItemBatch> extra) {
        return new CraftingSetup(recipe, satelliteInputs, satellite, priority, extra);
    }
}
