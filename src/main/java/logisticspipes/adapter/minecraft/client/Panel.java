package logisticspipes.adapter.minecraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

// vanilla looking window background drawn without a texture
final class Panel {

    private static final int FACE = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    private Panel() {
    }

    static void draw(GuiGraphics graphics, int left, int top, int width, int height, AbstractContainerMenu menu) {
        graphics.fill(left, top, left + width, top + height, 0xFF000000);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, LIGHT);
        graphics.fill(left + 3, top + 3, left + width - 1, top + height - 1, SHADOW);
        graphics.fill(left + 3, top + 3, left + width - 3, top + height - 3, FACE);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, LIGHT);
            graphics.fill(x, y, x + 17, y + 17, SLOT_SHADOW);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
        }
    }
}
