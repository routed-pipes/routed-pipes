package routedpipes.domain.hud;

// what a button on a panel does to the way the panel is looked at
public record HudAction(Kind kind, int target, int value) {

    public enum Kind {
        // target is the grid, value the page to show
        TURN_PAGE,
        // value is the page of the module column
        TURN_MODULES,
        // value is the module
        PICK_MODULE,
        UNPICK_MODULE
    }
}
