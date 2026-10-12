package logisticspipes.adapter.minecraft.client;

import net.minecraft.world.item.ItemStack;

// how the client tells the server to set a marker, the loader plugs in its way of sending
public final class ClientMarkers {

    @FunctionalInterface
    public interface Sender {
        void send(int containerId, int slot, ItemStack stack);
    }

    // recipe viewer plugins are created by the viewer, so they cannot be handed this any other way
    private static Sender sender = (containerId, slot, stack) -> { };

    private ClientMarkers() {
    }

    public static void setSender(Sender newSender) {
        sender = newSender;
    }

    public static void set(int containerId, int slot, ItemStack stack) {
        sender.send(containerId, slot, stack);
    }
}
