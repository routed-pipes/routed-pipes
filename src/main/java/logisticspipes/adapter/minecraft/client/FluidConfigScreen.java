package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.FluidConfigMenu;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class FluidConfigScreen extends AbstractContainerScreen<FluidConfigMenu> {

    private static final int TEXT = 0x404040;

    private Button partial;
    private Button minimum;

    public FluidConfigScreen(FluidConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind() == PipeKind.FLUID_SATELLITE) {
            int x = leftPos + imageWidth / 2;
            addRenderableWidget(Button.builder(Component.literal("-"), button -> press(FluidConfigMenu.BUTTON_SATELLITE_DOWN))
                    .bounds(x - 30, topPos + 37, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> press(FluidConfigMenu.BUTTON_SATELLITE_UP))
                    .bounds(x + 16, topPos + 37, 14, 14).build());
        }
        if (menu.kind() == PipeKind.FLUID_SUPPLIER) {
            partial = addRenderableWidget(Button.builder(partialLabel(), button -> press(FluidConfigMenu.BUTTON_PARTIAL))
                    .bounds(leftPos + 8, topPos + 36, 78, 11).build());
            minimum = addRenderableWidget(Button.builder(minimumLabel(), button -> press(FluidConfigMenu.BUTTON_MINIMUM))
                    .bounds(leftPos + 90, topPos + 36, 78, 11).build());
            // a row of buttons that take away and a row of buttons that add
            int perRow = FluidConfigMenu.AMOUNT_STEPS.length / 2;
            for (int step = 0; step < FluidConfigMenu.AMOUNT_STEPS.length; step++) {
                int id = FluidConfigMenu.BUTTON_FIRST_STEP + step;
                int amount = FluidConfigMenu.AMOUNT_STEPS[step];
                addRenderableWidget(Button.builder(Component.literal(amount > 0 ? "+" + amount : Integer.toString(amount)),
                        button -> press(id)).bounds(leftPos + 8 + step % perRow * 40, topPos + 48 + step / perRow * 12, 38, 11).build());
            }
        }
    }

    private Component partialLabel() {
        return Component.translatable(menu.partial() ? "logisticspipes.gui.fluid.partial.on" : "logisticspipes.gui.fluid.partial.off");
    }

    private Component minimumLabel() {
        return Component.translatable("logisticspipes.gui.fluid.minimum." + menu.minimum());
    }

    @Override
    protected void containerTick() {
        if (partial != null) {
            partial.setMessage(partialLabel());
            minimum.setMessage(minimumLabel());
        }
    }

    private void press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
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
        if (menu.kind() == PipeKind.FLUID_BASIC) {
            // the name of the fluid next to its slot
            ItemStack marker = menu.getSlot(0).getItem();
            Component name = marker.isEmpty() ? Component.translatable("logisticspipes.gui.fluid.none") : marker.getHoverName();
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), 120), 48, 39, TEXT, false);
        }
        if (menu.kind() == PipeKind.FLUID_SUPPLIER) {
            graphics.drawString(font, Component.translatable("logisticspipes.gui.fluid.stock", menu.amount()), 48, 22, TEXT, false);
        }
        if (menu.kind() == PipeKind.FLUID_SATELLITE) {
            Component label = Component.translatable("logisticspipes.gui.satellite");
            graphics.drawString(font, label, imageWidth / 2 - font.width(label) / 2, 26, TEXT, false);
            String id = menu.satelliteId() == 0 ? "-" : Integer.toString(menu.satelliteId());
            graphics.drawString(font, id, imageWidth / 2 - font.width(id) / 2, 40, TEXT, false);
        }
    }
}
