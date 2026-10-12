package logisticspipes.adapter.minecraft.client;

import logisticspipes.domain.item.FuzzyFlags;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// the list of fuzzy flags that opens under a marker slot when the mouse rests on it, a click on a line switches that flag
final class FuzzyPanel {

    private static final int WIDTH = 70;
    private static final int LINE = 10;
    private static final int HEIGHT = FuzzyFlags.COUNT * LINE + 4;
    // frames the mouse has to rest on a slot
    private static final int DELAY = 10;
    private static final int BORDER = 0xFF000000;
    private static final int BACKGROUND = 0xFF2B2B2B;
    private static final int ACTIVE = 0xFF3F6F3F;
    private static final int TEXT_ON = 0xFFFFFF;
    private static final int TEXT_OFF = 0xA0A0A0;
    // one colour per flag, the same on the list and in the corners of the slot
    private static final int[] COLOURS = {0xFF55FF55, 0xFFFF5555, 0xFF5599FF, 0xFFFFFF55};
    // above the items in the slots
    private static final int DEPTH = 300;

    private int open = -1;
    private int waiting = -1;
    private int frames;
    private int left;
    private int top;

    // the slot the list is open for, -1 while it is closed
    int open() {
        return open;
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + WIDTH && mouseY >= top && mouseY < top + HEIGHT;
    }

    // called every frame with the slot under the mouse and where it is on the display, -1 for no slot with flags
    void hover(int slot, int x, int y, double mouseX, double mouseY) {
        if (open >= 0 && (slot == open || inside(mouseX, mouseY))) {
            return;
        }
        open = -1;
        if (slot != waiting) {
            waiting = slot;
            frames = 0;
        }
        if (slot >= 0 && ++frames >= DELAY) {
            open = slot;
            left = x;
            top = y + 16;
        }
    }

    // the flag of the line at that point, -1 if there is none
    int flagAt(double mouseX, double mouseY) {
        if (open < 0 || !inside(mouseX, mouseY)) {
            return -1;
        }
        int line = ((int) mouseY - top - 2) / LINE;
        return mouseY - top < 2 || line >= FuzzyFlags.COUNT ? -1 : line;
    }

    void draw(GuiGraphics graphics, Font font, FuzzyFlags flags) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, DEPTH);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, BORDER);
        graphics.fill(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, BACKGROUND);
        for (int flag = 0; flag < FuzzyFlags.COUNT; flag++) {
            boolean on = (flags.bits() & 1 << flag) != 0;
            int y = top + 2 + flag * LINE;
            if (on) {
                graphics.fill(left + 1, y, left + WIDTH - 1, y + LINE, ACTIVE);
            }
            graphics.fill(left + 3, y + 3, left + 7, y + 7, COLOURS[flag]);
            graphics.drawString(font, Component.translatable("logisticspipes.gui.fuzzy." + flag), left + 10, y + 1, on ? TEXT_ON : TEXT_OFF, false);
        }
        graphics.pose().popPose();
    }

    // a dot in a corner of the slot for every flag that is set
    static void mark(GuiGraphics graphics, int x, int y, FuzzyFlags flags) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, DEPTH);
        for (int flag = 0; flag < FuzzyFlags.COUNT; flag++) {
            if ((flags.bits() & 1 << flag) != 0) {
                int cornerX = x + flag % 2 * 13;
                int cornerY = y + flag / 2 * 13;
                graphics.fill(cornerX, cornerY, cornerX + 3, cornerY + 3, COLOURS[flag]);
            }
        }
        graphics.pose().popPose();
    }
}
