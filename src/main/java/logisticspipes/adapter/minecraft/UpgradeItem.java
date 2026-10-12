package logisticspipes.adapter.minecraft;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

// goes into a routed pipe by using it on the pipe, sneak and use the pipe with an empty hand to get it back
public class UpgradeItem extends Item {

    public enum Kind {
        // the pipe reaches into inventories through another face than the one it touches
        SNEAKY,
        // items leaving the pipe travel faster until the next routed pipe
        SPEED
    }

    private static final String SIDE_TAG = "LogisticsPipesSide";

    private final Kind kind;

    public UpgradeItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    // the inventory face a sneaky upgrade uses
    public static Direction sideOf(ItemStack upgrade) {
        String name = upgrade.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(SIDE_TAG);
        Direction side = Direction.byName(name);
        return side == null ? Direction.UP : side;
    }

    public static void setSide(ItemStack upgrade, Direction side) {
        CustomData.update(DataComponents.CUSTOM_DATA, upgrade, tag -> tag.putString(SIDE_TAG, side.getName()));
    }

    // using a sneaky upgrade in the hand turns it to the next face
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack upgrade = player.getItemInHand(hand);
        if (kind != Kind.SNEAKY) {
            return InteractionResultHolder.pass(upgrade);
        }
        if (!level.isClientSide) {
            Direction next = Direction.values()[(sideOf(upgrade).ordinal() + 1) % Direction.values().length];
            setSide(upgrade, next);
            player.displayClientMessage(sideText(next), true);
        }
        return InteractionResultHolder.sidedSuccess(upgrade, level.isClientSide);
    }

    private static Component sideText(Direction side) {
        return Component.translatable("logisticspipes.upgrade.sneaky.side", Component.translatable("logisticspipes.side." + side.getName()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
        if (kind == Kind.SNEAKY) {
            tooltip.add(sideText(sideOf(stack)).copy().withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
