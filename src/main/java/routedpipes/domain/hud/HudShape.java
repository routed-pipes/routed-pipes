package routedpipes.domain.hud;

import java.util.Optional;

import routedpipes.domain.item.ItemBatch;

// what is drawn on a panel and where, the middle of the panel is 0 0 and y grows downwards
public sealed interface HudShape {

    // centred text has x in its middle, width is the room it has
    record Text(int x, int y, int width, HudText text, boolean centred) implements HudShape {
    }

    // 16 wide and high
    record Stack(int x, int y, ItemBatch batch) implements HudShape {

        // the count the way it fits under a stack, nothing for a single item
        public String amount() {
            int count = batch.count();
            if (count <= 1) {
                return "";
            }
            if (count < 1000) {
                return String.valueOf(count);
            }
            return count < 1_000_000 ? count / 1000 + "k" : count / 1_000_000 + "M";
        }
    }

    // filled goes from 0 to 1
    record Bar(int x, int y, int width, int height, double filled) implements HudShape {
    }

    // pressed by looking at it, chosen ones are drawn pressed in
    record Button(int x, int y, int width, int height, String label, Optional<ItemBatch> icon, HudAction action,
            boolean chosen) implements HudShape {

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
