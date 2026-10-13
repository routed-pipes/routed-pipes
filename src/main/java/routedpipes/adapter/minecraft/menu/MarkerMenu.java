package routedpipes.adapter.minecraft.menu;

import net.minecraft.world.item.ItemStack;

// a menu whose first slots are markers that a client may set directly, for example from a recipe viewer
public interface MarkerMenu {

    // marker slots are the menu slots 0 up to this
    int markerSlots();

    void setMarker(int slot, ItemStack stack);
}
