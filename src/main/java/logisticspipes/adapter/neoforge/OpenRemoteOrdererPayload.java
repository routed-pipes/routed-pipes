package logisticspipes.adapter.neoforge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// client to server: the key for the remote orderer was pressed, the server looks for the orderer itself
public record OpenRemoteOrdererPayload() implements CustomPacketPayload {

    public static final Type<OpenRemoteOrdererPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogisticsPipes.MODID, "open_remote_orderer"));

    public static final StreamCodec<ByteBuf, OpenRemoteOrdererPayload> CODEC = StreamCodec.unit(new OpenRemoteOrdererPayload());

    @Override
    public Type<OpenRemoteOrdererPayload> type() {
        return TYPE;
    }
}
