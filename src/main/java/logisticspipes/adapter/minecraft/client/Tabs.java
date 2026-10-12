package logisticspipes.adapter.minecraft.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

// a row of tabs along the top edge of a window, each with an icon
final class Tabs {

    static final int WIDTH = 25;
    static final int HEIGHT = 20;
    private static final int LEFT = 6;
    private static final int TOP = 5;
    private static final int FRAME = 0xFF555555;
    private static final int SELECTED = 0xFFC6C6C6;
    private static final int OTHER = 0xFF8B8B8B;

    // an icon is either a 16 pixel texture or an item
    record Tab(ResourceLocation texture, ItemStack item, Component name) {

        static Tab texture(String name, Component label) {
            return new Tab(ResourceLocation.fromNamespaceAndPath("logisticspipes", "textures/gui/" + name + ".png"),
                    ItemStack.EMPTY, label);
        }

        static Tab item(ItemLike item, Component label) {
            return new Tab(null, new ItemStack(item), label);
        }
    }

    private Tabs() {
    }

    static void draw(GuiGraphics graphics, int left, int top, List<Tab> tabs, int selected) {
        for (int index = 0; index < tabs.size(); index++) {
            int x = left + LEFT + index * WIDTH;
            int y = top + TOP;
            graphics.fill(x, y, x + WIDTH - 1, y + HEIGHT, FRAME);
            // the selected tab is open at the bottom and runs into the window
            graphics.fill(x + 1, y + 1, x + WIDTH - 2, y + HEIGHT - (index == selected ? 0 : 1), index == selected ? SELECTED : OTHER);
            Tab tab = tabs.get(index);
            if (tab.texture() != null) {
                graphics.blit(tab.texture(), x + 4, y + 2, 0, 0, 16, 16, 16, 16);
            } else {
                graphics.renderItem(tab.item(), x + 4, y + 2);
            }
        }
    }

    // the tab under the mouse, -1 for none
    static int at(int left, int top, int count, double mouseX, double mouseY) {
        double x = mouseX - left - LEFT;
        double y = mouseY - top - TOP;
        if (x < 0 || y < 0 || y >= HEIGHT || x >= count * WIDTH) {
            return -1;
        }
        return (int) x / WIDTH;
    }
}
