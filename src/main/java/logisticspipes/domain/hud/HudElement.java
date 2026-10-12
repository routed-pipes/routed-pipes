package logisticspipes.domain.hud;

import java.util.List;

import logisticspipes.domain.item.ItemBatch;

// the things a panel is made of, top to bottom
public sealed interface HudElement {

    record Label(HudText text, boolean centred) implements HudElement {
    }

    // a label with one stack next to it
    record Entry(HudText text, ItemBatch stack) implements HudElement {
    }

    // stacks in rows, with a pager once they do not fit on one page
    record Grid(List<ItemBatch> stacks, int columns, int perPage) implements HudElement {

        public Grid {
            stacks = List.copyOf(stacks);
        }
    }

    record Bar(long stored, long capacity) implements HudElement {
    }

    // a column of modules to pick from, beside it the content of the picked one or of the pipe while none is picked
    record Modules(List<Module> modules, List<HudElement> unpicked) implements HudElement {

        public Modules {
            modules = List.copyOf(modules);
            unpicked = List.copyOf(unpicked);
        }
    }

    record Module(ItemBatch icon, List<HudElement> content) {

        public Module {
            content = List.copyOf(content);
        }
    }
}
