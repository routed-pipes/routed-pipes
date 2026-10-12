package logisticspipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.UpgradeItem;
import logisticspipes.domain.logistics.PipeKind;
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

// the upgrade slots of a routed pipe, opened with the pipe controller
public class UpgradeMenu extends AbstractContainerMenu {

    private final ContainerData pipeKind;
    private final Predicate<Player> usable;

    // client side
    public UpgradeMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(PipeBlockEntity.UPGRADE_SLOTS), new SimpleContainerData(1), player -> true);
    }

    // the data holds the kind of pipe, the client needs it to know which upgrades fit
    public UpgradeMenu(MenuType<?> type, int id, Inventory inventory, Container upgrades, ContainerData pipeKind,
            Predicate<Player> usable) {
        super(type, id);
        this.pipeKind = pipeKind;
        this.usable = usable;
        for (int slot = 0; slot < PipeBlockEntity.UPGRADE_SLOTS; slot++) {
            addSlot(new Slot(upgrades, slot, 8 + slot * 18, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind().fitsPipe(kind());
                }

                @Override
                public int getMaxStackSize() {
                    return PipeBlockEntity.UPGRADE_STACK;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(pipeKind);
    }

    private PipeKind kind() {
        return PipeKind.values()[Math.floorMod(pipeKind.get(0), PipeKind.values().length)];
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PipeBlockEntity.UPGRADE_SLOTS
                ? moveItemStackTo(stack, PipeBlockEntity.UPGRADE_SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, PipeBlockEntity.UPGRADE_SLOTS, false);
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
