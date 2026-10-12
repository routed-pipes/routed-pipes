package logisticspipes.domain.hud;

import java.util.Objects;
import java.util.Optional;

// buttons on a panel are pressed by looking at them: one that is looked at long enough presses once,
// then it waits until the player looks away
public final class HudGaze {

    public static final long PRESS_AFTER_MILLIS = 400;

    // the button looked at, told apart by where its panel is and what it does
    public record Target(Object panel, HudAction action) {
    }

    private Target focused;
    private long since;
    private boolean pressed;

    public Optional<Target> focused() {
        return Optional.ofNullable(focused);
    }

    // returns the button that is pressed right now, if one is
    public Optional<Target> look(Optional<Target> at, long nowMillis) {
        if (!Objects.equals(at.orElse(null), focused)) {
            focused = at.orElse(null);
            since = nowMillis;
            pressed = false;
            return Optional.empty();
        }
        if (focused == null || pressed || nowMillis - since <= PRESS_AFTER_MILLIS) {
            return Optional.empty();
        }
        pressed = true;
        return Optional.of(focused);
    }
}
