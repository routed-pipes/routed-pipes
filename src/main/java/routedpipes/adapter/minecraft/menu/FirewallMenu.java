package routedpipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import routedpipes.adapter.minecraft.FirewallSettings;
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

// the list of a firewall pipe and its five switches, the button id is the number of the switch
public class FirewallMenu extends AbstractContainerMenu implements MarkerMenu {

    private final ContainerData flags;
    private final Predicate<Player> usable;

    // client side
    public FirewallMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(FirewallSettings.SLOTS), new SimpleContainerData(FirewallSettings.FLAGS),
                player -> true);
    }

    public FirewallMenu(MenuType<?> type, int id, Inventory inventory, Container list, ContainerData flags,
            Predicate<Player> usable) {
        super(type, id);
        this.flags = flags;
        this.usable = usable;
        for (int slot = 0; slot < FirewallSettings.SLOTS; slot++) {
            addSlot(new MarkerSlot(list, slot, 8 + slot % 6 * 18, 18 + slot / 6 * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }
        addDataSlots(flags);
    }

    public boolean flag(int index) {
        return flags.get(index) != 0;
    }

    @Override
    public int markerSlots() {
        return FirewallSettings.SLOTS;
    }

    @Override
    public void setMarker(int slot, ItemStack stack) {
        slots.get(slot).set(stack.copyWithCount(1));
    }

    // a click with an item lists it, an empty hand takes it off the list
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= FirewallSettings.SLOTS) {
            super.clicked(slotId, button, clickType, player);
        } else if (clickType == ClickType.PICKUP) {
            slots.get(slotId).set(getCarried().copyWithCount(1));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= FirewallSettings.FLAGS) {
            return false;
        }
        flags.set(id, flag(id) ? 0 : 1);
        return true;
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
