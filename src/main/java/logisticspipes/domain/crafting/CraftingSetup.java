package logisticspipes.domain.crafting;

import java.util.List;

import logisticspipes.domain.item.ItemBatch;

// one recipe a pipe crafts: which part of it goes to which satellite, 0 for none, and how eagerly it is used
public record CraftingSetup(Recipe recipe, List<ItemBatch> satelliteInputs, int satellite, int priority) {

    public CraftingSetup {
        satelliteInputs = List.copyOf(satelliteInputs);
    }
}
