package routedpipes.adapter.minecraft.client;

import java.util.List;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.menu.DiskMenu;
import routedpipes.adapter.minecraft.menu.MacroEditMenu;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.macro.Macro;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// the request screen with the macro that is being put together below it: a click on an entry takes it out again
public class MacroEditScreen extends RequestScreen<MacroEditMenu> {

    private static final int TEXT = 0x404040;
    private static final int CELL = 0xFF8B8B8B;
    private static final int CELLS = 9;
    private static final int ROW_X = 8;
    private static final int ROW_Y = 184;

    private EditBox name;
    private Button save;
    private boolean nameLoaded;
    private Macro macro = Macro.EMPTY;
    // the entry in the first cell
    private int first;

    public MacroEditScreen(MacroEditMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 244;
    }

    @Override
    protected void init() {
        super.init();
        String typed = name == null ? "" : name.getValue();
        name = new EditBox(font, leftPos + 8, topPos + 206, 132, 12, Component.translatable("routedpipes.gui.macro.name"));
        name.setHint(Component.translatable("routedpipes.gui.macro.name"));
        name.setMaxLength(DiskMenu.MAX_NAME);
        name.setValue(typed);
        name.setResponder(text -> ClientMenuLink.setText(menu.containerId, MacroEditMenu.TEXT_NAME, text));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> first -= CELLS)
                .bounds(leftPos + 144, topPos + 206, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> first += CELLS)
                .bounds(leftPos + 156, topPos + 206, 12, 12).build());
        save = addRenderableWidget(Button.builder(Component.translatable("routedpipes.gui.macro.save"),
                button -> press(MacroEditMenu.BUTTON_SAVE)).bounds(leftPos + 8, topPos + 222, 78, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("routedpipes.gui.disk.back"),
                button -> press(MacroEditMenu.BUTTON_BACK)).bounds(leftPos + 90, topPos + 222, 78, 16).build());
    }

    @Override
    protected Component requestLabel() {
        return Component.translatable("routedpipes.gui.macro.add");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack draft = menu.getSlot(MacroEditMenu.DRAFT_SLOT).getItem();
        macro = menu.macro();
        if (!nameLoaded && !draft.isEmpty()) {
            // the name of a macro that was opened to be changed
            nameLoaded = true;
            name.setValue(macro.name());
        }
        save.active = macro.named(name.getValue()).problem().isEmpty();
        int lastPage = Math.max(0, macro.entries().size() - 1) / CELLS * CELLS;
        first = Math.max(0, Math.min(first, lastPage));
    }

    // the place in the macro of the entry under the mouse, -1 if there is none
    private int entryAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - ROW_X;
        double y = mouseY - topPos - ROW_Y;
        if (x < 0 || y < 0 || y >= 16 || x >= CELLS * 18 || x % 18 >= 16) {
            return -1;
        }
        int index = first + (int) (x / 18);
        return index < macro.entries().size() ? index : -1;
    }

    private static ItemStack stackOf(ItemBatch entry) {
        return McItemType.stackOf(new ItemBatch(entry.type(), 1));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int entry = entryAt(mouseX, mouseY);
        if (entry >= 0) {
            press(MacroEditMenu.BUTTON_REMOVE + entry);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        List<ItemBatch> entries = macro.entries();
        for (int cell = 0; cell < CELLS; cell++) {
            int x = ROW_X + cell * 18;
            graphics.fill(x, ROW_Y, x + 16, ROW_Y + 16, CELL);
            if (first + cell < entries.size()) {
                ItemBatch entry = entries.get(first + cell);
                graphics.renderItem(stackOf(entry), x, ROW_Y);
                drawCount(graphics, x, ROW_Y, entry.count());
            }
        }
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("routedpipes.gui.macro.no_items"), ROW_X + 4, ROW_Y + 4, TEXT, false);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        int entry = entryAt(mouseX, mouseY);
        if (entry >= 0) {
            graphics.renderTooltip(font, stackOf(macro.entries().get(entry)), mouseX, mouseY);
        }
    }
}
