package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.CraftingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CraftingTableScreen extends AbstractContainerScreen<CraftingTableMenu> {

    public CraftingTableScreen(CraftingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 216;
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
        // arrow from the grid to the result
        graphics.fill(leftPos + 92, topPos + 41, leftPos + 110, topPos + 45, 0xFF8B8B8B);
        graphics.fill(leftPos + 110, topPos + 37, leftPos + 114, topPos + 49, 0xFF8B8B8B);
    }
}
