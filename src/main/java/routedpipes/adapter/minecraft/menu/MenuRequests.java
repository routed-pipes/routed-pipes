package routedpipes.adapter.minecraft.menu;

import java.util.List;
import java.util.Map;

import routedpipes.domain.item.ItemType;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// server side of what a client may ask of the menu it has open
public final class MenuRequests {

    private static final int MAX_COUNT = 64;
    private static final int MAX_TEXT = 64;
    private static final int MAX_INGREDIENTS = 9;
    public static final int MAX_CHOICES = 64;

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

    // a recipe viewer slot that takes any of these items, the menu picks the one it can get best
    public static void choose(Player player, int containerId, int slot, List<ItemStack> choices) {
        if (choices.isEmpty() || choices.size() > MAX_CHOICES || !(player.containerMenu instanceof MarkerMenu menu)
                || player.containerMenu.containerId != containerId || !player.containerMenu.stillValid(player)) {
            return;
        }
        apply(player, containerId, slot, MarkerChoice.pick(choices, menu.available()));
    }

    // a recipe viewer asks whether the network can bring the ingredients of a recipe to the open table
    // every slot comes with what it takes, picked the same way as when the recipe is moved into the grid
    public static void askIngredients(Player player, int containerId, int question, List<List<ItemStack>> choices) {
        if (player.containerMenu.containerId != containerId || !(player.containerMenu instanceof IngredientMenu menu)
                || !(player.containerMenu instanceof MarkerMenu markers) || !player.containerMenu.stillValid(player)
                || choices.size() > MAX_INGREDIENTS || choices.stream().anyMatch(slot -> slot.size() > MAX_CHOICES)) {
            return;
        }
        boolean picking = choices.stream().anyMatch(slot -> slot.size() > 1);
        Map<ItemType, Integer> available = picking ? markers.available() : Map.of();
        menu.askIngredients(question, choices.stream().map(slot -> slot.isEmpty() ? ItemStack.EMPTY : MarkerChoice.pick(slot, available)).toList());
    }

    public static void applyText(Player player, int containerId, int field, String text) {
        if (player.containerMenu.containerId != containerId || !(player.containerMenu instanceof TextMenu menu)
                || !player.containerMenu.stillValid(player)) {
            return;
        }
        menu.setText(field, text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
    }
}
