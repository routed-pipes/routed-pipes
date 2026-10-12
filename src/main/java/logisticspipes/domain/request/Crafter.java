package logisticspipes.domain.request;

import java.util.LinkedHashMap;
import java.util.Map;

import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

// a crafting pipe as the planner sees it: what one run makes, which ingredients go to the pipe itself
// and which to its satellite, and how much work it already has
public record Crafter(RouterId router, ItemBatch output, Map<ItemType, Integer> ownInputs, RouterId satellite,
        Map<ItemType, Integer> satelliteInputs, int backlog) {

    public Crafter {
        ownInputs = Map.copyOf(new LinkedHashMap<>(ownInputs));
        satelliteInputs = Map.copyOf(new LinkedHashMap<>(satelliteInputs));
    }

    // everything is delivered to the crafting pipe
    public static Crafter of(RouterId router, Recipe recipe) {
        return new Crafter(router, recipe.output(), recipe.inputTotals(), null, Map.of(), 0);
    }

    // the given part of the recipe is delivered to the satellite, the rest to the crafting pipe
    public static Crafter of(RouterId router, Recipe recipe, RouterId satellite, Map<ItemType, Integer> satelliteInputs, int backlog) {
        Map<ItemType, Integer> own = new LinkedHashMap<>(recipe.inputTotals());
        satelliteInputs.forEach((type, count) -> {
            int rest = own.getOrDefault(type, 0) - count;
            if (rest > 0) {
                own.put(type, rest);
            } else {
                own.remove(type);
            }
        });
        return new Crafter(router, recipe.output(), own, satellite, satelliteInputs, backlog);
    }
}
