package routedpipes.domain.crafting;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;

// kept are ingredients that come out of the craft unchanged, like a programmer, they are needed once and not per run
public record Recipe(List<ItemBatch> inputs, ItemBatch output, List<ItemBatch> kept) {
    public Recipe {
        inputs = List.copyOf(inputs);
        kept = List.copyOf(kept);
    }

    public Recipe(List<ItemBatch> inputs, ItemBatch output) {
        this(inputs, output, List.of());
    }

    // inputs of one run added up per item type
    public Map<ItemType, Integer> inputTotals() {
        return totals(inputs);
    }

    public Map<ItemType, Integer> keptTotals() {
        return totals(kept);
    }

    private static Map<ItemType, Integer> totals(List<ItemBatch> batches) {
        Map<ItemType, Integer> totals = new LinkedHashMap<>();
        for (ItemBatch batch : batches) {
            totals.merge(batch.type(), batch.count(), Integer::sum);
        }
        return totals;
    }

    // how often the recipe can run with what is in stock, what is kept has to be there once
    public int runsPossible(Map<ItemType, Integer> stock) {
        Map<ItemType, Integer> left = new HashMap<>(stock);
        for (Map.Entry<ItemType, Integer> needed : keptTotals().entrySet()) {
            int rest = left.getOrDefault(needed.getKey(), 0) - needed.getValue();
            if (rest < 0) {
                return 0;
            }
            left.put(needed.getKey(), rest);
        }
        int runs = Integer.MAX_VALUE;
        for (Map.Entry<ItemType, Integer> input : inputTotals().entrySet()) {
            runs = Math.min(runs, left.getOrDefault(input.getKey(), 0) / input.getValue());
        }
        return inputs.isEmpty() ? 0 : runs;
    }
}
