package logisticspipes.domain.logistics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;

// which part of its inventory a provider hands out
public enum ProviderMode {
    NORMAL,
    LEAVE_FIRST,
    LEAVE_LAST,
    LEAVE_FIRST_AND_LAST,
    LEAVE_ONE_PER_STACK,
    LEAVE_ONE_PER_TYPE;

    public ProviderMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    // how many items may be taken out of each slot, empty slots are null
    public int[] takeable(List<ItemBatch> slots) {
        int[] counts = new int[slots.size()];
        boolean skipFirst = this == LEAVE_FIRST || this == LEAVE_FIRST_AND_LAST;
        boolean skipLast = this == LEAVE_LAST || this == LEAVE_FIRST_AND_LAST;
        Set<ItemType> seen = new HashSet<>();
        for (int slot = skipFirst ? 1 : 0; slot < slots.size() - (skipLast ? 1 : 0); slot++) {
            ItemBatch batch = slots.get(slot);
            if (batch == null) {
                continue;
            }
            boolean firstOfType = seen.add(batch.type());
            boolean keepOne = this == LEAVE_ONE_PER_STACK || this == LEAVE_ONE_PER_TYPE && firstOfType;
            counts[slot] = batch.count() - (keepOne ? 1 : 0);
        }
        return counts;
    }
}
