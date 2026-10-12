package logisticspipes.domain.logistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;

// a supplier with a pattern upgrade: every listed item belongs into one slot of the inventory
// inventories are given slot by slot, null for an empty slot
// limited means that what does not fit its slot is sent away again and never ends up in another slot
public record PatternSupply(List<Entry> entries, SupplyMode mode, boolean limited) {

    // keep that many of the item in the slot with that number, counted from 0
    public record Entry(int slot, ItemBatch wanted) {
    }

    public PatternSupply {
        entries = List.copyOf(entries);
        // slots have a size, there is no filling all the room there is
        mode = mode == SupplyMode.INFINITE ? SupplyMode.PARTIAL : mode;
    }

    private static int held(Entry entry, List<ItemBatch> slots) {
        ItemBatch held = slots.get(entry.slot());
        return held == null ? 0 : held.count();
    }

    // slots the inventory does not have and slots that hold something else are left alone
    private static boolean usable(Entry entry, List<ItemBatch> slots) {
        if (entry.slot() < 0 || entry.slot() >= slots.size()) {
            return false;
        }
        ItemBatch held = slots.get(entry.slot());
        return held == null || held.type().equals(entry.wanted().type());
    }

    // what to order, one batch per slot that is short
    // what is already on its way counts for the slots in order, so one delivery is not waited for by two of them
    public List<ItemBatch> orders(List<ItemBatch> slots, ToIntFunction<ItemType> onTheWay) {
        Map<ItemType, Integer> coming = new HashMap<>();
        List<ItemBatch> orders = new ArrayList<>();
        for (Entry entry : entries) {
            if (!usable(entry, slots)) {
                continue;
            }
            ItemType type = entry.wanted().type();
            int target = entry.wanted().count();
            int have = held(entry, slots);
            int missing = Math.max(0, target - have);
            int expected = Math.min(missing, coming.computeIfAbsent(type, onTheWay::applyAsInt));
            coming.merge(type, -expected, Integer::sum);
            int amount = mode.amount(target, have, expected, missing);
            if (amount > 0) {
                orders.add(new ItemBatch(type, amount));
            }
        }
        return orders;
    }

    // where an arriving batch goes: how many into which slot, slots in the order they are listed
    // whatever is not placed did not fit the slots it is meant for
    public Map<Integer, Integer> placement(ItemBatch arriving, List<ItemBatch> slots) {
        Map<Integer, Integer> placement = new LinkedHashMap<>();
        int left = arriving.count();
        for (Entry entry : entries) {
            if (left == 0 || !entry.wanted().type().equals(arriving.type()) || !usable(entry, slots)) {
                continue;
            }
            int placed = placement.getOrDefault(entry.slot(), 0);
            int fits = Math.min(left, entry.wanted().count() - held(entry, slots) - placed);
            if (fits > 0) {
                placement.merge(entry.slot(), fits, Integer::sum);
                left -= fits;
            }
        }
        return placement;
    }

    public boolean lists(ItemType type) {
        return entries.stream().anyMatch(entry -> entry.wanted().type().equals(type));
    }
}
