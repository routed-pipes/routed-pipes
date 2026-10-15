package routedpipes.adapter.minecraft.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// only there to be sent to the client
final class HiddenSlot extends Slot {

    HiddenSlot(Container container, int slot) {
        super(container, slot, 0, 0);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
