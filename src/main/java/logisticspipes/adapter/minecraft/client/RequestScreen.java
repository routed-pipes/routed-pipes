package logisticspipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import logisticspipes.adapter.minecraft.menu.RequestMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class RequestScreen<M extends RequestMenu> extends AbstractContainerScreen<M> {

    private static final int TEXT = 0x404040;
    private static final int SELECTION = 0xFF55CC55;
    private static final int[] STEP_X = {8, 31, 54, 100, 119, 142};
    private static final int[] STEP_WIDTH = {22, 22, 18, 18, 22, 26};
    private static final int PREVIEW_WIDTH = 46;
    // behind the items of a preview: taken from stock, crafted, missing
    private static final int[] SOURCE_COLOURS = {0xFF55AA55, 0xFF5577CC, 0xFFCC5555};
    private static final int[] LEGEND_X = {8, 56, 114};
    private static final int LEGEND_Y = 110;

    private EditBox search;
    private Button request;
    private Button disk;
    private Button preview;

    public RequestScreen(M menu, Inventory inventory, Component title) {
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
        request = addRenderableWidget(Button.builder(requestLabel(),
                button -> press(RequestMenu.BUTTON_REQUEST)).bounds(leftPos + 8, topPos + 162, 160, 16).build());
        disk = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.disk"),
                button -> press(RequestMenu.BUTTON_DISK)).bounds(leftPos + 112, topPos + 162, 36, 16).build());
        preview = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.gui.preview"),
                button -> press(RequestMenu.BUTTON_PREVIEW)).bounds(leftPos + 122, topPos + 162, PREVIEW_WIDTH, 16).build());
        makeRoomForDisk();
        makeRoomForPreview();
    }

    protected Component requestLabel() {
        return Component.translatable("logisticspipes.gui.request");
    }

    // a pipe that takes a disk shows it in the corner, with a button for what is on it
    private void makeRoomForDisk() {
        request.setWidth(menu.takesDisk() ? 100 : 160);
        disk.visible = menu.takesDisk();
        disk.active = menu.holdsDisk();
    }

    // the preview button takes the right end of the request button, while the preview is on show it leads back to the list
    private void makeRoomForPreview() {
        preview.visible = menu.previews();
        if (!preview.visible) {
            return;
        }
        request.setWidth(request.getWidth() - PREVIEW_WIDTH - 2);
        preview.setX(request.getX() + request.getWidth() + 2);
        preview.setMessage(Component.translatable(menu.previewing() ? "logisticspipes.gui.preview.back" : "logisticspipes.gui.preview"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        makeRoomForDisk();
        makeRoomForPreview();
    }

    // typing in a text field must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected void press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Slot slot : menu.slots) {
            if (slot.index != RequestMenu.DISK_SLOT && slot.index < RequestMenu.FIRST_PREVIEW_SLOT + RequestMenu.PREVIEW_SIZE
                    && slot.isActive() && slot.hasItem()) {
                drawCount(graphics, leftPos + slot.x, topPos + slot.y, RequestMenu.listedCount(slot.getItem()));
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    // small text in the slot corner, the real stack count is always 1
    protected void drawCount(GuiGraphics graphics, int x, int y, int count) {
        String text = format(count);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 16 - font.width(text) * 0.5f, y + 11, 300);
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
        if (menu.previewing()) {
            for (int index = 0; index < RequestMenu.PREVIEW_SIZE; index++) {
                Slot slot = menu.slots.get(RequestMenu.FIRST_PREVIEW_SLOT + index);
                RequestMenu.sourceOf(slot.getItem()).ifPresent(source -> graphics.fill(leftPos + slot.x, topPos + slot.y,
                        leftPos + slot.x + 16, topPos + slot.y + 16, SOURCE_COLOURS[source.ordinal()]));
            }
            return;
        }
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
        if (menu.previewing()) {
            previewLabels(graphics);
        }
    }

    // below the previewed items: which colour stands for what, and the energy for moving it all
    private void previewLabels(GuiGraphics graphics) {
        RequestMenu.Source[] sources = RequestMenu.Source.values();
        for (int index = 0; index < sources.length; index++) {
            int x = LEGEND_X[index];
            graphics.fill(x, LEGEND_Y, x + 7, LEGEND_Y + 7, SOURCE_COLOURS[index]);
            graphics.drawString(font, Component.translatable("logisticspipes.gui.preview.legend."
                    + sources[index].name().toLowerCase(Locale.ROOT)), x + 9, LEGEND_Y, TEXT, false);
        }
        String energy = String.format(Locale.ROOT, "%,d", menu.previewEnergy()).replace(',', ' ');
        graphics.drawString(font, Component.translatable("logisticspipes.gui.preview.energy", energy), 8, LEGEND_Y + 10, TEXT, false);
    }

    // a previewed item says in words where it comes from
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        RequestMenu.sourceOf(stack).ifPresent(source -> tooltip.add(Component.translatable("logisticspipes.gui.preview."
                + source.name().toLowerCase(Locale.ROOT), RequestMenu.listedCount(stack)).withStyle(ChatFormatting.GRAY)));
        return tooltip;
    }
}
