package logisticspipes.adapter.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

// opens the request screen of a remote orderer carried anywhere in the inventory, not bound to a key until the player picks one
public final class RemoteOrdererKey {

    public static final KeyMapping MAPPING = new KeyMapping("key.routedpipes.remote_orderer",
            InputConstants.UNKNOWN.getValue(), "key.categories.routedpipes");

    private RemoteOrdererKey() {
    }

    // once per client tick
    public static void tick() {
        while (MAPPING.consumeClick()) {
            ClientMenuLink.openRemoteOrderer();
        }
    }
}
