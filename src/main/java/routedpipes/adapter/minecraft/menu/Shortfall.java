package routedpipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// what the network cannot bring, sent to the client through slots nobody sees
// a missing item carries how many are missing in its data, the stack count is always 1
public final class Shortfall {

    private static final String COUNT_TAG = "LogisticsPipesCount";

    private Shortfall() {
    }

    // fills the container sorted by item id, what does not fit is left out
    // returns how many kinds are missing, also those left out
    static int write(Container slots, Map<ItemType, Integer> missing) {
        List<Map.Entry<ItemType, Integer>> sorted = new ArrayList<>(missing.entrySet());
        sorted.sort(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(((McItemType) entry.getKey()).item())));
        for (int slot = 0; slot < slots.getContainerSize(); slot++) {
            ItemStack stack = ItemStack.EMPTY;
            if (slot < sorted.size()) {
                int count = sorted.get(slot).getValue();
                stack = McItemType.stackOf(new ItemBatch(sorted.get(slot).getKey(), 1));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(COUNT_TAG, count));
            }
            slots.setItem(slot, stack);
        }
        return sorted.size();
    }

    static List<ItemStack> read(Container slots) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < slots.getContainerSize(); slot++) {
            if (!slots.getItem(slot).isEmpty()) {
                stacks.add(slots.getItem(slot).copy());
            }
        }
        return stacks;
    }

    public static int count(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(COUNT_TAG);
    }
}
