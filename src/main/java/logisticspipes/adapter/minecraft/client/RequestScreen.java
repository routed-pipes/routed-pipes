package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.RequestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class RequestScreen extends AbstractContainerScreen<RequestMenu> {

    private static final int TEXT = 0x404040;
    private static final int SELECTION = 0xFF55CC55;
    private static final int[] STEP_X = {8, 31, 54, 100, 119, 142};
    private static final int[] STEP_WIDTH = {22, 22, 18, 18, 22, 26};

    private EditBox search;

    public RequestScreen(RequestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 186;
    }

    @Override
    protected void init() {
        super.init();
        String typed = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + 8, topPos + 4, 100, 12, Component.translatable("logisticspipes.gui.search"));
        search.setHint(Component.translatable("logisticspipes.gui.search"));
        search.setMaxLength(32);
        search.setValue(typed);
        search.setResponder(text -> ClientMenuLink.setText(menu.containerId, RequestMenu.TEXT_SEARCH, text));
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> press(RequestMenu.BUTTON_PREVIOUS_PAGE))
                .bounds(leftPos + 126, topPos + 4, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> press(RequestMenu.BUTTON_NEXT_PAGE))
                .bounds(leftPos + 156, topPos + 4, 12, 12).build());
        for (int step = 0; step < RequestMenu.AMOUNT_STEPS.length; step++) {
            int id = step;
            int amount = RequestMenu.AMOUNT_STEPS[step];
            addRenderableWidget(Button.builder(Component.literal(amount > 0 ? "+" + amount : Integer.toString(amount)),
                    button -> press(id)).bounds(leftPos + STEP_X[step], topPos + 142, STEP_WIDTH[step], 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.request"),
                button -> press(RequestMenu.BUTTON_REQUEST)).bounds(leftPos + 8, topPos + 162, 160, 16).build());
    }

    // typing in the search must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Slot slot : menu.slots) {
            if (slot.hasItem()) {
                drawCount(graphics, slot);
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    // small text in the slot corner, the real stack count is always 1
    private void drawCount(GuiGraphics graphics, Slot slot) {
        String text = format(RequestMenu.listedCount(slot.getItem()));
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + slot.x + 16 - font.width(text) * 0.5f, topPos + slot.y + 11, 300);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private static String format(int count) {
        if (count == 0) {
            return "craft";
        }
        if (count < 1000) {
            return Integer.toString(count);
        }
        if (count < 1_000_000) {
            return count / 1000 + "k";
        }
        return count / 1_000_000 + "M";
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        int selected = menu.selectedSlot();
        if (selected >= 0 && selected < menu.slots.size()) {
            Slot slot = menu.slots.get(selected);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, SELECTION);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String page = (menu.page() + 1) + "/" + menu.pages();
        graphics.drawString(font, page, 147 - font.width(page) / 2, 6, TEXT, false);
        int selected = menu.selectedSlot();
        ItemStack stack = selected >= 0 && selected < menu.slots.size() ? menu.slots.get(selected).getItem() : ItemStack.EMPTY;
        Component name = stack.isEmpty() ? Component.translatable("logisticspipes.gui.nothing_selected") : stack.getHoverName();
        graphics.drawString(font, font.plainSubstrByWidth(name.getString(), 160), 8, 130, TEXT, false);
        String amount = Integer.toString(menu.amount());
        graphics.drawString(font, amount, 86 - font.width(amount) / 2, 146, TEXT, false);
    }
}
