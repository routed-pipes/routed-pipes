package routedpipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import routedpipes.adapter.minecraft.FrequencyCardItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// the one slot for the frequency card of a system entrance or destination pipe
public class FrequencyCardMenu extends AbstractContainerMenu {

    public static final int CARD_SLOT = 0;
    public static final int INVENTORY_TOP = 51;
    private static final int SLOTS = 37;

    private final Predicate<Player> usable;

    // client side
    public FrequencyCardMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(1), player -> true);
    }

    public FrequencyCardMenu(MenuType<?> type, int id, Inventory inventory, Container card, Predicate<Player> usable) {
        super(type, id);
        this.usable = usable;
        addSlot(new Slot(card, 0, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FrequencyCardItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_TOP + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index == CARD_SLOT
                ? moveItemStackTo(stack, CARD_SLOT + 1, SLOTS, true)
                : moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false);
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
