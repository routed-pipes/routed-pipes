package logisticspipes.adapter.neoforge;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.security.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

// keeps locked pipes where they are unless their security station lets them go
final class SecurityEvents {

    private SecurityEvents() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(SecurityEvents::startBreaking);
        NeoForge.EVENT_BUS.addListener(SecurityEvents::machineBreaks);
        NeoForge.EVENT_BUS.addListener(SecurityEvents::explosion);
    }

    // a player is stopped at the first hit and told why
    private static void startBreaking(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (level.isClientSide || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)
                || pipe.security().allows(event.getEntity(), Permission.REMOVE_PIPES)) {
            return;
        }
        event.setCanceled(true);
        // a creative player has already seen the pipe vanish
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    // block breakers of other mods work through a pretend player that never clicks, and tools that break several
    // blocks at once break the extra ones for the real player without a click on them
    private static void machineBreaks(BlockEvent.BreakEvent event) {
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof PipeBlockEntity pipe)) {
            return;
        }
        boolean allowed = event.getPlayer() instanceof FakePlayer
                ? pipe.security().allowsAutomatedRemoval()
                : pipe.security().allows(event.getPlayer(), Permission.REMOVE_PIPES);
        if (!allowed) {
            event.setCanceled(true);
        }
    }

    private static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().getBlockEntity(pos) instanceof PipeBlockEntity pipe
                && !pipe.security().allowsAutomatedRemoval());
    }
}
