package routedpipes.adapter.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// client to server: put this stack as a marker into a slot of the open menu
public record SetMarkerPayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {

    public static final Type<SetMarkerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RoutedPipes.MODID, "set_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetMarkerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetMarkerPayload::containerId,
            ByteBufCodecs.VAR_INT, SetMarkerPayload::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, SetMarkerPayload::stack,
            SetMarkerPayload::new);

    @Override
    public Type<SetMarkerPayload> type() {
        return TYPE;
    }
}
