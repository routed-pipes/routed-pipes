package routedpipes.adapter.minecraft;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import routedpipes.adapter.minecraft.menu.SignItemMenu;
import routedpipes.domain.security.Permission;
import routedpipes.domain.sign.SignKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

// puts signs on the faces of routed pipes: use on a face to place one, sneak and use to take it off again
// a use on an item amount sign opens it to pick its item
// sneak and use in the air picks the kind of sign, the pipe block lets the plain use through
public class SignCreatorItem extends Item {

    public static final int SIGNS = 250;

    // the kind of sign the creator makes as its number, the loader has to register this as a data component
    public static final DataComponentType<Integer> MODE = DataComponentType.<Integer>builder()
            .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build();

    private final Supplier<MenuType<SignItemMenu>> signItemMenu;

    public SignCreatorItem(Properties properties, Supplier<MenuType<SignItemMenu>> signItemMenu) {
        super(properties.durability(SIGNS));
        this.signItemMenu = signItemMenu;
    }

    public static SignKind modeOf(ItemStack creator) {
        SignKind[] kinds = SignKind.values();
        return kinds[Math.floorMod(creator.getOrDefault(MODE, 0), kinds.length)];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack creator = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(creator);
        }
        if (!level.isClientSide) {
            creator.set(MODE, modeOf(creator).next().ordinal());
            player.displayClientMessage(creator.getHoverName(), true);
        }
        return InteractionResultHolder.sidedSuccess(creator, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PipeBlockEntity pipe)) {
            return InteractionResult.PASS;
        }
        if (pipe.signs() == null) {
            // a click on a plain pipe does not pick another kind of sign either
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        if (!level.isClientSide && (player == null || pipe.security().allows(player, Permission.CONFIGURE_SETTINGS))) {
            click(context.getItemInHand(), pipe, context.getClickedFace(), player, context.getHand(), context.isSecondaryUseActive());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void click(ItemStack creator, PipeBlockEntity pipe, Direction face, Player player, InteractionHand hand,
            boolean sneaking) {
        PipeSigns signs = pipe.signs();
        if (sneaking) {
            // the sign goes back into the creator
            if (signs.remove(face)) {
                creator.setDamageValue(Math.max(0, creator.getDamageValue() - 1));
            }
            return;
        }
        if (signs.at(face).isEmpty()) {
            if (!signs.place(face, modeOf(creator))) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("routedpipes.sign.not_allowed"), true);
                }
                return;
            }
            if (player != null) {
                creator.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }
        // a new item amount sign asks for its item at once
        if (player != null && signs.at(face).get() == SignKind.ITEM_AMOUNT) {
            signs.open(face, player, signItemMenu.get());
        }
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId() + "." + modeOf(stack).name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
    }
}
