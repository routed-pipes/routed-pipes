package routedpipes.adapter.neoforge;

import java.util.ArrayList;
import java.util.List;

import routedpipes.adapter.minecraft.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class CapabilityItemStorage implements ItemStorage {

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
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        return handler == null ? new ArrayList<>() : contentsOf(handler);
    }

    // slots of bins, drawers and the like hold far more than a stack but hand out one stack per call,
    // so the amount comes from the slot and a simulated extraction only tells whether it can be taken at all
    public static List<ItemStack> contentsOf(IItemHandler handler) {
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && !handler.extractItem(slot, 1, true).isEmpty()) {
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    @Override
    public List<ItemStack> slots(Level level, BlockPos pos, Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            slots.add(stack.isEmpty() || handler.extractItem(slot, 1, true).isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return slots;
    }

    @Override
    public List<ItemStack> held(Level level, BlockPos pos, Direction side) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
            slots.add(handler.getStackInSlot(slot).copy());
        }
        return slots;
    }

    @Override
    public ItemStack insertSlot(Level level, BlockPos pos, Direction side, int slot, ItemStack stack, boolean simulate) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        return handler == null || slot >= handler.getSlots() ? stack : handler.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractSlot(Level level, BlockPos pos, Direction side, int slot, int count) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        return handler == null || slot >= handler.getSlots() ? ItemStack.EMPTY : handler.extractItem(slot, count, false);
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
