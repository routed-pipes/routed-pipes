package routedpipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// the one slot that says which item an item amount sign counts
public class SignItemMenu extends AbstractContainerMenu {

    public static final int ITEM_SLOT = 0;
    public static final int INVENTORY_TOP = 51;

    private final Predicate<Player> usable;

    // client side
    public SignItemMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(1), 0, player -> true);
    }

    // the signs of a pipe keep their items in one container, slot is the one of the sign
    public SignItemMenu(MenuType<?> type, int id, Inventory inventory, Container items, int slot, Predicate<Player> usable) {
        super(type, id);
        this.usable = usable;
        addSlot(new MarkerSlot(items, slot, 80, 20));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_TOP + 58));
        }
    }

    // a click with an item picks it, an empty hand clears the sign
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId != ITEM_SLOT) {
            super.clicked(slotId, button, clickType, player);
        } else if (clickType == ClickType.PICKUP) {
            slots.get(ITEM_SLOT).set(getCarried().copyWithCount(1));
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
