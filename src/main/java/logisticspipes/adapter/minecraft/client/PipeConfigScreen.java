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
    private static final int SELECTED = 0xFFFFD040;

    private Button defaultRoute;
    private Button mode;
    private Button exclude;
    private Button importCleanup;
    // the ingredient whose satellite is being set, only with the upgrade for that
    private int satelliteSlot;
    private Button previousSlot;
    private Button nextSlot;

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
            exclude = addRenderableWidget(Button.builder(excludeLabel(), button -> press(PipeConfigMenu.BUTTON_EXCLUDE))
                    .bounds(leftPos + 120, topPos + 95, 48, 16).build());
            importCleanup = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.import"),
                    button -> press(PipeConfigMenu.BUTTON_IMPORT_CLEANUP)).bounds(leftPos + 120, topPos + 114, 48, 16).build());
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
        if (menu.kind() == PipeKind.CRAFTING) {
            previousSlot = addRenderableWidget(Button.builder(Component.literal("<"), button -> turnSatelliteSlot(-1))
                    .bounds(x - 30, y + 12, 10, 10).build());
            nextSlot = addRenderableWidget(Button.builder(Component.literal(">"), button -> turnSatelliteSlot(1))
                    .bounds(x + 20, y + 12, 10, 10).build());
        }
        addRenderableWidget(Button.builder(Component.literal("-"), button -> pressSatellite(false))
                .bounds(x - 30, y - 3, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> pressSatellite(true))
                .bounds(x + 16, y - 3, 14, 14).build());
    }

    private void turnSatelliteSlot(int change) {
        satelliteSlot = Math.floorMod(satelliteSlot + change, PipeBlockEntity.OUTPUT_SLOT);
    }

    // with the upgrade the buttons set the satellite of the chosen ingredient, without it the one of the bottom row
    private void pressSatellite(boolean up) {
        if (menu.advancedSatellite()) {
            press(PipeConfigMenu.BUTTON_SLOT_SATELLITE + satelliteSlot * 2 + (up ? 1 : 0));
        } else {
            press(up ? PipeConfigMenu.BUTTON_SATELLITE_UP : PipeConfigMenu.BUTTON_SATELLITE_DOWN);
        }
    }

    private int shownSatellite() {
        return menu.advancedSatellite() ? menu.slotSatellite(satelliteSlot) : menu.satelliteId();
    }

    private Component defaultRouteLabel() {
        return Component.translatable(menu.defaultRoute() ? "logisticspipes.gui.default_route.on" : "logisticspipes.gui.default_route.off");
    }

    private Component modeLabel() {
        String group = menu.kind() == PipeKind.PROVIDER ? "provider_mode" : "supply_mode";
        return Component.translatable("logisticspipes.gui." + group + "." + Math.max(0, menu.mode()));
    }

    // for a crafter the list is what stays in the machine or what goes
    private Component excludeLabel() {
        if (menu.kind() == PipeKind.CRAFTING) {
            return Component.translatable(menu.exclude() ? "logisticspipes.gui.cleanup.keep" : "logisticspipes.gui.cleanup.remove");
        }
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
        if (previousSlot != null) {
            previousSlot.visible = menu.advancedSatellite();
            nextSlot.visible = menu.advancedSatellite();
        }
        if (importCleanup != null) {
            importCleanup.visible = menu.cleanupSlots() > 0;
            exclude.visible = menu.cleanupSlots() > 0;
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
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT && menu.kind() == PipeKind.CRAFTING; slot++) {
            // without the upgrade the bottom row of the recipe is what goes to the satellite
            boolean toSatellite = menu.advancedSatellite() ? menu.slotSatellite(slot) != 0
                    : menu.satelliteId() != 0 && slot >= PipeBlockEntity.SATELLITE_ROW;
            int x = leftPos + menu.getSlot(slot).x;
            int y = topPos + menu.getSlot(slot).y;
            if (toSatellite) {
                graphics.fill(x, y, x + 16, y + 16, SATELLITE_TINT);
            }
            if (menu.advancedSatellite() && slot == satelliteSlot) {
                graphics.renderOutline(x - 1, y - 1, 18, 18, SELECTED);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.kind() == PipeKind.CRAFTING && menu.hasByproduct()) {
            graphics.drawString(font, Component.translatable("logisticspipes.gui.byproduct"), 144, 16, TEXT, false);
        }
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
        String id = shownSatellite() == 0 ? "-" : Integer.toString(shownSatellite());
        if (menu.kind() == PipeKind.CRAFTING && menu.advancedSatellite()) {
            Component slot = Component.translatable("logisticspipes.gui.satellite.slot", satelliteSlot + 1);
            graphics.drawString(font, slot, satelliteX() - font.width(slot) / 2, satelliteY() + 13, TEXT, false);
        }
        graphics.drawString(font, id, satelliteX() - font.width(id) / 2, satelliteY(), TEXT, false);
    }
}
