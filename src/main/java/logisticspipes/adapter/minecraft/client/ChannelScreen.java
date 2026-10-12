package logisticspipes.adapter.minecraft.client;

import java.util.Locale;

import logisticspipes.adapter.minecraft.menu.ChannelMenu;
import logisticspipes.domain.channel.ChannelAccess;
import logisticspipes.domain.channel.Channels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

// the channels of inventory system connectors, on a connector with the settings of that pipe below them
public class ChannelScreen extends AbstractContainerScreen<ChannelMenu> {

    private static final int TEXT = 0x404040;
    private static final int SELECTION = 0xFF55CC55;

    private Button use;
    private Button access;
    private Button delete;
    private Button rename;
    private Button newAccess;
    private EditBox name;
    // deleting takes a second click, this is the slot the first one was for
    private int deleting = -1;

    public ChannelScreen(ChannelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = menu.onPipe() ? ChannelMenu.CONNECTOR_TOP + 94 : ChannelMenu.CONNECTOR_TOP;
    }

    @Override
    protected void init() {
        super.init();
        button("<", ChannelMenu.BUTTON_PREVIOUS_PAGE, 110, 15, 12, 12);
        button(">", ChannelMenu.BUTTON_NEXT_PAGE, 156, 15, 12, 12);
        use = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.channel.use"),
                button -> press(ChannelMenu.BUTTON_USE)).bounds(leftPos + 8, topPos + 62, 38, 14).build());
        use.visible = menu.onPipe();
        access = addRenderableWidget(Button.builder(Component.empty(), button -> press(ChannelMenu.BUTTON_ACCESS))
                .bounds(leftPos + 48, topPos + 62, 72, 14).build());
        delete = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (deleting == menu.selectedSlot()) {
                press(ChannelMenu.BUTTON_DELETE);
            } else {
                deleting = menu.selectedSlot();
            }
        }).bounds(leftPos + 122, topPos + 62, 46, 14).build());
        String typed = name == null ? "" : name.getValue();
        name = new EditBox(font, leftPos + 8, topPos + 80, 160, 14, Component.translatable("logisticspipes.channel.name"));
        name.setMaxLength(Channels.MAX_NAME);
        name.setValue(typed);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("logisticspipes.channel.add"),
                button -> send(ChannelMenu.TEXT_ADD)).bounds(leftPos + 8, topPos + 98, 38, 14).build());
        newAccess = addRenderableWidget(Button.builder(Component.empty(), button -> press(ChannelMenu.BUTTON_NEW_ACCESS))
                .bounds(leftPos + 48, topPos + 98, 72, 14).build());
        rename = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.channel.rename"),
                button -> send(ChannelMenu.TEXT_RENAME)).bounds(leftPos + 122, topPos + 98, 46, 14).build());
        if (menu.onPipe()) {
            // with shift held the resistance changes by ten
            addRenderableWidget(Button.builder(Component.literal("<"), button -> press(hasShiftDown()
                    ? ChannelMenu.BUTTON_RESISTANCE_DOWN_10 : ChannelMenu.BUTTON_RESISTANCE_DOWN))
                    .bounds(leftPos + 110, topPos + ChannelMenu.CONNECTOR_TOP + 21, 12, 12).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> press(hasShiftDown()
                    ? ChannelMenu.BUTTON_RESISTANCE_UP_10 : ChannelMenu.BUTTON_RESISTANCE_UP))
                    .bounds(leftPos + 156, topPos + ChannelMenu.CONNECTOR_TOP + 21, 12, 12).build());
        }
        update();
    }

    private void button(String label, int id, int x, int y, int width, int height) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> press(id))
                .bounds(leftPos + x, topPos + y, width, height).build());
    }

    private void press(int button) {
        deleting = -1;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private void send(int field) {
        ClientMenuLink.setText(menu.containerId, field, name.getValue());
        name.setValue("");
    }

    private static Component accessLabel(ChannelAccess access) {
        return Component.translatable("logisticspipes.channel.access." + access.name().toLowerCase(Locale.ROOT));
    }

    // shows the buttons with what the server last said
    private void update() {
        boolean selected = menu.selectedSlot() >= 0;
        if (deleting != menu.selectedSlot()) {
            deleting = -1;
        }
        use.active = selected;
        access.active = selected && menu.ownsSelected();
        access.setMessage(selected ? accessLabel(menu.selectedAccess()) : Component.empty());
        delete.active = selected && menu.ownsSelected();
        delete.setMessage(Component.translatable(deleting >= 0 ? "logisticspipes.channel.confirm" : "logisticspipes.channel.delete"));
        rename.active = selected && menu.ownsSelected();
        newAccess.setMessage(accessLabel(menu.newAccess()));
    }

    @Override
    protected void containerTick() {
        update();
    }

    // typing a name must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            name.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        if (menu.selectedSlot() >= 0) {
            Slot slot = menu.getSlot(ChannelMenu.FIRST_CHANNEL_SLOT + menu.selectedSlot());
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, SELECTION);
        }
    }

    private String shortened(String text, int width) {
        return font.plainSubstrByWidth(text, width);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, shortened(title.getString(), 160), 8, 6, TEXT, false);
        graphics.drawString(font, Component.translatable("logisticspipes.channel.title"), 8, 18, TEXT, false);
        String page = (menu.page() + 1) + "/" + menu.pages();
        graphics.drawString(font, page, 139 - font.width(page) / 2, 17, TEXT, false);
        int selected = menu.selectedSlot();
        ItemStack paper = selected < 0 ? ItemStack.EMPTY : menu.getSlot(ChannelMenu.FIRST_CHANNEL_SLOT + selected).getItem();
        Component which = paper.isEmpty() ? Component.translatable("logisticspipes.channel.select") : paper.getHoverName();
        graphics.drawString(font, shortened(which.getString(), 160), 8, 51, TEXT, false);
        if (!menu.onPipe()) {
            return;
        }
        int top = ChannelMenu.CONNECTOR_TOP;
        ItemStack current = menu.getSlot(ChannelMenu.CURRENT_SLOT).getItem();
        Component channel = current.isEmpty() ? Component.translatable("logisticspipes.channel.none")
                : Component.translatable("logisticspipes.channel.current", current.getHoverName());
        graphics.drawString(font, shortened(channel.getString(), 140), 28, top + 4, TEXT, false);
        graphics.drawString(font, Component.translatable("logisticspipes.channel.resistance"), 8, top + 23, TEXT, false);
        String resistance = Integer.toString(menu.resistance());
        graphics.drawString(font, resistance, 139 - font.width(resistance) / 2, top + 23, TEXT, false);
        graphics.drawString(font, Component.translatable("logisticspipes.channel.waiting"), 8, top + 39, TEXT, false);
    }
}
