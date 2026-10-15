package routedpipes.adapter.fabric;

import java.util.ArrayList;
import java.util.List;

import routedpipes.adapter.minecraft.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TransferItemStorage implements ItemStorage {

    private static Storage<ItemVariant> find(Level level, BlockPos pos, Direction side) {
        return net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side);
    }

    // a storage without slots only has its views, they stand in for slots
    private static List<? extends StorageView<ItemVariant>> slotsOf(Storage<ItemVariant> storage) {
        if (storage == null) {
            return List.of();
        }
        if (storage instanceof SlottedStorage<ItemVariant> slotted) {
            return slotted.getSlots();
        }
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        storage.iterator().forEachRemaining(views::add);
        return views;
    }

    private static ItemStack stackOf(StorageView<ItemVariant> view) {
        return view.isResourceBlank() ? ItemStack.EMPTY : view.getResource().toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE));
    }

    // slots of bins, drawers and the like hold far more than a stack, so the amount comes from the slot
    // and a simulated extraction only tells whether it can be taken at all
    private static boolean extractable(StorageView<ItemVariant> view) {
        return !view.isResourceBlank() && view.getAmount() > 0 && StorageUtil.simulateExtract(view, view.getResource(), 1, null) > 0;
    }

    @Override
    public boolean exists(Level level, BlockPos pos, Direction side) {
        return find(level, pos, side) != null;
    }

    @Override
    public ItemStack insert(Level level, BlockPos pos, Direction side, ItemStack stack, boolean simulate) {
        Storage<ItemVariant> storage = find(level, pos, side);
        if (storage == null || stack.isEmpty()) {
            return stack;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return stack.copyWithCount(stack.getCount() - (int) inserted);
        }
    }

    @Override
    public List<ItemStack> contents(Level level, BlockPos pos, Direction side) {
        List<ItemStack> contents = new ArrayList<>();
        for (StorageView<ItemVariant> view : slotsOf(find(level, pos, side))) {
            if (extractable(view)) {
                contents.add(stackOf(view));
            }
        }
        return contents;
    }

    @Override
    public List<ItemStack> slots(Level level, BlockPos pos, Direction side) {
        List<ItemStack> slots = new ArrayList<>();
        for (StorageView<ItemVariant> view : slotsOf(find(level, pos, side))) {
            slots.add(extractable(view) ? stackOf(view) : ItemStack.EMPTY);
        }
        return slots;
    }

    @Override
    public List<ItemStack> held(Level level, BlockPos pos, Direction side) {
        List<ItemStack> slots = new ArrayList<>();
        for (StorageView<ItemVariant> view : slotsOf(find(level, pos, side))) {
            slots.add(stackOf(view));
        }
        return slots;
    }

    @Override
    public ItemStack insertSlot(Level level, BlockPos pos, Direction side, int slot, ItemStack stack, boolean simulate) {
        List<? extends StorageView<ItemVariant>> slots = slotsOf(find(level, pos, side));
        if (stack.isEmpty() || slot >= slots.size() || !(slots.get(slot) instanceof SingleSlotStorage<ItemVariant> target)) {
            return stack;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = target.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return stack.copyWithCount(stack.getCount() - (int) inserted);
        }
    }

    @Override
    public ItemStack extractSlot(Level level, BlockPos pos, Direction side, int slot, int count) {
        List<? extends StorageView<ItemVariant>> slots = slotsOf(find(level, pos, side));
        if (slot >= slots.size() || slots.get(slot).isResourceBlank()) {
            return ItemStack.EMPTY;
        }
        StorageView<ItemVariant> view = slots.get(slot);
        ItemVariant resource = view.getResource();
        try (Transaction transaction = Transaction.openOuter()) {
            long extracted = view.extract(resource, count, transaction);
            transaction.commit();
            return extracted == 0 ? ItemStack.EMPTY : resource.toStack((int) extracted);
        }
    }

    @Override
    public ItemStack extract(Level level, BlockPos pos, Direction side, ItemStack match, int count) {
        Storage<ItemVariant> storage = find(level, pos, side);
        if (storage == null || match.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long taken = storage.extract(ItemVariant.of(match), count, transaction);
            transaction.commit();
            return taken == 0 ? ItemStack.EMPTY : match.copyWithCount((int) taken);
        }
    }
}
