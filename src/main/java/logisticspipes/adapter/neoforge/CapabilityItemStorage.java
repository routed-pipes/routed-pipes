package logisticspipes.adapter.neoforge;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.adapter.minecraft.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

final class CapabilityItemStorage implements ItemStorage {

    @Override
    public boolean exists(Level level, BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, Direction side, ItemStack stack, boolean simulate) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        return handler == null ? stack : ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
    }

    @Override
    public List<ItemStack> contents(Level level, BlockPos pos, Direction side) {
        List<ItemStack> contents = new ArrayList<>();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) {
            return contents;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            // simulated so slots that cannot be emptied from this side do not count
            ItemStack stack = handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), true);
            if (!stack.isEmpty()) {
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, Direction side, ItemStack match, int count) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        int taken = 0;
        for (int slot = 0; slot < handler.getSlots() && taken < count; slot++) {
            if (ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), match)) {
                taken += handler.extractItem(slot, count - taken, false).getCount();
            }
        }
        return taken == 0 ? ItemStack.EMPTY : match.copyWithCount(taken);
    }
}
