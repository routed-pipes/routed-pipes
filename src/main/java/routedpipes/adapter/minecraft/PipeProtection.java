package routedpipes.adapter.minecraft;

import java.util.List;

import routedpipes.domain.security.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// keeps locked pipes where they are unless their security station lets them go, the loader asks this from its events
public final class PipeProtection {

    private PipeProtection() {
    }

    // a player is stopped at the first hit, true if they are
    public static boolean stopsHit(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)
                || pipe.security().allows(player, Permission.REMOVE_PIPES)) {
            return false;
        }
        // a creative player has already seen the pipe vanish
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return true;
    }

    // block breakers of other mods work through a pretend player that never clicks, and tools that break several
    // blocks at once break the extra ones for the real player without a click on them
    public static boolean stopsBreak(Level level, BlockPos pos, Player player, boolean machine) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) {
            return false;
        }
        return machine ? !pipe.security().allowsAutomatedRemoval() : !pipe.security().allows(player, Permission.REMOVE_PIPES);
    }

    // takes the pipes off what an explosion destroys
    public static void spareFromExplosion(Level level, List<BlockPos> destroyed) {
        if (level.isClientSide) {
            return;
        }
        destroyed.removeIf(pos -> level.getBlockEntity(pos) instanceof PipeBlockEntity pipe && !pipe.security().allowsAutomatedRemoval());
    }
}
