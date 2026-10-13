package routedpipes.domain.request;

import java.util.LinkedHashMap;
import java.util.Map;

import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;

// a crafting pipe as the planner sees it: what one run makes, which ingredients go to the pipe itself
// and which to its satellites, and how much work it already has
public record Crafter(RouterId router, ItemBatch output, Map<ItemType, Integer> ownInputs,
        Map<RouterId, Map<ItemType, Integer>> satelliteInputs, int backlog, int priority) {

    public Crafter {
        ownInputs = Map.copyOf(new LinkedHashMap<>(ownInputs));
        Map<RouterId, Map<ItemType, Integer>> copy = new LinkedHashMap<>();
        satelliteInputs.forEach((satellite, inputs) -> copy.put(satellite, Map.copyOf(inputs)));
        satelliteInputs = java.util.Collections.unmodifiableMap(copy);
    }

    // everything is delivered to the crafting pipe
    public static Crafter of(RouterId router, Recipe recipe) {
        return new Crafter(router, recipe.output(), recipe.inputTotals(), Map.of(), 0, 0);
    }

    // the given part of the recipe is delivered to the satellite, the rest to the crafting pipe
    public static Crafter of(RouterId router, Recipe recipe, RouterId satellite, Map<ItemType, Integer> satelliteInputs, int backlog) {
        return of(router, recipe, satellite == null ? Map.of() : Map.of(satellite, satelliteInputs), backlog);
    }

    // what goes to satellites is taken off what goes to the crafting pipe
    public static Crafter of(RouterId router, Recipe recipe, Map<RouterId, Map<ItemType, Integer>> satelliteInputs, int backlog) {
        Map<ItemType, Integer> own = new LinkedHashMap<>(recipe.inputTotals());
        satelliteInputs.values().forEach(inputs -> inputs.forEach((type, count) -> {
            int rest = own.getOrDefault(type, 0) - count;
            if (rest > 0) {
                own.put(type, rest);
            } else {
                own.remove(type);
            }
        }));
        return new Crafter(router, recipe.output(), own, satelliteInputs, backlog, 0);
    }

    // crafters with a higher priority are used up before any other is asked
    public Crafter withPriority(int value) {
        return new Crafter(router, output, ownInputs, satelliteInputs, backlog, value);
    }

    public Crafter withBacklog(int items) {
        return new Crafter(router, output, ownInputs, satelliteInputs, items, priority);
    }
}
