package routedpipes.adapter.minecraft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

// made by a security station, in a routed pipe it locks the pipe to that station
public class SecurityCardItem extends Item {

    private static final String STATION_TAG = "LogisticsPipesStation";

    public SecurityCardItem(Properties properties) {
        super(properties);
    }

    public ItemStack cardFor(UUID station) {
        ItemStack card = new ItemStack(this);
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.putUUID(STATION_TAG, station));
        return card;
    }

    // empty for anything that is not a card with a station on it
    public static Optional<UUID> stationOf(ItemStack stack) {
        if (!(stack.getItem() instanceof SecurityCardItem)) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(STATION_TAG) ? Optional.of(tag.getUUID(STATION_TAG)) : Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
        stationOf(stack).ifPresent(station -> tooltip.add(Component.translatable("routedpipes.security.id", station.toString())
                .withStyle(ChatFormatting.DARK_GRAY)));
    }
}
