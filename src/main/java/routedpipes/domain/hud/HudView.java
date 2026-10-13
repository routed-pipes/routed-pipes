package routedpipes.domain.hud;

import java.util.HashMap;
import java.util.Map;

// how one panel is looked at: the pages its lists are on and the module that is picked
public final class HudView {

    public static final int NOTHING_PICKED = -1;

    private final Map<Integer, Integer> pages = new HashMap<>();
    private int modulePage;
    private int picked = NOTHING_PICKED;

    public int page(int grid) {
        return pages.getOrDefault(grid, 0);
    }

    public int modulePage() {
        return modulePage;
    }

    public int picked() {
        return picked;
    }

    public void apply(HudAction action) {
        switch (action.kind()) {
            case TURN_PAGE -> pages.put(action.target(), action.value());
            case TURN_MODULES -> modulePage = action.value();
            case PICK_MODULE -> picked = action.value();
            case UNPICK_MODULE -> picked = NOTHING_PICKED;
        }
    }
}
