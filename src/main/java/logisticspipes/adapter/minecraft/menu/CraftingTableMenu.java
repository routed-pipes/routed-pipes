package logisticspipes.adapter.minecraft.menu;

import logisticspipes.adapter.minecraft.CraftingTableBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// recipe grid of markers, a preview of the result and the ingredient storage
public class CraftingTableMenu extends AbstractContainerMenu implements MarkerMenu {

    private static final int PATTERN_END = CraftingTableBlockEntity.PATTERN_SLOTS;
    private static final int RESULT_SLOT = PATTERN_END;
    private static final int STORAGE_START = RESULT_SLOT + 1;
    private static final int STORAGE_END = STORAGE_START + CraftingTableBlockEntity.STORAGE_SLOTS;

    private final Container preview = new SimpleContainer(1);
    private final CraftingTableBlockEntity table;

    // client side
    public CraftingTableMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(CraftingTableBlockEntity.PATTERN_SLOTS),
                new SimpleContainer(CraftingTableBlockEntity.STORAGE_SLOTS), null);
    }

    public CraftingTableMenu(MenuType<?> type, int id, Inventory inventory, CraftingTableBlockEntity table) {
        this(type, id, inventory, table.pattern(), table.storage(), table);
    }

    private CraftingTableMenu(MenuType<?> type, int id, Inventory inventory, Container pattern, Container storage,
            CraftingTableBlockEntity table) {
        super(type, id);
        this.table = table;
        for (int slot = 0; slot < CraftingTableBlockEntity.PATTERN_SLOTS; slot++) {
            addSlot(new MarkerSlot(pattern, slot, 30 + slot % 3 * 18, 17 + slot / 3 * 18));
        }
        addSlot(new MarkerSlot(preview, 0, 124, 35));
        for (int slot = 0; slot < CraftingTableBlockEntity.STORAGE_SLOTS; slot++) {
            addSlot(new Slot(storage, slot, 8 + slot % 9 * 18, 84 + slot / 9 * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 134 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 192));
        }
    }

    @Override
    public int markerSlots() {
        return PATTERN_END;
    }

    // the grid only knows which item, never how many
    @Override
    public void setMarker(int slot, ItemStack stack) {
        slots.get(slot).set(stack.copyWithCount(stack.isEmpty() ? 0 : 1));
    }

    @Override
    public void broadcastChanges() {
        if (table != null) {
            preview.setItem(0, table.result());
        }
        super.broadcastChanges();
    }

    // grid slots take a marker of the held item or get cleared, the preview does nothing
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId > RESULT_SLOT) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (slotId < PATTERN_END && clickType == ClickType.PICKUP) {
            slots.get(slotId).set(getCarried().copyWithCount(getCarried().isEmpty() ? 0 : 1));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < STORAGE_START || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < STORAGE_END
                ? moveItemStackTo(stack, STORAGE_END, slots.size(), true)
                : moveItemStackTo(stack, STORAGE_START, STORAGE_END, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return table == null || Container.stillValidBlockEntity(table, player);
    }
}
