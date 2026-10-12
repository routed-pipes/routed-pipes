package logisticspipes.adapter.minecraft.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// server side of what a client may ask of the menu it has open
public final class MenuRequests {

    private static final int MAX_COUNT = 64;
    private static final int MAX_TEXT = 64;

    private MenuRequests() {
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

    public static void applyText(Player player, int containerId, int field, String text) {
        if (player.containerMenu.containerId != containerId || !(player.containerMenu instanceof TextMenu menu)
                || !player.containerMenu.stillValid(player)) {
            return;
        }
        menu.setText(field, text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
    }
}
