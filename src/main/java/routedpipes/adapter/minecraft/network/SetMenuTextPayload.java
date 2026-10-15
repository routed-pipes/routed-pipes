package routedpipes.adapter.minecraft.network;

import routedpipes.adapter.minecraft.content.Content;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// client to server: text typed into a field of the open menu
public record SetMenuTextPayload(int containerId, int field, String text) implements CustomPacketPayload {

    public static final Type<SetMenuTextPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Content.MODID, "set_menu_text"));

    public static final StreamCodec<ByteBuf, SetMenuTextPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetMenuTextPayload::containerId,
            ByteBufCodecs.VAR_INT, SetMenuTextPayload::field,
            ByteBufCodecs.stringUtf8(256), SetMenuTextPayload::text,
            SetMenuTextPayload::new);

    @Override
    public Type<SetMenuTextPayload> type() {
        return TYPE;
    }
}
