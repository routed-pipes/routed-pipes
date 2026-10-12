package logisticspipes.adapter.minecraft.client;

import java.util.List;

import logisticspipes.adapter.minecraft.menu.DiskMenu;
import logisticspipes.domain.macro.Macro;
import logisticspipes.domain.macro.MacroDisk;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

// the name of the disk and the list of its macros, click one to pick it for the buttons below
public class DiskScreen extends AbstractContainerScreen<DiskMenu> {

    private static final int TEXT = 0x404040;
    private static final int LIST = 0xFF8B8B8B;
    private static final int LIST_TEXT = 0xFFFFFF;
    private static final int SELECTION = 0xFF55CC55;
    private static final int ROWS = 8;
    private static final int ROW = 11;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 36;
    private static final int LIST_WIDTH = 160;

    private EditBox name;
    private Button request;
    private Button edit;
    private Button delete;
    private boolean nameLoaded;
    private List<String> macros = List.of();
    // the name of the picked macro, null if none is
    private String selected;
    private int scroll;

    public DiskScreen(DiskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 170;
    }

    @Override
    protected void init() {
        super.init();
        String typed = name == null ? "" : name.getValue();
        name = new EditBox(font, leftPos + 8, topPos + 18, 138, 12, Component.translatable("logisticspipes.gui.disk.name"));
        name.setHint(Component.translatable("logisticspipes.gui.disk.name"));
        name.setMaxLength(DiskMenu.MAX_NAME);
        name.setValue(typed);
        name.setResponder(text -> ClientMenuLink.setText(menu.containerId, DiskMenu.TEXT_DISK_NAME, text));
        addRenderableWidget(name);
        request = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.disk.request"),
                button -> ClientMenuLink.setText(menu.containerId, DiskMenu.TEXT_REQUEST, selected))
                .bounds(leftPos + 8, topPos + 130, 78, 16).build());
        edit = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.disk.add"),
                button -> ClientMenuLink.setText(menu.containerId, DiskMenu.TEXT_EDIT, selected == null ? "" : selected))
                .bounds(leftPos + 90, topPos + 130, 78, 16).build());
        delete = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.disk.delete"),
                button -> ClientMenuLink.setText(menu.containerId, DiskMenu.TEXT_DELETE, selected))
                .bounds(leftPos + 8, topPos + 148, 78, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.disk.back"),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DiskMenu.BUTTON_BACK))
                .bounds(leftPos + 90, topPos + 148, 78, 16).build());
        updateButtons();
    }

    private void updateButtons() {
        request.active = selected != null;
        delete.active = selected != null;
        edit.setMessage(Component.translatable(selected == null ? "logisticspipes.gui.disk.add" : "logisticspipes.gui.disk.edit"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        MacroDisk disk = menu.disk();
        if (!nameLoaded && menu.getSlot(0).hasItem()) {
            nameLoaded = true;
            name.setValue(disk.name());
        }
        macros = disk.macros().stream().map(Macro::name).toList();
        if (!macros.contains(selected)) {
            selected = null;
        }
        scroll = Math.max(0, Math.min(scroll, macros.size() - ROWS));
        updateButtons();
    }

    // the place in the list of the macro under the mouse, -1 if there is none
    private int macroAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        if (x < 0 || y < 0 || x >= LIST_WIDTH || y >= ROWS * ROW) {
            return -1;
        }
        int index = scroll + (int) (y / ROW);
        return index < macros.size() ? index : -1;
    }

    // a click on the picked macro lets go of it, with none picked the editor starts a new one
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int macro = macroAt(mouseX, mouseY);
        if (macro >= 0) {
            selected = macros.get(macro).equals(selected) ? null : macros.get(macro);
            updateButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(macros.size() - ROWS, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    // typing the name must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            name.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;
        graphics.fill(x, y, x + LIST_WIDTH, y + ROWS * ROW, LIST);
        int row = macros.indexOf(selected) - scroll;
        if (selected != null && row >= 0 && row < ROWS) {
            graphics.fill(x, y + row * ROW, x + LIST_WIDTH, y + (row + 1) * ROW, SELECTION);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        if (macros.isEmpty()) {
            graphics.drawString(font, Component.translatable("logisticspipes.gui.disk.empty"), LIST_X + 2, LIST_Y + 2, LIST_TEXT, false);
        }
        for (int row = 0; row < ROWS && scroll + row < macros.size(); row++) {
            String macro = font.plainSubstrByWidth(macros.get(scroll + row), LIST_WIDTH - 4);
            graphics.drawString(font, macro, LIST_X + 2, LIST_Y + 2 + row * ROW, LIST_TEXT, false);
        }
    }
}
