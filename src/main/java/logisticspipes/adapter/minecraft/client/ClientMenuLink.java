package logisticspipes.adapter.minecraft.client;

import java.util.List;

import net.minecraft.world.item.ItemStack;

// how the client tells the server about things done in a screen or with a key, the loader plugs in its way of sending
public final class ClientMenuLink {

    public interface Sender {
        void setMarker(int containerId, int slot, ItemStack stack);

        void setText(int containerId, int field, String text);

        void openRemoteOrderer();

        void askIngredients(int containerId, int question, List<ItemStack> ingredients, boolean order);
    }

    // recipe viewer plugins are created by the viewer, so they cannot be handed this any other way
    private static Sender sender = new Sender() {
        @Override
        public void setMarker(int containerId, int slot, ItemStack stack) {
        }

        @Override
        public void setText(int containerId, int field, String text) {
        }

        @Override
        public void openRemoteOrderer() {
        }

        @Override
        public void askIngredients(int containerId, int question, List<ItemStack> ingredients, boolean order) {
        }
    };

    private ClientMenuLink() {
    }

    public static void setSender(Sender newSender) {
        sender = newSender;
    }

    public static void setMarker(int containerId, int slot, ItemStack stack) {
        sender.setMarker(containerId, slot, stack);
    }

    public static void setText(int containerId, int field, String text) {
        sender.setText(containerId, field, text);
    }

    public static void openRemoteOrderer() {
        sender.openRemoteOrderer();
    }

    public static void askIngredients(int containerId, int question, List<ItemStack> ingredients, boolean order) {
        sender.askIngredients(containerId, question, ingredients, order);
    }
}
