package routedpipes.adapter.minecraft;

import java.util.Map;
import java.util.WeakHashMap;

import routedpipes.application.LogisticsNetwork;
import net.minecraft.world.level.Level;

// one network per loaded world, rebuilt from the pipes as their chunks load
public final class LevelNetworks {

    private final Map<Level, LogisticsNetwork> networks = new WeakHashMap<>();

    public LogisticsNetwork of(Level level) {
        return networks.computeIfAbsent(level, key -> new LogisticsNetwork());
    }
}
