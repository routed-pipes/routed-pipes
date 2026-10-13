package logisticspipes.adapter.minecraft.client;

import java.util.Locale;

import logisticspipes.adapter.minecraft.menu.HudSettingsMenu;
import logisticspipes.domain.hud.HudCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

// a check box per kind of panel, clicking one asks the server to toggle it
public class HudSettingsScreen extends AbstractContainerScreen<HudSettingsMenu> {

    private static final int BOX_X = 30;
    private static final int LABEL_X = 50;
    private static final int FIRST_Y = 10;
    private static final int STEP = 20;
    private static final int BOX = 10;

    public HudSettingsScreen(HudSettingsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = HudSettingsMenu.WIDTH;
        imageHeight = HudSettingsMenu.HEIGHT;
    }

    private static Component label(HudCategory category) {
        return Component.translatable("routedpipes.hud.setting." + category.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        for (HudCategory category : HudCategory.values()) {
            int x = leftPos + BOX_X;
            int y = topPos + FIRST_Y + STEP * category.ordinal();
            graphics.fill(x, y, x + BOX, y + BOX, 0xFF373737);
            graphics.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0xFF8B8B8B);
            if (menu.settings().shows(category)) {
                graphics.fill(x + 3, y + 3, x + BOX - 3, y + BOX - 3, 0xFF404040);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        for (HudCategory category : HudCategory.values()) {
            graphics.drawString(font, label(category), LABEL_X, FIRST_Y + STEP * category.ordinal() + 1, 0x404040, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (HudCategory category : HudCategory.values()) {
            int y = topPos + FIRST_Y + STEP * category.ordinal();
            if (mouseX >= leftPos + BOX_X && mouseX < leftPos + LABEL_X + font.width(label(category)) && mouseY >= y && mouseY < y + BOX) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, category.ordinal());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
