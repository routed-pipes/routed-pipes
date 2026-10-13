package routedpipes.adapter.minecraft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

// pairs a system entrance pipe with a system destination pipe: both need a card with the same id
// a card is blank until it is used, using a stack gives all of its cards the same id
public class FrequencyCardItem extends Item {

    private static final String FREQUENCY_TAG = "LogisticsPipesFrequency";

    public FrequencyCardItem(Properties properties) {
        super(properties);
    }

    // empty for a blank card and for anything that is no card
    public static Optional<UUID> frequencyOf(ItemStack stack) {
        if (!(stack.getItem() instanceof FrequencyCardItem)) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(FREQUENCY_TAG) ? Optional.of(tag.getUUID(FREQUENCY_TAG)) : Optional.empty();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (frequencyOf(stack).isPresent()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            UUID id = UUID.randomUUID();
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(FREQUENCY_TAG, id));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
        tooltip.add(frequencyOf(stack)
                .map(id -> Component.translatable("routedpipes.security.id", id.toString()))
                .orElseGet(() -> Component.translatable("routedpipes.frequency.blank")).withStyle(ChatFormatting.DARK_GRAY));
    }
}
