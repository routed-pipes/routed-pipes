package logisticspipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.adapter.minecraft.menu.StatisticsTableMenu;
import logisticspipes.domain.statistics.GraphView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class StatisticsTableScreen extends AbstractContainerScreen<StatisticsTableMenu> {

    private static final int TEXT = 0x404040;
    private static final int WARNING = 0xAA0000;
    private static final int SELECTION = 0xFF55CC55;
    private static final int PLOT_LEFT = 34;
    private static final int PLOT_TOP = 108;
    private static final int PLOT_WIDTH = 134;
    private static final int PLOT_HEIGHT = 80;
    private static final int AXIS_STRIP = 12;
    private static final int PLOT_FACE = 0xFFE4E4E4;
    private static final int PLOT_GRID = 0xFFC8C8C8;
    private static final int PLOT_FRAME = 0xFF555555;
    private static final int LINE = 0xFFD02020;
    private static final int DOT = 0xFF000000;
    // two pixels of dragging an axis scale by this much, a notch of the wheel by twice that
    private static final double SCALE_STEP = 1.25;

    // what a drag that started on the graph does
    private enum Drag {
        NONE, PAN, SCALE_TIME, SCALE_AMOUNT
    }

    private final List<Tabs.Tab> tabs = List.of(
            Tabs.Tab.texture("stats", Component.translatable("routedpipes.statistics.track_amount")),
            Tabs.Tab.item(Items.CRAFTING_TABLE, Component.translatable("routedpipes.statistics.crafting_tasks")));
    private final List<Button> amountButtons = new ArrayList<>();
    private final List<Button> listButtons = new ArrayList<>();
    private final List<Button> addButtons = new ArrayList<>();
    private Button remove;
    private Button acquire;
    private EditBox search;
    private GraphView view = new GraphView(PLOT_WIDTH, PLOT_HEIGHT);
    // the stack the samples were last read from, and its item without them
    private ItemStack shownStack = ItemStack.EMPTY;
    private ItemStack shownItem = ItemStack.EMPTY;
    private long[] samples = new long[0];
    private Drag drag = Drag.NONE;

    public StatisticsTableScreen(StatisticsTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        amountButtons.clear();
        listButtons.clear();
        addButtons.clear();
        amountButtons.add(button("<", StatisticsTableMenu.BUTTON_PREVIOUS_ITEMS, 8, 64, 16, 16));
        amountButtons.add(button(Component.translatable("routedpipes.statistics.add"), StatisticsTableMenu.BUTTON_ADD, 28, 64, 40, 16));
        remove = button(Component.translatable("routedpipes.statistics.remove"), StatisticsTableMenu.BUTTON_REMOVE, 72, 64, 56, 16);
        amountButtons.add(remove);
        amountButtons.add(button(">", StatisticsTableMenu.BUTTON_NEXT_ITEMS, 152, 64, 16, 16));
        listButtons.add(button("<", StatisticsTableMenu.BUTTON_PREVIOUS_PAGE, 110, 28, 12, 12));
        listButtons.add(button(">", StatisticsTableMenu.BUTTON_NEXT_PAGE, 156, 28, 12, 12));
        acquire = button(Component.translatable("routedpipes.statistics.acquire_tasks"), StatisticsTableMenu.BUTTON_REFRESH, 8, 43, 160, 16);
        addButtons.add(button(Component.translatable("routedpipes.statistics.refresh"), StatisticsTableMenu.BUTTON_REFRESH, 8, 196, 76, 16));
        addButtons.add(button(Component.translatable("routedpipes.statistics.add"), StatisticsTableMenu.BUTTON_CONFIRM, 92, 196, 76, 16));
        String typed = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + 8, topPos + 44, 160, 14, Component.translatable("routedpipes.gui.search"));
        search.setHint(Component.translatable("routedpipes.gui.search"));
        search.setMaxLength(32);
        search.setValue(typed);
        search.setResponder(text -> ClientMenuLink.setText(menu.containerId, StatisticsTableMenu.TEXT_SEARCH, text));
        addRenderableWidget(search);
        update();
    }

    private Button button(String label, int id, int x, int y, int width, int height) {
        return button(Component.literal(label), id, x, y, width, height);
    }

    private Button button(Component label, int id, int x, int y, int width, int height) {
        return addRenderableWidget(Button.builder(label, pressed -> press(id)).bounds(leftPos + x, topPos + y, width, height).build());
    }

    // shows the widgets of the open view and reads the history again when the server sent a new one
    private void update() {
        StatisticsTableMenu.View shown = menu.view();
        amountButtons.forEach(button -> button.visible = shown == StatisticsTableMenu.View.AMOUNTS);
        listButtons.forEach(button -> button.visible = shown != StatisticsTableMenu.View.AMOUNTS);
        addButtons.forEach(button -> button.visible = shown == StatisticsTableMenu.View.ADD);
        acquire.visible = shown == StatisticsTableMenu.View.TASKS;
        search.visible = shown == StatisticsTableMenu.View.ADD;
        if (!search.visible) {
            search.setFocused(false);
        }
        ItemStack stack = menu.getSlot(StatisticsTableMenu.SELECTED_SLOT).getItem();
        remove.active = !stack.isEmpty();
        if (stack == shownStack) {
            return;
        }
        shownStack = stack;
        samples = menu.samples();
        ItemStack item = stack.copy();
        if (!item.isEmpty()) {
            item.remove(DataComponents.CUSTOM_DATA);
        }
        if (!ItemStack.isSameItemSameComponents(item, shownItem)) {
            // another item was selected, its graph starts at now and fits what there is to see
            shownItem = item;
            view = new GraphView(PLOT_WIDTH, PLOT_HEIGHT);
            view.fit(samples);
        }
    }

    @Override
    protected void containerTick() {
        update();
    }

    // the menu is told on this side as well, the slots of the view are there at once that way
    private void press(int button) {
        menu.clickMenuButton(minecraft.player, button);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        update();
    }

    private boolean overPlot(double mouseX, double mouseY) {
        return menu.view() == StatisticsTableMenu.View.AMOUNTS && view.shows(mouseX - leftPos - PLOT_LEFT, mouseY - topPos - PLOT_TOP);
    }

    // dragging the graph moves it, dragging the strip under it or left of it stretches that axis
    private Drag dragAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - PLOT_LEFT;
        double y = mouseY - topPos - PLOT_TOP;
        if (menu.view() != StatisticsTableMenu.View.AMOUNTS) {
            return Drag.NONE;
        }
        if (view.shows(x, y)) {
            return Drag.PAN;
        }
        if (x >= 0 && x <= PLOT_WIDTH && y > PLOT_HEIGHT && y <= PLOT_HEIGHT + AXIS_STRIP) {
            return Drag.SCALE_TIME;
        }
        if (y >= 0 && y <= PLOT_HEIGHT && x < 0 && x >= 8 - PLOT_LEFT) {
            return Drag.SCALE_AMOUNT;
        }
        return Drag.NONE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = Tabs.at(leftPos, topPos, tabs.size(), mouseX, mouseY);
        if (tab >= 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            press(tab);
            return true;
        }
        drag = dragAt(mouseX, mouseY);
        return drag != Drag.NONE || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        drag = Drag.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        switch (drag) {
            case PAN -> view.pan(dragX, -dragY);
            case SCALE_TIME -> view.scaleTime(Math.pow(SCALE_STEP, dragX / 2));
            case SCALE_AMOUNT -> view.scaleAmount(Math.pow(SCALE_STEP, -dragY / 2));
            default -> {
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!overPlot(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        view.scaleTime(Math.pow(SCALE_STEP, 2 * scrollY));
        view.scaleAmount(Math.pow(SCALE_STEP, 2 * scrollY));
        return true;
    }

    // typing in the search must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.view() != StatisticsTableMenu.View.AMOUNTS) {
            for (int slot = 0; slot < StatisticsTableMenu.LIST_SLOTS; slot++) {
                Slot listed = menu.getSlot(StatisticsTableMenu.FIRST_LIST_SLOT + slot);
                if (listed.hasItem()) {
                    drawCount(graphics, listed);
                }
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
        int hovered = Tabs.at(leftPos, topPos, tabs.size(), mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderTooltip(font, tabs.get(hovered).name(), mouseX, mouseY);
        }
    }

    // small text in the slot corner, the real stack count is always 1
    private void drawCount(GuiGraphics graphics, Slot slot) {
        String text = compact(RequestMenu.listedCount(slot.getItem()));
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + slot.x + 16 - font.width(text) * 0.5f, topPos + slot.y + 11, 300);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private static String compact(long amount) {
        if (Math.abs(amount) < 1000) {
            return Long.toString(amount);
        }
        if (Math.abs(amount) < 1_000_000) {
            return amount / 1000 + "k";
        }
        return amount / 1_000_000 + "M";
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        Tabs.draw(graphics, leftPos, topPos, tabs, menu.view() == StatisticsTableMenu.View.TASKS ? 1 : 0);
        if (menu.view() == StatisticsTableMenu.View.AMOUNTS) {
            highlight(graphics, menu.selectedSlot());
            graph(graphics);
        } else if (menu.pickedSlot() >= 0) {
            highlight(graphics, StatisticsTableMenu.FIRST_LIST_SLOT + menu.pickedSlot());
        }
    }

    private void highlight(GuiGraphics graphics, int index) {
        if (index >= 0) {
            Slot slot = menu.getSlot(index);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, SELECTION);
        }
    }

    private void graph(GuiGraphics graphics) {
        int left = leftPos + PLOT_LEFT;
        int bottom = topPos + PLOT_TOP + PLOT_HEIGHT;
        graphics.fill(left - 1, bottom - PLOT_HEIGHT - 1, left + PLOT_WIDTH + 1, bottom + 1, PLOT_FRAME);
        graphics.fill(left, bottom - PLOT_HEIGHT, left + PLOT_WIDTH, bottom, PLOT_FACE);
        if (samples.length == 0) {
            return;
        }
        long amountStep = view.amountStep(16);
        for (long amount = Math.floorDiv((long) Math.ceil(view.bottom()), amountStep) * amountStep; amount <= view.top();
                amount += amountStep) {
            int y = bottom - (int) Math.round(view.y(amount));
            if (amount >= view.bottom()) {
                graphics.fill(left, y, left + PLOT_WIDTH, y + 1, PLOT_GRID);
                String label = compact(amount);
                graphics.drawString(font, label, left - 3 - font.width(label), y - 4, TEXT, false);
            }
        }
        int ageStep = view.ageStep(45);
        for (int age = (int) Math.ceil(view.rightAge() / ageStep) * ageStep; age <= view.leftAge(); age += ageStep) {
            int x = left + (int) Math.round(view.x(age));
            graphics.fill(x, bottom, x + 1, bottom + 3, PLOT_FRAME);
            String label = GraphView.ago(age);
            // the label of the newest sample would stick out of the window
            graphics.drawString(font, label, Math.min(x - font.width(label) / 2, left + PLOT_WIDTH - font.width(label)), bottom + 4,
                    TEXT, false);
        }
        graphics.enableScissor(left, bottom - PLOT_HEIGHT, left + PLOT_WIDTH, bottom);
        int first = Math.max(0, (int) Math.floor(view.rightAge()) - 1);
        int last = Math.min(samples.length - 1, (int) Math.ceil(view.leftAge()) + 1);
        int dot = view.x(0) - view.x(1) < 4 ? 0 : 1;
        for (int age = first; age <= last; age++) {
            int x = left + (int) Math.round(view.x(age));
            int y = bottom - sampleHeight(age);
            if (age < last) {
                line(graphics, x, y, left + (int) Math.round(view.x(age + 1)), bottom - sampleHeight(age + 1));
            }
            graphics.fill(x - dot, y - dot, x + dot + 1, y + dot + 1, DOT);
        }
        graphics.disableScissor();
    }

    // how far above the bottom of the graph the sample of that age is, kept near the graph so that lines stay short
    private int sampleHeight(int age) {
        double y = view.y(samples[samples.length - 1 - age]);
        return (int) Math.round(Math.max(-PLOT_HEIGHT, Math.min(2 * PLOT_HEIGHT, y)));
    }

    private void line(GuiGraphics graphics, int fromX, int fromY, int toX, int toY) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        for (int step = 0; step <= steps; step++) {
            int x = steps == 0 ? fromX : fromX + (toX - fromX) * step / steps;
            int y = steps == 0 ? fromY : fromY + (toY - fromY) * step / steps;
            graphics.fill(x, y, x + 1, y + 1, LINE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        switch (menu.view()) {
            case AMOUNTS -> amounts(graphics);
            case TASKS -> list(graphics, "routedpipes.statistics.crafting_tasks");
            case ADD -> list(graphics, "routedpipes.statistics.pick_item");
        }
    }

    private void amounts(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("routedpipes.statistics.track_amount"), 8, 30, TEXT, false);
        String page = (menu.itemsPage() + 1) + "/" + menu.itemsPages();
        graphics.drawString(font, page, 140 - font.width(page) / 2, 68, TEXT, false);
        if (!shownStack.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(shownStack.getHoverName().getString(), 136), 30, 90, TEXT, false);
        }
        if (!menu.hasPipe()) {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("routedpipes.statistics.no_pipe").getString(), 160),
                    8, 208, WARNING, false);
        } else if (!shownStack.isEmpty() && samples.length == 0) {
            graphics.drawString(font, Component.translatable("routedpipes.statistics.no_samples"), PLOT_LEFT + 4, PLOT_TOP + 4, TEXT, false);
        }
    }

    private void list(GuiGraphics graphics, String label) {
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(label).getString(), 98), 8, 30, TEXT, false);
        String page = (menu.listPage() + 1) + "/" + menu.listPages();
        graphics.drawString(font, page, 139 - font.width(page) / 2, 30, TEXT, false);
    }
}
