package logisticspipes.adapter.minecraft.client;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// the list of satellites within reach that opens over the settings of a crafter
// a click on a name picks that satellite, the first line picks none, a click anywhere else closes the list
final class SatellitePicker {

    private static final int WIDTH = 120;
    private static final int LINE = 10;
    // the line for no satellite and this many names
    private static final int ROWS = 8;
    private static final int HEIGHT = (ROWS + 1) * LINE + 4;
    private static final int BORDER = 0xFF000000;
    private static final int BACKGROUND = 0xFF2B2B2B;
    private static final int CHOSEN = 0xFF3F6F3F;
    private static final int HOVERED = 0xFF4A4A6A;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_DIM = 0xA0A0A0;
    // above the items in the slots
    private static final int DEPTH = 300;

    private int field = -1;
    private int left;
    private int top;
    private int scroll;

    boolean isOpen() {
        return field >= 0;
    }

    // the text field of the menu the pick goes to
    int field() {
        return field;
    }

    void open(int field, int left, int top) {
        this.field = field;
        this.left = left;
        this.top = top;
        scroll = 0;
    }

    void close() {
        field = -1;
    }

    private int lineAt(double mouseX, double mouseY) {
        boolean inside = mouseX >= left && mouseX < left + WIDTH && mouseY >= top + 2 && mouseY < top + HEIGHT - 2;
        return inside ? ((int) mouseY - top - 2) / LINE : -1;
    }

    // the name on the line at that point, an empty name on the line for no satellite, nothing where there is no line
    Optional<String> nameAt(double mouseX, double mouseY, List<String> offered) {
        int line = lineAt(mouseX, mouseY);
        if (line == 0) {
            return Optional.of("");
        }
        int index = scroll + line - 1;
        return line > 0 && index < offered.size() ? Optional.of(offered.get(index)) : Optional.empty();
    }

    void scroll(double amount, List<String> offered) {
        scroll = Math.max(0, Math.min(Math.max(0, offered.size() - ROWS), scroll - (int) Math.signum(amount)));
    }

    private void line(GuiGraphics graphics, Font font, int line, String text, boolean chosen, boolean hovered, int colour) {
        int y = top + 2 + line * LINE;
        if (chosen || hovered) {
            graphics.fill(left + 1, y, left + WIDTH - 1, y + LINE, chosen ? CHOSEN : HOVERED);
        }
        graphics.drawString(font, font.plainSubstrByWidth(text, WIDTH - 6), left + 3, y + 1, colour, false);
    }

    void draw(GuiGraphics graphics, Font font, List<String> offered, String chosen, int mouseX, int mouseY) {
        scroll = Math.max(0, Math.min(scroll, offered.size() - ROWS));
        int hovered = lineAt(mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, DEPTH);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, BORDER);
        graphics.fill(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, BACKGROUND);
        line(graphics, font, 0, Component.translatable("routedpipes.gui.satellite.off").getString(), chosen.isEmpty(), hovered == 0, TEXT);
        if (offered.isEmpty()) {
            line(graphics, font, 1, Component.translatable("routedpipes.gui.satellite.none_in_reach").getString(), false, false, TEXT_DIM);
        }
        for (int row = 0; row < ROWS && scroll + row < offered.size(); row++) {
            String name = offered.get(scroll + row);
            line(graphics, font, row + 1, name, name.equals(chosen), hovered == row + 1, TEXT);
        }
        graphics.pose().popPose();
    }
}
