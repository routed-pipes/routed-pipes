package routedpipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import routedpipes.adapter.minecraft.RequestTable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// recipe grid, buffer and sort slot of a request table
public class RequestTableMenu extends AbstractContainerMenu implements MarkerMenu {

    // what the table does for the buttons of its screen
    public interface Actions {
        ItemStack result();

        void craft();

        void order(Player player, int crafts, boolean missingOnly);

        void openList(Player player);

        void openDisk(Player player);
    }

    public static final int BUTTON_CLEAR = 0;
    public static final int BUTTON_ORDER_MISSING = 1;
    public static final int BUTTON_ORDER_ONE = 2;
    public static final int BUTTON_ORDER_TEN = 3;
    public static final int BUTTON_ORDER_STACK = 4;
    public static final int BUTTON_LIST = 5;
    public static final int BUTTON_DISK = 6;
    public static final int RESULT_SLOT = RequestTable.GRID_SLOTS;
    private static final int FIRST_BUFFER_SLOT = RESULT_SLOT + 1;
    private static final int FIRST_PLAYER_SLOT = FIRST_BUFFER_SLOT + RequestTable.BUFFER_SLOTS + 1;
    // added last so that the slots before it keep their numbers
    public static final int DISK_SLOT = FIRST_PLAYER_SLOT + 36;

    private final Container preview = new SimpleContainer(1);
    private final Predicate<Player> usable;
    private final Actions actions;

    // client side
    public RequestTableMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(RequestTable.GRID_SLOTS), new SimpleContainer(RequestTable.BUFFER_SLOTS),
                new SimpleContainer(1), new SimpleContainer(1), player -> true, null);
    }

    public RequestTableMenu(MenuType<?> type, int id, Inventory inventory, Container grid, Container buffer, Container sort,
            Container disk, Predicate<Player> usable, Actions actions) {
        super(type, id);
        this.usable = usable;
        this.actions = actions;
        for (int slot = 0; slot < RequestTable.GRID_SLOTS; slot++) {
            addSlot(new MarkerSlot(grid, slot, 8 + slot % 3 * 18, 18 + slot / 3 * 18));
        }
        addSlot(new MarkerSlot(preview, 0, 92, 36));
        for (int slot = 0; slot < RequestTable.BUFFER_SLOTS; slot++) {
            addSlot(new Slot(buffer, slot, 8 + slot % 9 * 18, 78 + slot / 9 * 18));
        }
        addSlot(new Slot(sort, 0, 152, 18));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 146 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 204));
        }
        addSlot(new DiskSlot(disk, 0, 92, 56));
    }

    public boolean holdsDisk() {
        return slots.get(DISK_SLOT).hasItem();
    }

    // the result is worked out on the server and shown through a slot
    @Override
    public void broadcastChanges() {
        if (actions != null) {
            preview.setItem(0, actions.result());
        }
        super.broadcastChanges();
    }

    @Override
    public int markerSlots() {
        return RequestTable.GRID_SLOTS;
    }

    @Override
    public void setMarker(int slot, ItemStack stack) {
        slots.get(slot).set(stack.copyWithCount(1));
    }

    // grid slots remember the held item, a click on the result crafts once
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < RequestTable.GRID_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                slots.get(slotId).set(getCarried().copyWithCount(1));
            }
        } else if (slotId == RESULT_SLOT) {
            if (actions != null && clickType == ClickType.PICKUP) {
                actions.craft();
            }
        } else {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (actions == null) {
            return false;
        }
        switch (id) {
            case BUTTON_CLEAR -> {
                for (int slot = 0; slot < RequestTable.GRID_SLOTS; slot++) {
                    slots.get(slot).set(ItemStack.EMPTY);
                }
            }
            case BUTTON_ORDER_MISSING -> actions.order(player, 1, true);
            case BUTTON_ORDER_ONE -> actions.order(player, 1, false);
            case BUTTON_ORDER_TEN -> actions.order(player, 10, false);
            case BUTTON_ORDER_STACK -> actions.order(player, 64, false);
            case BUTTON_LIST -> actions.openList(player);
            case BUTTON_DISK -> actions.openDisk(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < FIRST_BUFFER_SLOT || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < FIRST_PLAYER_SLOT || index == DISK_SLOT
                ? moveItemStackTo(stack, FIRST_PLAYER_SLOT, DISK_SLOT, true)
                : moveItemStackTo(stack, FIRST_BUFFER_SLOT, FIRST_BUFFER_SLOT + RequestTable.BUFFER_SLOTS, false);
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
        return usable.test(player);
    }
}
