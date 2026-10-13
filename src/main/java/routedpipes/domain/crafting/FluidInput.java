package routedpipes.domain.crafting;

import routedpipes.domain.fluid.FluidType;
import routedpipes.domain.item.ItemType;

// a fluid a recipe takes with every run, in mB, and the name of the fluid satellite it is delivered to
// asItem is what stands for the fluid where the network counts items, like in the list of what a request is missing
public record FluidInput(FluidType fluid, int amount, String satellite, ItemType asItem) {

    public FluidInput {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }
}
