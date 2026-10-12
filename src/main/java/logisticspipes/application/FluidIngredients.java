package logisticspipes.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import logisticspipes.domain.crafting.FluidInput;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.PackedFluid;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.request.Stock;

// the fluids the crafters of a request take, each under the item that stands for it in the plan
// a plan for items knows nothing of fluid, so the fluid in the providers is handed to it as stock of those items
final class FluidIngredients {

    private final Map<ItemType, FluidType> fluids = new LinkedHashMap<>();

    void add(FluidInput input) {
        fluids.put(input.asItem(), input.fluid());
    }

    boolean isEmpty() {
        return fluids.isEmpty();
    }

    // empty for an item that is no fluid ingredient
    Optional<FluidType> fluidOf(ItemType item) {
        return Optional.ofNullable(fluids.get(item));
    }

    // what the fluid providers hold of these fluids, fluidStock lists it as packed fluid
    List<Stock> stock(List<Stock> fluidStock) {
        List<Stock> stock = new ArrayList<>();
        for (Stock entry : fluidStock) {
            Map<ItemType, Integer> held = new LinkedHashMap<>();
            fluids.forEach((item, fluid) -> {
                int amount = entry.items().getOrDefault(new PackedFluid(fluid), 0);
                if (amount > 0) {
                    held.put(item, amount);
                }
            });
            if (!held.isEmpty()) {
                stock.add(new Stock(entry.provider(), held));
            }
        }
        return stock;
    }
}
