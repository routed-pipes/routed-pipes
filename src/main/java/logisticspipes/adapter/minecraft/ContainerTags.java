package logisticspipes.adapter.minecraft;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

// saves and loads small inventories slot by slot
final class ContainerTags {

    private ContainerTags() {
    }

    static CompoundTag save(Container container, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, container.getItem(slot));
        }
        return ContainerHelper.saveAllItems(new CompoundTag(), items, registries);
    }

    static void load(Container container, CompoundTag tag, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < items.size(); slot++) {
            container.setItem(slot, items.get(slot));
        }
    }
}
