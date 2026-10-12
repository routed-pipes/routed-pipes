package logisticspipes.domain.request;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

// works out where requested items come from: stock first, then crafting, recursively for the ingredients
public final class RequestPlanner {

    private static final class Draft {
        private final Map<RouterId, Map<ItemType, Integer>> stock = new LinkedHashMap<>();
        private final List<Provision> provisions = new ArrayList<>();
        private final List<CraftJob> crafts = new ArrayList<>();
        private final Map<ItemType, Integer> missing = new LinkedHashMap<>();

        private Draft copy() {
            Draft copy = new Draft();
            stock.forEach((provider, items) -> copy.stock.put(provider, new LinkedHashMap<>(items)));
            copy.provisions.addAll(provisions);
            copy.crafts.addAll(crafts);
            copy.missing.putAll(missing);
            return copy;
        }

        private void become(Draft other) {
            stock.clear();
            stock.putAll(other.stock);
            provisions.clear();
            provisions.addAll(other.provisions);
            crafts.clear();
            crafts.addAll(other.crafts);
            missing.clear();
            missing.putAll(other.missing);
        }
    }

    private final List<Stock> stock;
    private final List<Crafter> crafters;

    // stock and crafters are used in the given order, so pass the nearest providers and the least busy crafters first
    public RequestPlanner(List<Stock> stock, List<Crafter> crafters) {
        this.stock = List.copyOf(stock);
        this.crafters = List.copyOf(crafters);
    }

    public RequestPlan plan(RouterId destination, ItemBatch wanted) {
        Draft draft = new Draft();
        for (Stock entry : stock) {
            draft.stock.computeIfAbsent(entry.provider(), provider -> new LinkedHashMap<>()).putAll(entry.items());
        }
        resolve(draft, destination, wanted.type(), wanted.count(), new HashSet<>());
        return new RequestPlan(List.copyOf(draft.provisions), List.copyOf(draft.crafts), Map.copyOf(draft.missing));
    }

    private void resolve(Draft draft, RouterId destination, ItemType type, int count, Set<ItemType> crafting) {
        int remaining = takeFromStock(draft, destination, type, count);
        if (remaining == 0) {
            return;
        }
        // an item that is already being crafted further up must not be crafted again for itself
        if (crafting.add(type)) {
            Draft closest = null;
            for (Crafter crafter : crafters) {
                if (!crafter.output().type().equals(type)) {
                    continue;
                }
                Draft attempt = draft.copy();
                int perRun = crafter.output().count();
                int runs = (remaining + perRun - 1) / perRun;
                for (Map.Entry<ItemType, Integer> input : crafter.ownInputs().entrySet()) {
                    resolve(attempt, crafter.router(), input.getKey(), input.getValue() * runs, crafting);
                }
                for (Map.Entry<ItemType, Integer> input : crafter.satelliteInputs().entrySet()) {
                    resolve(attempt, crafter.satellite(), input.getKey(), input.getValue() * runs, crafting);
                }
                attempt.crafts.add(new CraftJob(crafter.router(), destination, new ItemBatch(type, remaining)));
                if (attempt.missing.equals(draft.missing)) {
                    draft.become(attempt);
                    crafting.remove(type);
                    return;
                }
                if (closest == null) {
                    closest = attempt;
                }
            }
            crafting.remove(type);
            if (closest != null) {
                // report the ingredients that are short, not the crafted item
                draft.become(closest);
                return;
            }
        }
        draft.missing.merge(type, remaining, Integer::sum);
    }

    // returns how many are still needed
    private static int takeFromStock(Draft draft, RouterId destination, ItemType type, int count) {
        int remaining = count;
        for (Map.Entry<RouterId, Map<ItemType, Integer>> entry : draft.stock.entrySet()) {
            if (remaining == 0) {
                break;
            }
            int available = entry.getValue().getOrDefault(type, 0);
            if (available == 0 || entry.getKey().equals(destination)) {
                continue;
            }
            int taken = Math.min(available, remaining);
            entry.getValue().put(type, available - taken);
            draft.provisions.add(new Provision(entry.getKey(), destination, new ItemBatch(type, taken)));
            remaining -= taken;
        }
        return remaining;
    }
}
