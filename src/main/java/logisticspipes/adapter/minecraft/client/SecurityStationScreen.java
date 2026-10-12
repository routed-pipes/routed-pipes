package logisticspipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import logisticspipes.adapter.minecraft.menu.SecurityStationMenu;
import logisticspipes.domain.security.Permission;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class SecurityStationScreen extends AbstractContainerScreen<SecurityStationMenu> {

    private static final int TEXT = 0x404040;
    private static final int ID = 0x2020C0;
    private static final int SELECTION = 0xFF55CC55;
    private static final int SUGGESTIONS_TOP = 184;
    private static final int SUGGESTION_HEIGHT = 11;
    private static final int MAX_SUGGESTIONS = 3;

    private static final int COMPUTERS_TOP = 88;
    private static final int COMPUTER_COLUMNS = 3;

    private static final List<Tabs.Tab> TABS = List.of(
            Tabs.Tab.texture("lock", Component.translatable("logisticspipes.gui.tab.station")),
            Tabs.Tab.item(Items.PLAYER_HEAD, Component.translatable("logisticspipes.gui.tab.players")),
            Tabs.Tab.item(Items.COMPARATOR, Component.translatable("logisticspipes.gui.tab.computers")));
    private final List<Button> stationButtons = new ArrayList<>();
    private final List<Button> playerButtons = new ArrayList<>();
    private final List<Button> computerButtons = new ArrayList<>();
    private Button computersAllowed;
    private EditBox computer;
    private final List<Button> permissions = new ArrayList<>();
    private Button authorized;
    private Button automatedRemoval;
    private EditBox name;

    public SecurityStationScreen(SecurityStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = SecurityStationMenu.INVENTORY_TOP + 82;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        stationButtons.clear();
        playerButtons.clear();
        computerButtons.clear();
        permissions.clear();
        automatedRemoval = station(Button.builder(Component.empty(), button -> press(SecurityStationMenu.BUTTON_AUTOMATED_REMOVAL))
                .bounds(leftPos + 8, topPos + 54, 160, 16).build());
        authorized = station(Button.builder(Component.empty(), button -> press(SecurityStationMenu.BUTTON_AUTHORIZED))
                .bounds(leftPos + 8, topPos + 74, 160, 16).build());
        station(Button.builder(Component.literal("-"), button -> press(SecurityStationMenu.BUTTON_REMOVE_CARD))
                .bounds(leftPos + 8, topPos + 106, 20, 18).build());
        station(Button.builder(Component.literal("+"), button -> press(SecurityStationMenu.BUTTON_ADD_CARD))
                .bounds(leftPos + 30, topPos + 106, 20, 18).build());
        station(Button.builder(Component.translatable("logisticspipes.channel.title"),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SecurityStationMenu.BUTTON_CHANNELS))
                .bounds(leftPos + 88, topPos + 106, 80, 18).build());
        players(Button.builder(Component.literal("<"), button -> press(SecurityStationMenu.BUTTON_PREVIOUS_PAGE))
                .bounds(leftPos + 110, topPos + 27, 12, 12).build());
        players(Button.builder(Component.literal(">"), button -> press(SecurityStationMenu.BUTTON_NEXT_PAGE))
                .bounds(leftPos + 156, topPos + 27, 12, 12).build());
        for (Permission permission : Permission.values()) {
            int id = SecurityStationMenu.BUTTON_PERMISSION + permission.ordinal();
            permissions.add(players(Button.builder(Component.empty(), button -> press(id))
                    .bounds(leftPos + 8, topPos + 76 + permission.ordinal() * 15, 160, 14).build()));
        }
        String typed = name == null ? "" : name.getValue();
        name = new EditBox(font, leftPos + 8, topPos + 167, 110, 14, Component.translatable("logisticspipes.security.add_player"));
        name.setMaxLength(16);
        name.setValue(typed);
        addRenderableWidget(name);
        players(Button.builder(Component.translatable("logisticspipes.security.add"), button -> {
            ClientMenuLink.setText(menu.containerId, SecurityStationMenu.TEXT_ADD_PLAYER, name.getValue());
            name.setValue("");
        }).bounds(leftPos + 122, topPos + 166, 46, 16).build());
        computersAllowed = computers(Button.builder(Component.empty(), button -> press(SecurityStationMenu.BUTTON_COMPUTERS_ALLOWED))
                .bounds(leftPos + 8, topPos + 44, 160, 16).build());
        computers(Button.builder(Component.literal("<"), button -> press(SecurityStationMenu.BUTTON_PREVIOUS_COMPUTERS))
                .bounds(leftPos + 110, topPos + 27, 12, 12).build());
        computers(Button.builder(Component.literal(">"), button -> press(SecurityStationMenu.BUTTON_NEXT_COMPUTERS))
                .bounds(leftPos + 156, topPos + 27, 12, 12).build());
        String number = computer == null ? "" : computer.getValue();
        computer = new EditBox(font, leftPos + 8, topPos + 141, 62, 14, Component.translatable("logisticspipes.security.computer_id"));
        computer.setMaxLength(10);
        computer.setFilter(text -> text.matches("-?\\d*"));
        computer.setValue(number);
        addRenderableWidget(computer);
        computers(Button.builder(Component.translatable("logisticspipes.security.add"),
                button -> ClientMenuLink.setText(menu.containerId, SecurityStationMenu.TEXT_ADD_COMPUTER, computer.getValue()))
                .bounds(leftPos + 74, topPos + 140, 40, 16).build());
        computers(Button.builder(Component.translatable("logisticspipes.security.remove"),
                button -> ClientMenuLink.setText(menu.containerId, SecurityStationMenu.TEXT_REMOVE_COMPUTER, computer.getValue()))
                .bounds(leftPos + 118, topPos + 140, 50, 16).build());
        update();
    }

    // the tab for computers is only there in a game that has them
    private List<Tabs.Tab> tabs() {
        return menu.hasComputers() ? TABS : TABS.subList(0, 2);
    }

    private Button computers(Button button) {
        computerButtons.add(button);
        return addRenderableWidget(button);
    }

    private Button station(Button button) {
        stationButtons.add(button);
        return addRenderableWidget(button);
    }

    private Button players(Button button) {
        playerButtons.add(button);
        return addRenderableWidget(button);
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "logisticspipes.gui.yes" : "logisticspipes.gui.no"));
    }

    // shows the widgets of the open tab with what the server last said
    private void update() {
        SecurityStationMenu.Tab tab = menu.tab();
        stationButtons.forEach(button -> button.visible = tab == SecurityStationMenu.Tab.STATION);
        playerButtons.forEach(button -> button.visible = tab == SecurityStationMenu.Tab.PLAYERS);
        computerButtons.forEach(button -> button.visible = tab == SecurityStationMenu.Tab.COMPUTERS);
        name.visible = tab == SecurityStationMenu.Tab.PLAYERS;
        if (!name.visible) {
            name.setFocused(false);
        }
        computer.visible = tab == SecurityStationMenu.Tab.COMPUTERS;
        if (!computer.visible) {
            computer.setFocused(false);
        }
        computersAllowed.setMessage(onOff("logisticspipes.security.computers_allowed", menu.computersAllowed()));
        automatedRemoval.setMessage(onOff("logisticspipes.security.automated_removal", menu.automatedRemoval()));
        authorized.setMessage(onOff("logisticspipes.security.cards_valid", menu.authorized()));
        for (Permission permission : Permission.values()) {
            Button button = permissions.get(permission.ordinal());
            button.active = menu.selectedSlot() >= 0;
            button.setMessage(onOff("logisticspipes.security.permission." + permission.name().toLowerCase(Locale.ROOT),
                    menu.allowed(permission)));
        }
    }

    @Override
    protected void containerTick() {
        update();
    }

    // the menu is told on this side as well, the slots of the tab are there at once that way
    private void press(int button) {
        menu.clickMenuButton(minecraft.player, button);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    // the players who are online and match what was typed so far
    private List<String> suggestions() {
        String typed = name.getValue().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (PlayerInfo player : minecraft.getConnection().getOnlinePlayers()) {
            String candidate = player.getProfile().getName();
            if (candidate.toLowerCase(Locale.ROOT).contains(typed)) {
                names.add(candidate);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // as many of them as fit under the field, with a line left for the dots when there are more
    private static List<String> shown(List<String> suggestions) {
        return suggestions.size() <= MAX_SUGGESTIONS ? suggestions : suggestions.subList(0, MAX_SUGGESTIONS - 1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = Tabs.at(leftPos, topPos, tabs().size(), mouseX, mouseY);
        if (tab >= 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            press(tab == SecurityStationMenu.Tab.COMPUTERS.ordinal() ? SecurityStationMenu.BUTTON_COMPUTERS : tab);
            update();
            return true;
        }
        if (menu.tab() == SecurityStationMenu.Tab.PLAYERS && mouseX >= leftPos + 8 && mouseX < leftPos + 168) {
            // a click on a suggested name takes it over
            int line = (int) Math.floor((mouseY - topPos - SUGGESTIONS_TOP) / SUGGESTION_HEIGHT);
            List<String> suggestions = shown(suggestions());
            if (line >= 0 && line < suggestions.size()) {
                name.setValue(suggestions.get(line));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // typing a name must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            name.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (computer.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            computer.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int hovered = Tabs.at(leftPos, topPos, tabs().size(), mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderTooltip(font, tabs().get(hovered).name(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        Tabs.draw(graphics, leftPos, topPos, tabs(), menu.tab().ordinal());
        int selected = menu.selectedSlot();
        if (menu.tab() == SecurityStationMenu.Tab.PLAYERS && selected >= 0) {
            Slot slot = menu.getSlot(SecurityStationMenu.FIRST_PLAYER_SLOT + selected);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, SELECTION);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        switch (menu.tab()) {
            case STATION -> station(graphics);
            case PLAYERS -> players(graphics);
            case COMPUTERS -> computers(graphics);
        }
    }

    // the switch, and under it the ids it does not go for
    private void computers(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("logisticspipes.gui.tab.computers"), 8, 30, TEXT, false);
        String page = (menu.computerPage() + 1) + "/" + menu.computerPages();
        graphics.drawString(font, page, 139 - font.width(page) / 2, 29, TEXT, false);
        graphics.drawString(font, Component.translatable(menu.computersAllowed()
                ? "logisticspipes.security.computers_except" : "logisticspipes.security.computers_only"), 8, 70, TEXT, false);
        List<Integer> computers = menu.computers();
        for (int index = 0; index < computers.size(); index++) {
            graphics.drawString(font, String.valueOf(computers.get(index)), 8 + index % COMPUTER_COLUMNS * 54,
                    COMPUTERS_TOP + index / COMPUTER_COLUMNS * 12, ID, false);
        }
        graphics.drawString(font, Component.translatable("logisticspipes.security.computer_id"), 8, 130, TEXT, false);
    }

    private void station(GuiGraphics graphics) {
        graphics.drawString(font, title, 8, 30, TEXT, false);
        // the id is too long for the window at full size
        graphics.pose().pushPose();
        graphics.pose().translate(8, 42, 0);
        graphics.pose().scale(0.75f, 0.75f, 1);
        graphics.drawString(font, menu.stationId().toString(), 0, 0, ID, false);
        graphics.pose().popPose();
        graphics.drawString(font, Component.translatable("logisticspipes.security.cards"), 8, 95, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    private void players(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("logisticspipes.gui.tab.players"), 8, 30, TEXT, false);
        String page = (menu.page() + 1) + "/" + menu.pages();
        graphics.drawString(font, page, 139 - font.width(page) / 2, 29, TEXT, false);
        int selected = menu.selectedSlot();
        ItemStack head = selected < 0 ? ItemStack.EMPTY : menu.getSlot(SecurityStationMenu.FIRST_PLAYER_SLOT + selected).getItem();
        Component who = head.isEmpty() ? Component.translatable("logisticspipes.security.select_player") : head.getHoverName();
        graphics.drawString(font, font.plainSubstrByWidth(who.getString(), 160), 8, 64, TEXT, false);
        graphics.drawString(font, Component.translatable("logisticspipes.security.add_player"), 8, 156, TEXT, false);
        List<String> suggestions = suggestions();
        List<String> shown = shown(suggestions);
        for (int line = 0; line < shown.size(); line++) {
            graphics.drawString(font, shown.get(line), 8, SUGGESTIONS_TOP + line * SUGGESTION_HEIGHT, TEXT, false);
        }
        if (shown.size() < suggestions.size()) {
            graphics.drawString(font, "...", 8, SUGGESTIONS_TOP + shown.size() * SUGGESTION_HEIGHT, TEXT, false);
        }
    }
}
