package routedpipes.adapter.minecraft.gametest;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

// an item used on a block, whether or not the player holds it
// vanilla only lets subclasses say which stack is used
final class TestUse {

    private TestUse() {
    }

    static UseOnContext of(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        return new UseOnContext(level, player, hand, stack, hit) {
        };
    }
}
