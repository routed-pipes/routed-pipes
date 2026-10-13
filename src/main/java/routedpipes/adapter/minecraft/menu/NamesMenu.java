package routedpipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import routedpipes.adapter.minecraft.ModuleItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// settings of the sinks for mods, tags and creative tabs: a list of names, picked from what an item put in the first slot has
public class NamesMenu extends AbstractContainerMenu implements MarkerMenu, TextMenu {

    public static final int FIELD_ADD = 0;
    public static final int FIELD_REMOVE = 1;
    public static final int MAX_NAMES = 9;
    public static final int SLOT_ITEM = 0;
    public static final int SLOT_MODULE = 1;

    private final Container item = new SimpleContainer(1);
    private final Container module = new SimpleContainer(1);
    private final Predicate<Player> usable;
    private final Runnable changed;

    // client side
    public NamesMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, ItemStack.EMPTY, player -> true, () -> { });
    }

    // the module is shown in a slot of its own so that its list reaches the client
    public NamesMenu(MenuType<?> type, int id, Inventory inventory, ItemStack moduleStack, Predicate<Player> usable,
            Runnable changed) {
        super(type, id);
        this.usable = usable;
        this.changed = changed;
        module.setItem(0, moduleStack);
        addSlot(new MarkerSlot(item, 0, 8, 18));
        addSlot(new MarkerSlot(module, 0, 152, 18));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 174 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 232));
        }
    }

    public ItemStack module() {
        return module.getItem(0);
    }

    public ItemStack item() {
        return item.getItem(0);
    }

    public List<String> names() {
        return ModuleItem.namesOf(module());
    }

    @Override
    public int markerSlots() {
        return 1;
    }

    @Override
    public void setMarker(int slot, ItemStack stack) {
        item.setItem(0, stack.copyWithCount(1));
    }

    @Override
    public void setText(int field, String text) {
        List<String> names = new ArrayList<>(names());
        boolean done = field == FIELD_ADD
                ? !text.isBlank() && names.size() < MAX_NAMES && !names.contains(text) && names.add(text)
                : field == FIELD_REMOVE && names.remove(text);
        if (done) {
            ModuleItem.setNames(module(), names);
            changed.run();
        }
    }

    // a click with an item shows it in the first slot, an empty hand clears that
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == SLOT_ITEM) {
            item.setItem(0, getCarried().copyWithCount(1));
        } else if (slotId != SLOT_MODULE) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return usable.test(player);
    }
}
