package logisticspipes.domain.hud;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

// how far away a panel shows and how many show at once
public final class HudRange {

    // closer than this the panel would be in the player's face
    public static final double NEAREST = 0.75;
    public static final double FARTHEST = 15;
    // in a room full of pipes only the nearest get a panel
    public static final int MAX_PANELS = 32;

    private HudRange() {
    }

    public static boolean shows(double distance) {
        return distance > NEAREST && distance < FARTHEST;
    }

    // the ones in range, nearest first
    public static <T> List<T> nearest(Collection<T> all, ToDoubleFunction<T> distance) {
        return all.stream().filter(one -> shows(distance.applyAsDouble(one)))
                .sorted(Comparator.comparingDouble(distance)).limit(MAX_PANELS).toList();
    }
}
