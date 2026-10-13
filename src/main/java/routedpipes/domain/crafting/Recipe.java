package routedpipes.domain.crafting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;

public record Recipe(List<ItemBatch> inputs, ItemBatch output) {
    public Recipe {
        inputs = List.copyOf(inputs);
    }

    // inputs of one run added up per item type
    public Map<ItemType, Integer> inputTotals() {
        Map<ItemType, Integer> totals = new LinkedHashMap<>();
        for (ItemBatch input : inputs) {
            totals.merge(input.type(), input.count(), Integer::sum);
        }
        return totals;
    }

    // how often the recipe can run with what is in stock
    public int runsPossible(Map<ItemType, Integer> stock) {
        int runs = Integer.MAX_VALUE;
        for (Map.Entry<ItemType, Integer> input : inputTotals().entrySet()) {
            runs = Math.min(runs, stock.getOrDefault(input.getKey(), 0) / input.getValue());
        }
        return inputs.isEmpty() ? 0 : runs;
    }
}
