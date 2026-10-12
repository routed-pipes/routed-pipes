package logisticspipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// the module slots of a chassis pipe, how many are usable depends on the chassis
public class ChassisMenu extends AbstractContainerMenu {

    private final class ModuleSlot extends Slot {
        private ModuleSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && stack.getItem() instanceof ModuleItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() < slotCount.get(0);
        }
    }

    private final ContainerData slotCount;
    private final Predicate<Player> usable;

    // client side
    public ChassisMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(PipeBlockEntity.MODULE_SLOTS), new SimpleContainerData(1), player -> true);
    }

    public ChassisMenu(MenuType<?> type, int id, Inventory inventory, Container modules, ContainerData slotCount,
            Predicate<Player> usable) {
        super(type, id);
        this.slotCount = slotCount;
        this.usable = usable;
        for (int slot = 0; slot < PipeBlockEntity.MODULE_SLOTS; slot++) {
            addSlot(new ModuleSlot(modules, slot, 17 + slot * 18, 35));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(slotCount);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PipeBlockEntity.MODULE_SLOTS
                ? moveItemStackTo(stack, PipeBlockEntity.MODULE_SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, slotCount.get(0), false);
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
