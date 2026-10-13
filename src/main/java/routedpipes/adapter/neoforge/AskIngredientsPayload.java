package routedpipes.adapter.neoforge;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// client to server: can the network bring these ingredients to the open crafting table, and with order: have it bring them
// the answer comes back through the menu, marked with the number of the question
public record AskIngredientsPayload(int containerId, int question, boolean order, List<ItemStack> ingredients)
        implements CustomPacketPayload {

    public static final Type<AskIngredientsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogisticsPipes.MODID, "ask_ingredients"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AskIngredientsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AskIngredientsPayload::containerId,
            ByteBufCodecs.VAR_INT, AskIngredientsPayload::question,
            ByteBufCodecs.BOOL, AskIngredientsPayload::order,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9)), AskIngredientsPayload::ingredients,
            AskIngredientsPayload::new);

    @Override
    public Type<AskIngredientsPayload> type() {
        return TYPE;
    }
}
