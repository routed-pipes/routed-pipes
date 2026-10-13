package routedpipes.adapter.neoforge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import routedpipes.adapter.minecraft.HudPanelCodec;
import routedpipes.domain.hud.HudPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// server to client: the panels of the glasses that are new or different and the places that have none any more
// with reset everything the client had before is dropped first
public record HudPanelsPayload(boolean reset, Map<BlockPos, HudPanel> changed, List<BlockPos> gone) implements CustomPacketPayload {

    public static final Type<HudPanelsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RoutedPipes.MODID, "hud_panels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudPanelsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HudPanelsPayload::reset,
            ByteBufCodecs.<RegistryFriendlyByteBuf, BlockPos, HudPanel, Map<BlockPos, HudPanel>>map(LinkedHashMap::new, BlockPos.STREAM_CODEC, HudPanelCodec.CODEC), HudPanelsPayload::changed,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), HudPanelsPayload::gone,
            HudPanelsPayload::new);

    @Override
    public Type<HudPanelsPayload> type() {
        return TYPE;
    }
}
