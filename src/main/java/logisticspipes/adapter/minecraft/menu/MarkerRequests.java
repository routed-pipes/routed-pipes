package logisticspipes.adapter.minecraft.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// server side of a client asking to put a marker into the menu it has open
public final class MarkerRequests {

    private static final int MAX_COUNT = 64;

    private MarkerRequests() {
    }

    // everything comes from the client, so nothing is trusted
    public static void apply(Player player, int containerId, int slot, ItemStack stack) {
        if (player.containerMenu.containerId != containerId || !(player.containerMenu instanceof MarkerMenu menu)
                || !player.containerMenu.stillValid(player) || slot < 0 || slot >= menu.markerSlots()
                || !player.containerMenu.getSlot(slot).isActive()) {
            return;
        }
        menu.setMarker(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(stack.getCount(), MAX_COUNT)));
    }
}
