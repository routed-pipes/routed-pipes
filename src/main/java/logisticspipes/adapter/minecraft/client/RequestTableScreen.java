package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.MarkerMenu;
import logisticspipes.adapter.minecraft.menu.RequestTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RequestTableScreen extends AbstractContainerScreen<RequestTableMenu> implements MarkerScreen {

    private static final int TEXT = 0x404040;

    public RequestTableScreen(RequestTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 228;
        inventoryLabelY = imageHeight - 94;
    }

    private void button(String label, String tooltip, int id, int x, int y, int width) {
        addRenderableWidget(Button.builder(Component.literal(label),
                pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                .bounds(leftPos + x, topPos + y, width, 14)
                .tooltip(Tooltip.create(Component.translatable("logisticspipes.gui.request_table." + tooltip))).build());
    }

    @Override
    protected void init() {
        super.init();
        button("X", "clear", RequestTableMenu.BUTTON_CLEAR, 66, 18, 14);
        button("~", "missing", RequestTableMenu.BUTTON_ORDER_MISSING, 66, 36, 14);
        button("+", "one", RequestTableMenu.BUTTON_ORDER_ONE, 114, 18, 26);
        button("++", "ten", RequestTableMenu.BUTTON_ORDER_TEN, 114, 34, 26);
        button("+++", "stack", RequestTableMenu.BUTTON_ORDER_STACK, 114, 50, 26);
        addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.request_table.list"),
                pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RequestTableMenu.BUTTON_LIST))
                .bounds(leftPos + 142, topPos + 50, 28, 14).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("logisticspipes.gui.request_table.sort"), 146, 38, TEXT, false);
    }

    @Override
    public int left() {
        return leftPos;
    }

    @Override
    public int top() {
        return topPos;
    }

    @Override
    public MarkerMenu markers() {
        return menu;
    }

    @Override
    public int containerId() {
        return menu.containerId;
    }
}
