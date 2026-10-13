package routedpipes.adapter.minecraft.menu;

import routedpipes.adapter.minecraft.DiskItem;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// holds one disk and nothing else
final class DiskSlot extends Slot {

    DiskSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof DiskItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
