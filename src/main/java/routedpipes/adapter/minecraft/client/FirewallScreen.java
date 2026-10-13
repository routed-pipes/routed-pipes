package routedpipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import routedpipes.adapter.minecraft.FirewallSettings;
import routedpipes.adapter.minecraft.menu.FirewallMenu;
import routedpipes.adapter.minecraft.menu.MarkerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FirewallScreen extends AbstractContainerScreen<FirewallMenu> implements MarkerScreen {

    private static final int TEXT = 0x404040;
    private static final String[] LABELS = {"listed", "providing", "crafting", "sorting", "power"};

    private final List<Button> switches = new ArrayList<>();

    public FirewallScreen(FirewallMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 222;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        switches.clear();
        for (int flag = 0; flag < FirewallSettings.FLAGS; flag++) {
            int id = flag;
            switches.add(addRenderableWidget(Button.builder(label(flag),
                    button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                    .bounds(leftPos + 120, topPos + 26 + flag * 22, 48, 12).build()));
        }
    }

    // every switch says whether its thing is stopped
    private Component label(int flag) {
        return Component.translatable(menu.flag(flag) ? "routedpipes.gui.firewall.blocked" : "routedpipes.gui.firewall.allowed");
    }

    @Override
    protected void containerTick() {
        for (int flag = 0; flag < switches.size(); flag++) {
            switches.get(flag).setMessage(label(flag));
        }
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
        for (int flag = 0; flag < FirewallSettings.FLAGS; flag++) {
            graphics.drawString(font, Component.translatable("routedpipes.gui.firewall." + LABELS[flag]), 120, 16 + flag * 22, TEXT, false);
        }
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
