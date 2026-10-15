package routedpipes.adapter.minecraft.menu;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import routedpipes.adapter.minecraft.RequestTable;
import routedpipes.domain.item.ItemType;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// recipe grid, buffer and sort slot of a request table
// slots that are never shown carry what the network lacks for the chosen number of crafts
public class RequestTableMenu extends AbstractContainerMenu implements MarkerMenu, TextMenu {

    // what the table does for the buttons of its screen
    public interface Actions {
        ItemStack result();

        // empty if the buffer lacks something
        ItemStack craftOne();

        // into the buffer
        void keep(ItemStack stack);

        int pending();

        Map<ItemType, Integer> missing(int crafts);

        void order(Player player, int crafts);

        void orderAndCraft(Player player, int crafts);

        void storeAll();

        void openList(Player player);

        void openDisk(Player player);
    }

    public static final int BUTTON_CLEAR = 0;
    public static final int BUTTON_ORDER = 1;
    public static final int BUTTON_CRAFT = 2;
    public static final int BUTTON_LIST = 3;
    public static final int BUTTON_DISK = 4;
    public static final int BUTTON_STORE_ALL = 5;
    public static final int TEXT_AMOUNT = 0;
    public static final int MAX_AMOUNT = 999;
    public static final int RESULT_SLOT = RequestTable.GRID_SLOTS;
    public static final int MISSING_SLOTS = 7;
    private static final int FIRST_BUFFER_SLOT = RESULT_SLOT + 1;
    public static final int SORT_SLOT = FIRST_BUFFER_SLOT + RequestTable.BUFFER_SLOTS;
    private static final int FIRST_PLAYER_SLOT = SORT_SLOT + 1;
    // added last so that the slots before it keep their numbers
    public static final int DISK_SLOT = FIRST_PLAYER_SLOT + 36;
    private static final int DATA_AMOUNT = 0;
    private static final int DATA_PENDING = 1;
    // how often the network is asked what it lacks
    private static final int MISSING_REFRESH = 10;

    private final Container preview = new SimpleContainer(1);
    private final Container missing = new SimpleContainer(MISSING_SLOTS);
    private final ContainerData data = new SimpleContainerData(2);
    private final Predicate<Player> usable;
    private final Actions actions;
    private int sinceMissing = MISSING_REFRESH;

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
        addSlot(new MarkerSlot(preview, 0, 90, 36));
        for (int slot = 0; slot < RequestTable.BUFFER_SLOTS; slot++) {
            addSlot(new Slot(buffer, slot, 8 + slot % 9 * 18, 118 + slot / 9 * 18));
        }
        addSlot(new Slot(sort, 0, 62, 97));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 188 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 246));
        }
        addSlot(new DiskSlot(disk, 0, 8, 97));
        for (int slot = 0; slot < MISSING_SLOTS; slot++) {
            addSlot(new HiddenSlot(missing, slot));
        }
        data.set(DATA_AMOUNT, 1);
        addDataSlots(data);
    }

    public boolean holdsDisk() {
        return slots.get(DISK_SLOT).hasItem();
    }

    public int amount() {
        return data.get(DATA_AMOUNT);
    }

    // crafts the table still makes once their ingredients are in
    public int pending() {
        return data.get(DATA_PENDING);
    }

    // what the network lacks for the chosen number of crafts, see Shortfall
    public List<ItemStack> missing() {
        return Shortfall.read(missing);
    }

    // the result is worked out on the server and shown through a slot, and so is what is missing
    @Override
    public void broadcastChanges() {
        if (actions != null) {
            preview.setItem(0, actions.result());
            data.set(DATA_PENDING, actions.pending());
            if (++sinceMissing >= MISSING_REFRESH) {
                sinceMissing = 0;
                Shortfall.write(missing, actions.missing(amount()));
            }
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

    @Override
    public void setText(int field, String text) {
        if (field != TEXT_AMOUNT) {
            return;
        }
        try {
            data.set(DATA_AMOUNT, Mth.clamp(Integer.parseInt(text.trim()), 1, MAX_AMOUNT));
        } catch (NumberFormatException e) {
            data.set(DATA_AMOUNT, 1);
        }
        sinceMissing = MISSING_REFRESH;
    }

    // grid slots remember the held item, a click on the result crafts onto the cursor, with shift into the inventory
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < RequestTable.GRID_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                slots.get(slotId).set(getCarried().copyWithCount(1));
                sinceMissing = MISSING_REFRESH;
            }
        } else if (slotId == RESULT_SLOT) {
            if (actions != null && clickType == ClickType.PICKUP) {
                craftOntoCursor();
            } else if (actions != null && clickType == ClickType.QUICK_MOVE) {
                craftIntoInventory();
            }
        } else {
            super.clicked(slotId, button, clickType, player);
        }
    }

    private void craftOntoCursor() {
        ItemStack carried = getCarried();
        ItemStack result = actions.result();
        boolean fits = carried.isEmpty() || ItemStack.isSameItemSameComponents(carried, result)
                && carried.getCount() + result.getCount() <= carried.getMaxStackSize();
        if (result.isEmpty() || !fits) {
            return;
        }
        ItemStack crafted = actions.craftOne();
        if (carried.isEmpty()) {
            setCarried(crafted);
        } else {
            carried.grow(crafted.getCount());
        }
    }

    // as often as the buffer allows, what no longer fits stays in the buffer
    private void craftIntoInventory() {
        while (true) {
            ItemStack crafted = actions.craftOne();
            if (crafted.isEmpty()) {
                return;
            }
            moveItemStackTo(crafted, FIRST_PLAYER_SLOT, DISK_SLOT, true);
            if (!crafted.isEmpty()) {
                actions.keep(crafted);
                return;
            }
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
            case BUTTON_ORDER -> actions.order(player, amount());
            case BUTTON_CRAFT -> actions.orderAndCraft(player, amount());
            case BUTTON_STORE_ALL -> actions.storeAll();
            case BUTTON_LIST -> actions.openList(player);
            case BUTTON_DISK -> actions.openDisk(player);
            default -> {
                return false;
            }
        }
        sinceMissing = MISSING_REFRESH;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < FIRST_BUFFER_SLOT || index > DISK_SLOT || !slot.hasItem()) {
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
