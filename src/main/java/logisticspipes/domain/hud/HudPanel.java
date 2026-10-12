package logisticspipes.domain.hud;

import java.util.List;

// what the glasses show at one pipe or block, sizes are in hundredths of a block
public record HudPanel(HudCategory category, int width, int height, List<HudElement> elements) {

    public HudPanel {
        elements = List.copyOf(elements);
    }
}
