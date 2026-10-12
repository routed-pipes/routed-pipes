package logisticspipes.adapter.minecraft;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

// where the pipe in a player's hand would go and which sides it would connect on, before it is placed
public final class PlacementPreview {

    // the state is the one the pipe gets when it is placed
    public record Preview(BlockPos pos, BlockState state) {

        public Set<Direction> sides() {
            Set<Direction> sides = EnumSet.noneOf(Direction.class);
            for (Direction direction : Direction.values()) {
                if (LogisticsPipeBlock.connected(state, direction)) {
                    sides.add(direction);
                }
            }
            return sides;
        }
    }

    private PlacementPreview() {
    }

    // empty without a pipe in either hand and where one cannot be placed
    public static Optional<Preview> of(Player player, BlockHitResult hit) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof LogisticsPipeBlock pipe) {
                return of(pipe, new BlockPlaceContext(player, hand, stack, hit));
            }
        }
        return Optional.empty();
    }

    private static Optional<Preview> of(LogisticsPipeBlock pipe, BlockPlaceContext context) {
        if (!context.canPlace()) {
            return Optional.empty();
        }
        BlockState state = pipe.getStateForPlacement(context);
        BlockPos pos = context.getClickedPos();
        if (state == null || !context.getLevel().isUnobstructed(state, pos, CollisionContext.of(context.getPlayer()))) {
            return Optional.empty();
        }
        return Optional.of(new Preview(pos, state));
    }
}
