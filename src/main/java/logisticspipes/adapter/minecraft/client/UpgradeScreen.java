package logisticspipes.adapter.minecraft.client;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import logisticspipes.adapter.minecraft.SecurityCardItem;
import logisticspipes.adapter.minecraft.menu.UpgradeMenu;
import logisticspipes.domain.statistics.PipeStatistics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// the pipe controller: statistics, upgrades, security card and open orders of one pipe on tabs,
// and a last tab that lists every routed pipe of its network
public class UpgradeScreen extends AbstractContainerScreen<UpgradeMenu> {

    private static final int TEXT = 0x404040;
    private static final int PIPE_NAME = 0x83601C;
    private static final int DENIED = 0xAA0000;
    private static final int AUTHORIZED = 0x00802A;
    private static final int ID = 0x2020C0;
    private static final int SESSION_X = 95;
    private static final int LIFETIME_X = 143;
    private static final int TEXT_WIDTH = 138;

    private final List<Tabs.Tab> tabs = List.of(
            Tabs.Tab.texture("stats", Component.translatable("routedpipes.gui.tab.statistics")),
            Tabs.Tab.item(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("routedpipes", "upgrade_sneaky")),
                    Component.translatable("routedpipes.gui.upgrades")),
            Tabs.Tab.texture("lock", Component.translatable("routedpipes.gui.tab.security")),
            Tabs.Tab.texture("lines", Component.translatable("routedpipes.gui.tab.tasks")),
            Tabs.Tab.item(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("routedpipes", "basic_logistics_pipe")),
                    Component.translatable("routedpipes.gui.tab.network")));
    private Button previousPage;
    private Button nextPage;

    public UpgradeScreen(UpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = UpgradeMenu.INVENTORY_TOP + 82;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> press(UpgradeMenu.BUTTON_PREVIOUS_PAGE))
                .bounds(leftPos + 110, topPos + 26, 12, 12).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> press(UpgradeMenu.BUTTON_NEXT_PAGE))
                .bounds(leftPos + 156, topPos + 26, 12, 12).build());
        showPageButtons();
    }

    private void showPageButtons() {
        boolean paged = menu.tab() == UpgradeMenu.Tab.TASKS || menu.tab() == UpgradeMenu.Tab.NETWORK;
        previousPage.visible = paged;
        nextPage.visible = paged;
    }

    @Override
    protected void containerTick() {
        showPageButtons();
    }

    // the menu is told on this side as well, the slots of the tab are there at once that way
    private void press(int button) {
        menu.clickMenuButton(minecraft.player, button);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = Tabs.at(leftPos, topPos, tabs.size(), mouseX, mouseY);
        if (tab < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        press(tab);
        showPageButtons();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int hovered = Tabs.at(leftPos, topPos, tabs.size(), mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderTooltip(font, tabs.get(hovered).name(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        Tabs.draw(graphics, leftPos, topPos, tabs, menu.tab().ordinal());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        switch (menu.tab()) {
            case STATISTICS -> statistics(graphics);
            case UPGRADES -> upgrades(graphics);
            case SECURITY -> security(graphics);
            case TASKS -> tasks(graphics);
            case NETWORK -> network(graphics);
        }
    }

    private void centred(GuiGraphics graphics, String text, int x, int y, int colour) {
        graphics.drawString(font, text, x - font.width(text) / 2, y, colour, false);
    }

    // with spaces between the thousands
    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private void statistics(GuiGraphics graphics) {
        centred(graphics, font.plainSubstrByWidth(title.getString(), 160), imageWidth / 2, 30, PIPE_NAME);
        centred(graphics, Component.translatable("routedpipes.gui.statistics.session").getString(), SESSION_X, 44, TEXT);
        centred(graphics, Component.translatable("routedpipes.gui.statistics.lifetime").getString(), LIFETIME_X, 44, TEXT);
        PipeStatistics.Kind[] kinds = PipeStatistics.Kind.values();
        for (int row = 0; row < kinds.length; row++) {
            int y = 58 + row * 14;
            String name = kinds[row].name().toLowerCase(Locale.ROOT);
            graphics.drawString(font, Component.translatable("routedpipes.gui.statistics." + name), 8, y, TEXT, false);
            centred(graphics, number(menu.session(kinds[row])), SESSION_X, y, TEXT);
            centred(graphics, number(menu.lifetime(kinds[row])), LIFETIME_X, y, TEXT);
        }
        graphics.drawString(font, Component.translatable("routedpipes.gui.statistics.routing_table",
                number(menu.routingTableSize())), 8, 106, TEXT, false);
    }

    private void upgrades(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("routedpipes.gui.upgrades"), 8, 30, TEXT, false);
        if (menu.sneakyRow()) {
            graphics.drawString(font, Component.translatable("routedpipes.gui.sneaky_upgrades"), 8, UpgradeMenu.SNEAKY_ROW_TOP - 12,
                    TEXT, false);
        }
        if (!menu.mayUpgrade()) {
            graphics.drawString(font, Component.translatable("routedpipes.security.denied"), 8, 66, DENIED, false);
        }
    }

    private void security(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("routedpipes.gui.tab.security"), 8, 30, TEXT, false);
        Optional<UUID> station = SecurityCardItem.stationOf(menu.getSlot(UpgradeMenu.SECURITY_SLOT).getItem());
        if (station.isEmpty()) {
            return;
        }
        graphics.drawString(font, Component.translatable("routedpipes.security.id", ""), 8, 66, TEXT, false);
        // the id is too long for the window at full size
        graphics.pose().pushPose();
        graphics.pose().translate(8, 78, 0);
        graphics.pose().scale(0.75f, 0.75f, 1);
        graphics.drawString(font, station.get().toString(), 0, 0, ID, false);
        graphics.pose().popPose();
        graphics.drawString(font, Component.translatable(menu.cardAuthorized()
                ? "routedpipes.security.authorized" : "routedpipes.security.unauthorized"), 8, 92,
                menu.cardAuthorized() ? AUTHORIZED : DENIED, false);
    }

    private void tasks(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("routedpipes.gui.tab.tasks"), 8, 30, TEXT, false);
        centred(graphics, (menu.page() + 1) + "/" + menu.pages(), 139, 28, TEXT);
        boolean any = false;
        for (int row = 0; row < UpgradeMenu.TASK_ROWS; row++) {
            ItemStack stack = menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT + row).getItem();
            Optional<UpgradeMenu.Task> task = UpgradeMenu.taskOf(stack);
            if (task.isEmpty()) {
                continue;
            }
            any = true;
            int y = UpgradeMenu.TASKS_TOP + row * UpgradeMenu.TASK_ROW_HEIGHT;
            String entry = Component.translatable("routedpipes.gui.tasks.entry", task.get().number(), task.get().count(),
                    stack.getHoverName()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(entry, TEXT_WIDTH), 30, y, TEXT, false);
            Component target = task.get().target()
                    .<Component>map(pos -> Component.translatable("routedpipes.gui.tasks.target", pos.getX(), pos.getY(), pos.getZ()))
                    .orElse(Component.translatable("routedpipes.gui.tasks.no_target"));
            graphics.drawString(font, font.plainSubstrByWidth(target.getString(), TEXT_WIDTH), 30, y + 9, TEXT, false);
        }
        if (!any) {
            graphics.drawString(font, Component.translatable("routedpipes.gui.tasks.none"), 30, UpgradeMenu.TASKS_TOP + 4, TEXT, false);
        }
    }

    // the energy of the network on top, then a pipe per row: what it is and where, and below that what it moved
    private void network(GuiGraphics graphics) {
        String energy = Component.translatable("routedpipes.gui.network.energy", number(menu.networkEnergy())).getString();
        graphics.drawString(font, font.plainSubstrByWidth(energy, 100), 8, 30, TEXT, false);
        centred(graphics, (menu.page() + 1) + "/" + menu.pages(), 139, 28, TEXT);
        for (int row = 0; row < UpgradeMenu.PIPE_ROWS; row++) {
            ItemStack stack = menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT + row).getItem();
            Optional<UpgradeMenu.PipeRow> pipe = UpgradeMenu.pipeRowOf(stack);
            if (pipe.isEmpty()) {
                continue;
            }
            int y = UpgradeMenu.TASKS_TOP + row * UpgradeMenu.PIPE_ROW_HEIGHT;
            BlockPos pos = pipe.get().pos();
            String where = Component.translatable("routedpipes.gui.network.pipe", stack.getHoverName(), pos.getX(), pos.getY(),
                    pos.getZ()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(where, TEXT_WIDTH), 30, y, PIPE_NAME, false);
            String counts = Component.translatable("routedpipes.gui.network.counts", number(pipe.get().sent()),
                    number(pipe.get().received()), number(pipe.get().relayed()), pipe.get().orders()).getString();
            // four numbers in a row need smaller letters
            graphics.pose().pushPose();
            graphics.pose().translate(30, y + 10, 0);
            graphics.pose().scale(0.75f, 0.75f, 1);
            graphics.drawString(font, font.plainSubstrByWidth(counts, TEXT_WIDTH * 4 / 3), 0, 0, TEXT, false);
            graphics.pose().popPose();
        }
    }
}
