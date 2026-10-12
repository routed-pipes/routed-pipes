package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
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
    private static final int SATELLITE_TINT = 0xFF6F8FBF;

    private Button defaultRoute;
    private Button mode;
    private Button exclude;

    public PipeConfigScreen(PipeConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = PipeConfigMenu.inventoryTop(menu.kind()) + 82;
        inventoryLabelY = imageHeight - 94;
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
        if (menu.kind() == PipeKind.PROVIDER) {
            mode = addRenderableWidget(Button.builder(modeLabel(), button -> press(PipeConfigMenu.BUTTON_MODE))
                    .bounds(leftPos + 68, topPos + 22, 100, 16).build());
            exclude = addRenderableWidget(Button.builder(excludeLabel(), button -> press(PipeConfigMenu.BUTTON_EXCLUDE))
                    .bounds(leftPos + 68, topPos + 42, 100, 16).build());
        }
        if (menu.kind() == PipeKind.SUPPLIER) {
            mode = addRenderableWidget(Button.builder(modeLabel(), button -> press(PipeConfigMenu.BUTTON_MODE))
                    .bounds(leftPos + 120, topPos + 34, 48, 16).build());
        }
        if (menu.kind() == PipeKind.CRAFTING) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> press(PipeConfigMenu.BUTTON_PRIORITY_DOWN))
                    .bounds(leftPos + 96, topPos + 74, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> press(PipeConfigMenu.BUTTON_PRIORITY_UP))
                    .bounds(leftPos + 132, topPos + 74, 14, 14).build());
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

    private Component modeLabel() {
        String group = menu.kind() == PipeKind.PROVIDER ? "provider_mode" : "supply_mode";
        return Component.translatable("logisticspipes.gui." + group + "." + Math.max(0, menu.mode()));
    }

    private Component excludeLabel() {
        return Component.translatable(menu.exclude() ? "logisticspipes.gui.exclude" : "logisticspipes.gui.include");
    }

    @Override
    protected void containerTick() {
        if (defaultRoute != null) {
            defaultRoute.setMessage(defaultRouteLabel());
        }
        if (mode != null) {
            // modules that share this layout have no mode to choose
            mode.visible = menu.mode() >= 0;
            mode.setMessage(modeLabel());
        }
        if (exclude != null) {
            exclude.setMessage(excludeLabel());
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
        if (menu.kind() == PipeKind.CRAFTING && menu.satelliteId() != 0) {
            // the bottom row of the recipe is what goes to the satellite
            for (int slot = PipeBlockEntity.SATELLITE_ROW; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
                int x = leftPos + menu.getSlot(slot).x;
                int y = topPos + menu.getSlot(slot).y;
                graphics.fill(x, y, x + 16, y + 16, SATELLITE_TINT);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.kind() == PipeKind.CRAFTING) {
            graphics.drawString(font, Component.translatable("logisticspipes.gui.priority"), 30, 77, TEXT, false);
            String priority = Integer.toString(menu.priority());
            graphics.drawString(font, priority, 121 - font.width(priority) / 2, 77, TEXT, false);
        }
        if (!hasSatelliteControls()) {
            return;
        }
        Component label = Component.translatable("logisticspipes.gui.satellite");
        graphics.drawString(font, label, satelliteX() - font.width(label) / 2, satelliteY() - 14, TEXT, false);
        String id = menu.satelliteId() == 0 ? "-" : Integer.toString(menu.satelliteId());
        graphics.drawString(font, id, satelliteX() - font.width(id) / 2, satelliteY(), TEXT, false);
    }
}
