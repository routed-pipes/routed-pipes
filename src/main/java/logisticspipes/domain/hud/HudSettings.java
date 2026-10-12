package logisticspipes.domain.hud;

import java.util.EnumSet;
import java.util.Set;

// which kinds of panels a pair of glasses shows, all of them at first
public record HudSettings(Set<HudCategory> shown) {

    public static final HudSettings ALL = new HudSettings(EnumSet.allOf(HudCategory.class));

    public HudSettings {
        shown = shown.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(shown));
    }

    public boolean shows(HudCategory category) {
        return shown.contains(category);
    }

    public HudSettings toggled(HudCategory category) {
        EnumSet<HudCategory> next = EnumSet.noneOf(HudCategory.class);
        next.addAll(shown);
        if (!next.remove(category)) {
            next.add(category);
        }
        return new HudSettings(next);
    }

    // one bit per category, for the menu to sync
    public int bits() {
        int bits = 0;
        for (HudCategory category : shown) {
            bits |= 1 << category.ordinal();
        }
        return bits;
    }

    public static HudSettings ofBits(int bits) {
        EnumSet<HudCategory> shown = EnumSet.noneOf(HudCategory.class);
        for (HudCategory category : HudCategory.values()) {
            if ((bits & 1 << category.ordinal()) != 0) {
                shown.add(category);
            }
        }
        return new HudSettings(shown);
    }
}
