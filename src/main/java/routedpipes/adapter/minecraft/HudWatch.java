package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import routedpipes.domain.hud.HudPanel;
import routedpipes.domain.hud.HudPanels;
import routedpipes.domain.hud.HudRange;
import routedpipes.domain.hud.HudSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

// tells the players who wear the glasses what the pipes around them have to show
// a player is only sent what changed since the last look
public final class HudWatch {

    // how the panels get to the client, the loader plugs in its way of sending
    public interface Sender {
        // with reset the client forgets every panel it had before
        void send(Player player, boolean reset, Map<BlockPos, HudPanel> changed, List<BlockPos> gone);
    }

    // ticks between two looks around a player
    public static final int INTERVAL = 10;

    private final Sender sender;
    // what every watching player was last sent, a player who logs in again or respawns is a new one
    private final Map<Player, Map<BlockPos, HudPanel>> sent = new WeakHashMap<>();

    public HudWatch(Sender sender) {
        this.sender = sender;
    }

    public void tick(Player player) {
        if (player.tickCount % INTERVAL == 0) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        if (!HudGlassesItem.worn(player)) {
            if (sent.remove(player) != null) {
                sender.send(player, true, Map.of(), List.of());
            }
            return;
        }
        Map<BlockPos, HudPanel> now = around(player);
        Map<BlockPos, HudPanel> before = sent.put(player, now);
        Map<BlockPos, HudPanel> changed = new LinkedHashMap<>();
        now.forEach((pos, panel) -> {
            if (before == null || !panel.equals(before.get(pos))) {
                changed.put(pos, panel);
            }
        });
        List<BlockPos> gone = before == null ? List.of() : before.keySet().stream().filter(pos -> !now.containsKey(pos)).toList();
        if (before == null || !changed.isEmpty() || !gone.isEmpty()) {
            sender.send(player, before == null, changed, gone);
        }
    }

    // the panels a player with glasses sees from where they stand, nearest first
    // only the chunks in reach are looked at and only the nearest pipes are asked what they have to show
    public static Map<BlockPos, HudPanel> around(Player player) {
        if (!HudGlassesItem.worn(player)) {
            return Map.of();
        }
        HudSettings settings = HudGlassesItem.settingsOf(player.getItemBySlot(EquipmentSlot.HEAD));
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        List<BlockEntity> candidates = new ArrayList<>();
        int firstX = SectionPos.blockToSectionCoord(Mth.floor(eye.x - HudRange.FARTHEST));
        int lastX = SectionPos.blockToSectionCoord(Mth.floor(eye.x + HudRange.FARTHEST));
        int firstZ = SectionPos.blockToSectionCoord(Mth.floor(eye.z - HudRange.FARTHEST));
        int lastZ = SectionPos.blockToSectionCoord(Mth.floor(eye.z + HudRange.FARTHEST));
        for (int x = firstX; x <= lastX; x++) {
            for (int z = firstZ; z <= lastZ; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!entity.isRemoved() && HudSources.categoryOf(entity).filter(settings::shows).isPresent()) {
                        candidates.add(entity);
                    }
                }
            }
        }
        Map<BlockPos, HudPanel> panels = new LinkedHashMap<>();
        for (BlockEntity entity : HudRange.nearest(candidates, entity -> Math.sqrt(entity.getBlockPos().distToCenterSqr(eye)))) {
            HudSources.of(entity).flatMap(HudPanels::of).ifPresent(panel -> panels.put(entity.getBlockPos().immutable(), panel));
        }
        return panels;
    }
}
