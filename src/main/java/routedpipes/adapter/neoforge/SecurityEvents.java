package routedpipes.adapter.neoforge;

import routedpipes.adapter.minecraft.PipeProtection;
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
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                    && PipeProtection.stopsHit(event.getLevel(), event.getPos(), event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level && PipeProtection.stopsBreak(level, event.getPos(),
                    event.getPlayer(), event.getPlayer() instanceof FakePlayer)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate event) ->
                PipeProtection.spareFromExplosion(event.getLevel(), event.getAffectedBlocks()));
    }
}
