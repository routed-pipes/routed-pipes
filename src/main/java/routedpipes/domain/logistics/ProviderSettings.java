package routedpipes.domain.logistics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;

// what a provider hands out: a mode for the slots and a list of items that is either all it gives or all it keeps
public record ProviderSettings(ProviderMode mode, Set<ItemType> filter, boolean exclude) {

    public static final ProviderSettings EVERYTHING = new ProviderSettings(ProviderMode.NORMAL, Set.of(), false);

    public ProviderSettings {
        filter = Set.copyOf(filter);
    }

    // an empty list never holds anything back
    public boolean offers(ItemType type) {
        return filter.isEmpty() || exclude != filter.contains(type);
    }

    // how many items may be taken out of each slot, empty slots are null
    public int[] takeable(List<ItemBatch> slots) {
        int[] counts = mode.takeable(slots);
        for (int slot = 0; slot < counts.length; slot++) {
            if (slots.get(slot) != null && !offers(slots.get(slot).type())) {
                counts[slot] = 0;
            }
        }
        return counts;
    }

    public Map<ItemType, Integer> stock(List<ItemBatch> slots) {
        int[] counts = takeable(slots);
        Map<ItemType, Integer> stock = new LinkedHashMap<>();
        for (int slot = 0; slot < counts.length; slot++) {
            if (counts[slot] > 0) {
                stock.merge(slots.get(slot).type(), counts[slot], Integer::sum);
            }
        }
        return stock;
    }
}
