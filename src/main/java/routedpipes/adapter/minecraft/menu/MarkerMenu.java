package routedpipes.adapter.minecraft.menu;

import java.util.Map;

import routedpipes.domain.item.ItemType;
import net.minecraft.world.item.ItemStack;

// a menu whose first slots are markers that a client may set directly, for example from a recipe viewer
public interface MarkerMenu {

    // marker slots are the menu slots 0 up to this
    int markerSlots();

    void setMarker(int slot, ItemStack stack);

    // what the network and the block itself have, to pick among the items a recipe viewer slot takes
    default Map<ItemType, Integer> available() {
        return Map.of();
    }
}
