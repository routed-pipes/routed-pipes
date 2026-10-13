package logisticspipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ChassisScreen extends AbstractContainerScreen<ChassisMenu> {

    private Button turn;
    private final List<Button> settings = new ArrayList<>();

    public ChassisScreen(ChassisMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = ChassisMenu.INVENTORY_TOP + 82;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        turn = addRenderableWidget(Button.builder(pointedLabel(),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ChassisMenu.BUTTON_TURN))
                .bounds(leftPos + 78, topPos + 14, 90, 16).build());
        settings.clear();
        for (int slot = 0; slot < PipeBlockEntity.MODULE_SLOTS; slot++) {
            int button = ChassisMenu.BUTTON_SETTINGS + slot;
            settings.add(addRenderableWidget(Button.builder(Component.literal("!"),
                    pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button))
                    .bounds(leftPos + 19 + slot * 18, topPos + 54, 12, 12).build()));
        }
    }

    private Component pointedLabel() {
        if (menu.pointed() < 0) {
            return Component.translatable("routedpipes.gui.pointed.none");
        }
        Direction side = Direction.from3DDataValue(menu.pointed());
        return Component.translatable("routedpipes.gui.pointed", Component.translatable("routedpipes.side." + side.getName()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        turn.setMessage(pointedLabel());
        turn.active = menu.pointed() >= 0;
        for (int slot = 0; slot < settings.size(); slot++) {
            // only modules that have something to set up get a button
            settings.get(slot).visible = menu.getSlot(slot).isActive()
                    && menu.getSlot(slot).getItem().getItem() instanceof ModuleItem module && module.hasSettings();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
    }
}
