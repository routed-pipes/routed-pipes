package routedpipes.adapter.neoforge;

import java.util.List;

import routedpipes.adapter.minecraft.menu.MenuRequests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// client to server: a slot of the open menu takes any of these items, the server picks the one it can get best
public record ChooseMarkerPayload(int containerId, int slot, List<ItemStack> choices) implements CustomPacketPayload {

    public static final Type<ChooseMarkerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RoutedPipes.MODID, "choose_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseMarkerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseMarkerPayload::containerId,
            ByteBufCodecs.VAR_INT, ChooseMarkerPayload::slot,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MenuRequests.MAX_CHOICES)), ChooseMarkerPayload::choices,
            ChooseMarkerPayload::new);

    @Override
    public Type<ChooseMarkerPayload> type() {
        return TYPE;
    }
}
