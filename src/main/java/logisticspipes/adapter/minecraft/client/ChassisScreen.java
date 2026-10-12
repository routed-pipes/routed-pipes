package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ChassisScreen extends AbstractContainerScreen<ChassisMenu> {

    private Button turn;

    public ChassisScreen(ChassisMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        turn = addRenderableWidget(Button.builder(pointedLabel(),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ChassisMenu.BUTTON_TURN))
                .bounds(leftPos + 78, topPos + 14, 90, 16).build());
    }

    private Component pointedLabel() {
        if (menu.pointed() < 0) {
            return Component.translatable("logisticspipes.gui.pointed.none");
        }
        Direction side = Direction.from3DDataValue(menu.pointed());
        return Component.translatable("logisticspipes.gui.pointed", Component.translatable("logisticspipes.side." + side.getName()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        turn.setMessage(pointedLabel());
        turn.active = menu.pointed() >= 0;
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
    }
}
