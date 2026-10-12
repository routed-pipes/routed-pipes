package logisticspipes.domain.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.hud.HudAction.Kind;
import logisticspipes.domain.hud.HudShape.Button;
import logisticspipes.domain.hud.HudShape.Stack;
import logisticspipes.domain.hud.HudShape.Text;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.listing.Page;

// puts the elements of a panel where they are drawn
public final class HudLayout {

    public static final int CELL = 18;
    private static final int MARGIN = 5;
    private static final int LINE = 10;
    private static final int PAGER_WIDTH = 10;
    private static final int PAGER_HEIGHT = 9;

    // the module column of a chassis, the numbers of the original
    private static final int MODULES_PER_PAGE = 3;
    private static final int MODULES_X = -45;
    private static final int MODULES_Y = -35;
    private static final int MODULE_WIDTH = 20;
    private static final int MODULE_HEIGHT = 25;
    private static final int MODULE_STEP = 27;
    private static final int CONTENT_X = -23;
    private static final int CONTENT_WIDTH = 68;
    private static final int TOP_ROW_Y = -45;
    private static final int UNPICK_WIDTH = 8;
    // the lists of every module turn their pages on their own
    private static final int GRIDS_PER_MODULE = 64;

    private HudLayout() {
    }

    public static List<HudShape> place(HudPanel panel, HudView view) {
        List<HudShape> shapes = new ArrayList<>();
        placeColumn(shapes, panel.elements(), -panel.width() / 2 + MARGIN, -panel.height() / 2 + MARGIN,
                panel.width() - 2 * MARGIN, 0, view);
        return shapes;
    }

    private static void placeModules(List<HudShape> shapes, HudElement.Modules modules, HudView view) {
        Page<HudElement.Module> page = Page.of(modules.modules(), view.modulePage(), MODULES_PER_PAGE);
        int picked = view.picked() < modules.modules().size() ? view.picked() : HudView.NOTHING_PICKED;
        if (page.number() > 0) {
            shapes.add(pager("<", MODULES_X, TOP_ROW_Y, new HudAction(Kind.TURN_MODULES, 0, page.number() - 1)));
        }
        if (page.number() < page.pages() - 1) {
            shapes.add(pager(">", MODULES_X + PAGER_WIDTH + 2, TOP_ROW_Y, new HudAction(Kind.TURN_MODULES, 0, page.number() + 1)));
        }
        for (int row = 0; row < page.entries().size(); row++) {
            int module = page.number() * MODULES_PER_PAGE + row;
            shapes.add(new Button(MODULES_X, MODULES_Y + MODULE_STEP * row, MODULE_WIDTH, MODULE_HEIGHT, "",
                    Optional.of(page.entries().get(row).icon()), new HudAction(Kind.PICK_MODULE, 0, module), module == picked));
        }
        if (picked == HudView.NOTHING_PICKED) {
            placeColumn(shapes, modules.unpicked(), CONTENT_X, MODULES_Y, CONTENT_WIDTH, 0, view);
            return;
        }
        shapes.add(new Button(CONTENT_X + CONTENT_WIDTH - UNPICK_WIDTH, TOP_ROW_Y, UNPICK_WIDTH, PAGER_HEIGHT, "x", Optional.empty(),
                new HudAction(Kind.UNPICK_MODULE, 0, 0), false));
        placeColumn(shapes, modules.modules().get(picked).content(), CONTENT_X, MODULES_Y, CONTENT_WIDTH,
                (picked + 1) * GRIDS_PER_MODULE, view);
    }

    private static Button pager(String label, int x, int y, HudAction action) {
        return new Button(x, y, PAGER_WIDTH, PAGER_HEIGHT, label, Optional.empty(), action, false);
    }

    private static void placeColumn(List<HudShape> shapes, List<HudElement> elements, int left, int top, int width,
            int firstGrid, HudView view) {
        int y = top;
        for (int index = 0; index < elements.size(); index++) {
            switch (elements.get(index)) {
                case HudElement.Label label -> {
                    shapes.add(new Text(label.centred() ? left + width / 2 : left, y, width, label.text(), label.centred()));
                    y += LINE;
                }
                case HudElement.Entry entry -> {
                    shapes.add(new Text(left, y + (CELL - LINE) / 2 + 1, width - CELL, entry.text(), false));
                    shapes.add(new Stack(left + width - CELL + 1, y + 1, entry.stack()));
                    y += CELL;
                }
                case HudElement.Grid grid -> y = placeGrid(shapes, grid, left, y, width, firstGrid + index, view);
                case HudElement.Bar bar -> {
                    double filled = bar.capacity() <= 0 ? 0 : Math.max(0, Math.min(1, (double) bar.stored() / bar.capacity()));
                    shapes.add(new HudShape.Bar(left, y, width, LINE, filled));
                    y += LINE + 3;
                }
                case HudElement.Modules modules -> placeModules(shapes, modules, view);
            }
        }
    }

    // returns where the next element goes
    private static int placeGrid(List<HudShape> shapes, HudElement.Grid grid, int left, int top, int width, int id, HudView view) {
        Page<ItemBatch> page = Page.of(grid.stacks(), view.page(id), grid.perPage());
        int y = top;
        if (page.pages() > 1) {
            if (page.number() > 0) {
                shapes.add(pager("<", left, y, new HudAction(Kind.TURN_PAGE, id, page.number() - 1)));
            }
            if (page.number() < page.pages() - 1) {
                shapes.add(pager(">", left + width - PAGER_WIDTH, y, new HudAction(Kind.TURN_PAGE, id, page.number() + 1)));
            }
            shapes.add(new Text(left + width / 2, y + 1, width - 2 * PAGER_WIDTH,
                    HudText.of("logisticspipes.hud.page", page.number() + 1, page.pages()), true));
            y += PAGER_HEIGHT + 2;
        }
        int gridLeft = left + (width - grid.columns() * CELL) / 2;
        for (int index = 0; index < page.entries().size(); index++) {
            shapes.add(new Stack(gridLeft + index % grid.columns() * CELL + 1, y + index / grid.columns() * CELL + 1,
                    page.entries().get(index)));
        }
        int rows = (page.entries().size() + grid.columns() - 1) / grid.columns();
        return y + rows * CELL + 2;
    }
}
