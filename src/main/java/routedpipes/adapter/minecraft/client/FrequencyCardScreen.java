package routedpipes.adapter.minecraft.client;

import routedpipes.adapter.minecraft.menu.FrequencyCardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FrequencyCardScreen extends AbstractContainerScreen<FrequencyCardMenu> {

    public FrequencyCardScreen(FrequencyCardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = FrequencyCardMenu.INVENTORY_TOP + 82;
        inventoryLabelY = imageHeight - 94;
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
}
