package routedpipes.adapter.minecraft.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import routedpipes.adapter.minecraft.menu.MarkerMenu;
import routedpipes.adapter.minecraft.menu.RequestTableMenu;
import routedpipes.adapter.minecraft.menu.Shortfall;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class RequestTableScreen extends AbstractContainerScreen<RequestTableMenu> implements MarkerScreen {

    private static final int TEXT = 0x404040;
    private static final int MISSING = 0xA02020;
    private static final int READY = 0x206020;
    private static final int ARROW = 0xFF8B8B8B;
    private static final int MISSING_X = 52;
    private static final int MISSING_Y = 77;

    private Button disk;

    public RequestTableScreen(RequestTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 270;
        inventoryLabelY = imageHeight - 94;
    }

    private Button button(String label, String tooltip, int id, int x, int y, int width) {
        Button.Builder builder = Button.builder(Component.translatable("routedpipes.gui.request_table." + label),
                pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                .bounds(leftPos + x, topPos + y, width, 14);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(Component.translatable("routedpipes.gui.request_table." + tooltip)));
        }
        return addRenderableWidget(builder.build());
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("X"),
                pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RequestTableMenu.BUTTON_CLEAR))
                .bounds(leftPos + 66, topPos + 18, 12, 12)
                .tooltip(Tooltip.create(Component.translatable("routedpipes.gui.request_table.clear"))).build());
        EditBox amount = new EditBox(font, leftPos + 112, topPos + 18, 56, 12, Component.translatable("routedpipes.gui.request_table.amount"));
        amount.setMaxLength(3);
        amount.setFilter(text -> text.chars().allMatch(Character::isDigit));
        amount.setValue(Integer.toString(menu.amount()));
        amount.setResponder(text -> ClientMenuLink.setText(menu.containerId, RequestTableMenu.TEXT_AMOUNT, text));
        amount.setTooltip(Tooltip.create(Component.translatable("routedpipes.gui.request_table.amount.tooltip")));
        addRenderableWidget(amount);
        button("order", "order.tooltip", RequestTableMenu.BUTTON_ORDER, 112, 33, 56);
        button("craft", "craft.tooltip", RequestTableMenu.BUTTON_CRAFT, 112, 49, 56);
        // the macros on the disk in the slot beside it
        disk = addRenderableWidget(Button.builder(Component.translatable("routedpipes.gui.disk"),
                pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RequestTableMenu.BUTTON_DISK))
                .bounds(leftPos + 27, topPos + 97, 30, 14).build());
        disk.active = menu.holdsDisk();
        button("store_all", "store_all.tooltip", RequestTableMenu.BUTTON_STORE_ALL, 82, 97, 48);
        button("list", null, RequestTableMenu.BUTTON_LIST, 132, 97, 36);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        disk.active = menu.holdsDisk();
    }

    // typing the amount must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        List<ItemStack> missing = menu.missing();
        for (int index = 0; index < missing.size(); index++) {
            int x = leftPos + MISSING_X + index * 17;
            int y = topPos + MISSING_Y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(font, Component.translatable("routedpipes.request.missing_entry",
                        Shortfall.count(missing.get(index)), missing.get(index).getHoverName()), mouseX, mouseY);
            }
        }
        if (hoveredSlot == menu.getSlot(RequestTableMenu.SORT_SLOT) && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderTooltip(font, Component.translatable("routedpipes.gui.request_table.sort"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        // arrow from the grid to the result
        graphics.fill(leftPos + 66, topPos + 42, leftPos + 82, topPos + 46, ARROW);
        graphics.fill(leftPos + 82, topPos + 38, leftPos + 86, topPos + 50, ARROW);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("routedpipes.gui.request_table.amount"), 112, 6, TEXT, false);
        if (menu.pending() > 0) {
            Component pending = Component.translatable("routedpipes.gui.request_table.pending", menu.pending());
            graphics.drawString(font, pending, 98 - font.width(pending) / 2, 57, TEXT, false);
        }
        if (!menu.getSlot(RequestTableMenu.RESULT_SLOT).hasItem()) {
            return;
        }
        List<ItemStack> missing = menu.missing();
        if (missing.isEmpty()) {
            graphics.drawString(font, Component.translatable("routedpipes.gui.request_table.ready"), 8, 81, READY, false);
            return;
        }
        graphics.drawString(font, Component.translatable("routedpipes.gui.request_table.missing"), 8, 81, MISSING, false);
        for (int index = 0; index < missing.size(); index++) {
            int x = MISSING_X + index * 17;
            graphics.renderItem(missing.get(index), x, MISSING_Y);
            graphics.renderItemDecorations(font, missing.get(index), x, MISSING_Y, shortCount(Shortfall.count(missing.get(index))));
        }
        if (menu.missingKinds() > missing.size()) {
            Component more = Component.translatable("routedpipes.gui.request_table.more", menu.missingKinds() - missing.size());
            graphics.drawString(font, more, imageWidth - 6 - font.width(more), 67, MISSING, false);
        }
    }

    // fits on an item, the tooltip has the exact number
    private static String shortCount(int count) {
        return count < 1000 ? Integer.toString(count) : count < 1_000_000 ? count / 1000 + "k" : count / 1_000_000 + "M";
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
