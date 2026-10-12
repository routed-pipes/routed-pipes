package logisticspipes.adapter.neoforge;

import logisticspipes.adapter.minecraft.CraftingTableBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

// ingredients go into the storage slots, the last slot hands out freshly crafted results
final class CraftingTableItemHandler implements IItemHandler {

    private final CraftingTableBlockEntity table;
    private final InvWrapper storage;

    CraftingTableItemHandler(CraftingTableBlockEntity table) {
        this.table = table;
        this.storage = new InvWrapper(table.storage());
    }

    private boolean isResult(int slot) {
        return slot == storage.getSlots();
    }

    @Override
    public int getSlots() {
        return storage.getSlots() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return isResult(slot) ? table.available() : storage.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return isResult(slot) ? stack : storage.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return isResult(slot) ? table.takeResult(amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return isResult(slot) ? 64 : storage.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !isResult(slot);
    }
}
