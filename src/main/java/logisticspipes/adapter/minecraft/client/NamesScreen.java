package logisticspipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.adapter.minecraft.ItemTraits;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.menu.MarkerMenu;
import logisticspipes.adapter.minecraft.menu.NamesMenu;
import logisticspipes.domain.logistics.ModuleKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

// two lists of names: what the item in the slot offers, click to add, and what the module takes, click to remove
public class NamesScreen extends AbstractContainerScreen<NamesMenu> implements MarkerScreen {

    private static final int TEXT = 0x404040;
    private static final int HOVER = 0xFF2040A0;
    private static final int ROW = 10;
    private static final int LIST_X = 30;
    private static final int LIST_WIDTH = 118;
    private static final int OFFERED_Y = 18;
    private static final int OFFERED_ROWS = 4;
    private static final int TAKEN_X = 8;
    private static final int TAKEN_WIDTH = 160;
    private static final int TAKEN_Y = 72;

    private int scroll;

    public NamesScreen(NamesMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 256;
        inventoryLabelY = 163;
    }

    @Override
    protected void init() {
        super.init();
        // creative tabs know their items only once they were built, like when the creative inventory opens
        CreativeModeTabs.tryRebuildTabContents(minecraft.player.connection.enabledFeatures(),
                minecraft.options.operatorItemsTab().get() && minecraft.player.canUseGameMasterBlocks(),
                minecraft.level.registryAccess());
    }

    private ModuleKind kind() {
        return menu.module().getItem() instanceof ModuleItem module ? module.kind() : null;
    }

    // names of the item in the slot that are not taken yet
    private List<String> offered() {
        ItemStack item = menu.item();
        ModuleKind kind = kind();
        if (item.isEmpty() || kind == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(switch (kind) {
            case MOD_ITEM_SINK -> List.of(ItemTraits.namespace(item.getItem()));
            case TAG_ITEM_SINK -> ItemTraits.tags(item);
            case CREATIVE_TAB_ITEM_SINK -> ItemTraits.groups(item);
            default -> List.<String>of();
        });
        names.removeAll(menu.names());
        return names;
    }

    private static int rowAt(double mouse, int start, int rows) {
        int row = (int) Math.floor((mouse - start) / ROW);
        return mouse >= start && row < rows ? row : -1;
    }

    private int offeredRowAt(double mouseX, double mouseY) {
        boolean inside = mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_WIDTH;
        return inside ? rowAt(mouseY, topPos + OFFERED_Y, OFFERED_ROWS) : -1;
    }

    private int takenRowAt(double mouseX, double mouseY) {
        boolean inside = mouseX >= leftPos + TAKEN_X && mouseX < leftPos + TAKEN_X + TAKEN_WIDTH;
        return inside ? rowAt(mouseY, topPos + TAKEN_Y, NamesMenu.MAX_NAMES) : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<String> offered = offered();
        int offeredRow = offeredRowAt(mouseX, mouseY) + scroll;
        if (offeredRowAt(mouseX, mouseY) >= 0 && offeredRow < offered.size()) {
            ClientMenuLink.setText(menu.containerId, NamesMenu.FIELD_ADD, offered.get(offeredRow));
            return true;
        }
        List<String> taken = menu.names();
        int takenRow = takenRowAt(mouseX, mouseY);
        if (takenRow >= 0 && takenRow < taken.size()) {
            ClientMenuLink.setText(menu.containerId, NamesMenu.FIELD_REMOVE, taken.get(takenRow));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, offered().size() - OFFERED_ROWS);
        scroll = Math.max(0, Math.min(most, scroll - (int) Math.signum(scrollY)));
        return true;
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

    private void drawRows(GuiGraphics graphics, List<String> names, int from, int rows, int x, int y, int width, int hovered) {
        for (int row = 0; row < rows && from + row < names.size(); row++) {
            String name = font.plainSubstrByWidth(names.get(from + row), width);
            graphics.drawString(font, name, x, y + row * ROW, row == hovered ? HOVER : TEXT, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        List<String> offered = offered();
        scroll = Math.max(0, Math.min(scroll, offered.size() - OFFERED_ROWS));
        if (menu.item().isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("routedpipes.gui.names.hint"), LIST_X, OFFERED_Y, LIST_WIDTH, TEXT);
        }
        drawRows(graphics, offered, scroll, OFFERED_ROWS, LIST_X, OFFERED_Y, LIST_WIDTH, offeredRowAt(mouseX, mouseY));
        graphics.drawString(font, Component.translatable("routedpipes.gui.names.taken"), TAKEN_X, TAKEN_Y - 12, TEXT, false);
        drawRows(graphics, menu.names(), 0, NamesMenu.MAX_NAMES, TAKEN_X, TAKEN_Y, TAKEN_WIDTH, takenRowAt(mouseX, mouseY));
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
