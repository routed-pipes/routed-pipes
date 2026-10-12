package logisticspipes.adapter.minecraft;

import java.util.List;
import java.util.Optional;

import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.security.Permission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

// opens the request screen of a linked request pipe from anywhere in the same dimension
public class RemoteOrdererItem extends Item {

    private static final String LINK_TAG = "LogisticsPipesLink";

    public RemoteOrdererItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    private record Link(BlockPos pos, String dimension) {
    }

    private static Optional<Link> linkOf(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(LINK_TAG)) {
            return Optional.empty();
        }
        CompoundTag link = tag.getCompound(LINK_TAG);
        return Optional.of(new Link(new BlockPos(link.getInt("X"), link.getInt("Y"), link.getInt("Z")), link.getString("Dimension")));
    }

    public static boolean isLinked(ItemStack stack) {
        return linkOf(stack).isPresent();
    }

    // sneak and use on a request pipe to link, a plain click would just open the pipe
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) || !pipe.kind().requestPipe()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && (context.getPlayer() == null
                || pipe.security().allows(context.getPlayer(), Permission.ACTIVE_REQUESTING))) {
            CustomData.update(DataComponents.CUSTOM_DATA, context.getItemInHand(), tag -> {
                CompoundTag link = new CompoundTag();
                link.putInt("X", pos.getX());
                link.putInt("Y", pos.getY());
                link.putInt("Z", pos.getZ());
                link.putString("Dimension", level.dimension().location().toString());
                tag.put(LINK_TAG, link);
            });
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("logisticspipes.remote_orderer.linked"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        open(stack, player);
        return InteractionResultHolder.consume(stack);
    }

    // server side: opens the linked pipe or tells the player why not
    private static void open(ItemStack stack, Player player) {
        Level level = player.level();
        Optional<Link> link = linkOf(stack);
        if (link.isEmpty()) {
            player.displayClientMessage(Component.translatable("logisticspipes.remote_orderer.not_linked"), true);
        } else if (!link.get().dimension().equals(level.dimension().location().toString())) {
            player.displayClientMessage(Component.translatable("logisticspipes.remote_orderer.other_dimension"), true);
        } else if (level.isLoaded(link.get().pos()) && level.getBlockEntity(link.get().pos()) instanceof PipeBlockEntity pipe
                && pipe.kind().requestPipe()) {
            if (pipe.security().allows(player, Permission.ACTIVE_REQUESTING)) {
                pipe.openRequestScreen(player, true);
            }
        } else {
            player.displayClientMessage(Component.translatable("logisticspipes.remote_orderer.pipe_missing"), true);
        }
    }

    // what the key does: an orderer anywhere in the inventory is used as if it were in the hand, a linked one first
    // the client only says that the key was pressed, so a player without an orderer gets nothing
    public static void openFromInventory(Player player) {
        ItemStack found = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof RemoteOrdererItem && (found.isEmpty() || !isLinked(found) && isLinked(stack))) {
                found = stack;
            }
        }
        if (!found.isEmpty()) {
            open(found, player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.logisticspipes.remote_orderer.description").withStyle(ChatFormatting.GRAY));
        linkOf(stack).ifPresent(link -> tooltip.add(Component.translatable("logisticspipes.remote_orderer.link",
                link.pos().getX(), link.pos().getY(), link.pos().getZ()).withStyle(ChatFormatting.DARK_GRAY)));
    }
}
