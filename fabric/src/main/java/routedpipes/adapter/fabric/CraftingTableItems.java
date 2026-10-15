package routedpipes.adapter.fabric;

import java.util.ArrayList;
import java.util.List;

import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

// ingredients go into the storage slots, the last slot hands out freshly crafted results
final class CraftingTableItems {

    private CraftingTableItems() {
    }

    static CombinedSlottedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> of(CraftingTableBlockEntity table) {
        List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();
        InventoryStorage.of(table.storage(), null).getSlots().forEach(slot -> slots.add(new IngredientSlot(slot)));
        slots.add(new ResultSlot(table));
        return new CombinedSlottedStorage<>(slots);
    }

    // ingredients only go in, the table uses them up
    private record IngredientSlot(SingleSlotStorage<ItemVariant> slot) implements SingleSlotStorage<ItemVariant> {

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return slot.insert(resource, maxAmount, transaction);
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        public boolean isResourceBlank() {
            return slot.isResourceBlank();
        }

        @Override
        public ItemVariant getResource() {
            return slot.getResource();
        }

        @Override
        public long getAmount() {
            return slot.getAmount();
        }

        @Override
        public long getCapacity() {
            return slot.getCapacity();
        }
    }

    private static final class ResultSlot implements SingleSlotStorage<ItemVariant> {

        private final CraftingTableBlockEntity table;
        private final PendingAmount pending;

        ResultSlot(CraftingTableBlockEntity table) {
            this.table = table;
            this.pending = new PendingAmount(amount -> table.takeResult((int) amount, false));
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public boolean supportsInsertion() {
            return false;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            ItemStack available = table.available();
            if (available.isEmpty() || !resource.matches(available)) {
                return 0;
            }
            int already = (int) pending.pending();
            int taken = table.takeResult((int) Math.min(already + maxAmount, Integer.MAX_VALUE), true).getCount() - already;
            if (taken <= 0) {
                return 0;
            }
            pending.add(taken, transaction);
            return taken;
        }

        @Override
        public boolean isResourceBlank() {
            return getResource().isBlank();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(table.available());
        }

        @Override
        public long getAmount() {
            return Math.max(0, table.available().getCount() - pending.pending());
        }

        @Override
        public long getCapacity() {
            return 64;
        }
    }
}
