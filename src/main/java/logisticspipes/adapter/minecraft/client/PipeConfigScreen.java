package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.MarkerMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PipeConfigScreen extends AbstractContainerScreen<PipeConfigMenu> implements MarkerScreen {

    private static final int TEXT = 0x404040;

    private Button defaultRoute;

    public PipeConfigScreen(PipeConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private boolean hasSatelliteControls() {
        return menu.kind() == PipeKind.CRAFTING || menu.kind() == PipeKind.SATELLITE;
    }

    // crafting pipes show the satellite under the output slot, satellite pipes in the middle
    private int satelliteX() {
        return menu.kind() == PipeKind.CRAFTING ? 132 : imageWidth / 2;
    }

    private int satelliteY() {
        return menu.kind() == PipeKind.CRAFTING ? 58 : 40;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind() == PipeKind.BASIC) {
            defaultRoute = addRenderableWidget(Button.builder(defaultRouteLabel(),
                    button -> press(PipeConfigMenu.BUTTON_DEFAULT_ROUTE)).bounds(leftPos + 92, topPos + 34, 76, 16).build());
        }
        if (menu.kind() == PipeKind.CRAFTING) {
            addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.import"),
                    button -> press(PipeConfigMenu.BUTTON_IMPORT_RECIPE)).bounds(leftPos + 85, topPos + 25, 38, 14).build());
        }
        if (!hasSatelliteControls()) {
            return;
        }
        int x = leftPos + satelliteX();
        int y = topPos + satelliteY();
        addRenderableWidget(Button.builder(Component.literal("-"), button -> press(PipeConfigMenu.BUTTON_SATELLITE_DOWN))
                .bounds(x - 30, y - 3, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> press(PipeConfigMenu.BUTTON_SATELLITE_UP))
                .bounds(x + 16, y - 3, 14, 14).build());
    }

    private Component defaultRouteLabel() {
        return Component.translatable(menu.defaultRoute() ? "logisticspipes.gui.default_route.on" : "logisticspipes.gui.default_route.off");
    }

    @Override
    protected void containerTick() {
        if (defaultRoute != null) {
            defaultRoute.setMessage(defaultRouteLabel());
        }
    }

    private void press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
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
        if (!hasSatelliteControls()) {
            return;
        }
        Component label = Component.translatable("logisticspipes.gui.satellite");
        graphics.drawString(font, label, satelliteX() - font.width(label) / 2, satelliteY() - 14, TEXT, false);
        String id = menu.satelliteId() == 0 ? "-" : Integer.toString(menu.satelliteId());
        graphics.drawString(font, id, satelliteX() - font.width(id) / 2, satelliteY(), TEXT, false);
    }
}
