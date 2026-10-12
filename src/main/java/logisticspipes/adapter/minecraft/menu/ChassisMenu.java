package logisticspipes.adapter.minecraft.menu;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.UpgradeItem;
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
            return getContainerSlot() < slotCount.get(DATA_SLOTS);
        }
    }

    public static final int BUTTON_TURN = 0;
    // plus the slot of the module whose settings are to be opened
    public static final int BUTTON_SETTINGS = 10;
    public static final int DATA_SLOTS = 0;
    public static final int DATA_POINTED = 1;
    // not 0 while the chassis has the upgrade that gives modules their own upgrade slots
    public static final int DATA_MODULE_UPGRADES = 2;
    public static final int DATA_COUNT = 3;
    public static final int INVENTORY_TOP = 124;
    private static final int UPGRADE_SLOTS = PipeBlockEntity.MODULE_SLOTS * PipeBlockEntity.UPGRADES_PER_MODULE;

    private final class ModuleUpgradeSlot extends Slot {
        private ModuleUpgradeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        private ItemStack module() {
            return slots.get(getContainerSlot() / PipeBlockEntity.UPGRADES_PER_MODULE).getItem();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && stack.getItem() instanceof UpgradeItem upgrade && module().getItem() instanceof ModuleItem module
                    && upgrade.kind().fitsModule(module.kind());
        }

        @Override
        public int getMaxStackSize() {
            return PipeBlockEntity.UPGRADE_STACK;
        }

        @Override
        public boolean isActive() {
            return slotCount.get(DATA_MODULE_UPGRADES) != 0
                    && getContainerSlot() / PipeBlockEntity.UPGRADES_PER_MODULE < slotCount.get(DATA_SLOTS);
        }
    }

    private final ContainerData slotCount;
    private final Predicate<Player> usable;
    private final Runnable turn;
    private final BiConsumer<Player, Integer> openSettings;

    // client side
    public ChassisMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(PipeBlockEntity.MODULE_SLOTS), new SimpleContainer(UPGRADE_SLOTS),
                new SimpleContainerData(DATA_COUNT), player -> true, () -> { },
                (player, slot) -> { });
    }

    public ChassisMenu(MenuType<?> type, int id, Inventory inventory, Container modules, Container moduleUpgrades,
            ContainerData slotCount,
            Predicate<Player> usable, Runnable turn, BiConsumer<Player, Integer> openSettings) {
        super(type, id);
        this.turn = turn;
        this.openSettings = openSettings;
        this.slotCount = slotCount;
        this.usable = usable;
        for (int slot = 0; slot < PipeBlockEntity.MODULE_SLOTS; slot++) {
            addSlot(new ModuleSlot(modules, slot, 17 + slot * 18, 35));
        }
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            int module = slot / PipeBlockEntity.UPGRADES_PER_MODULE;
            addSlot(new ModuleUpgradeSlot(moduleUpgrades, slot, 17 + module * 18, 70 + slot % PipeBlockEntity.UPGRADES_PER_MODULE * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_TOP + 58));
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
        int own = PipeBlockEntity.MODULE_SLOTS + UPGRADE_SLOTS;
        boolean moved = index < own
                ? moveItemStackTo(stack, own, slots.size(), true)
                : moveItemStackTo(stack, 0, slotCount.get(DATA_SLOTS), false);
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

    // which side the inventory is on that the modules work with, -1 for none
    public int pointed() {
        return slotCount.get(DATA_POINTED);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_SETTINGS && id < BUTTON_SETTINGS + slotCount.get(DATA_SLOTS)) {
            openSettings.accept(player, id - BUTTON_SETTINGS);
            return true;
        }
        if (id != BUTTON_TURN) {
            return false;
        }
        turn.run();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return usable.test(player);
    }
}
