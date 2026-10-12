package logisticspipes.adapter.minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import logisticspipes.domain.hud.HudPanel;
import net.minecraft.core.BlockPos;

// the panels the server last told this client about, the drawing reads them from here
public final class ShownHudPanels {

    private static volatile Map<BlockPos, HudPanel> panels = Map.of();

    private ShownHudPanels() {
    }

    public static Map<BlockPos, HudPanel> all() {
        return panels;
    }

    public static void apply(boolean reset, Map<BlockPos, HudPanel> changed, List<BlockPos> gone) {
        Map<BlockPos, HudPanel> next = reset ? new LinkedHashMap<>() : new LinkedHashMap<>(panels);
        gone.forEach(next::remove);
        next.putAll(changed);
        panels = Map.copyOf(next);
    }
}
