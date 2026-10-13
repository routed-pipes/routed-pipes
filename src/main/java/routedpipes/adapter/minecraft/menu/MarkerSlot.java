package routedpipes.adapter.minecraft.menu;

import java.util.function.BooleanSupplier;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// shows an item without holding a real one, the menu decides what a click does
final class MarkerSlot extends Slot {

    private final BooleanSupplier active;

    MarkerSlot(Container container, int slot, int x, int y) {
        this(container, slot, x, y, () -> true);
    }

    MarkerSlot(Container container, int slot, int x, int y, BooleanSupplier active) {
        super(container, slot, x, y);
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
