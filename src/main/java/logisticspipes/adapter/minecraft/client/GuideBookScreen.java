package logisticspipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import logisticspipes.adapter.minecraft.GuideBookItem;
import logisticspipes.domain.guide.GuideChapter;
import logisticspipes.domain.guide.GuidePages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// the guide book: a list of chapters and the pages of each, drawn on the vanilla book
// the chapters about things of the mod are made from what is registered and its descriptions
public class GuideBookScreen extends Screen {

    private static final int BOOK_WIDTH = 192;
    private static final int BOOK_HEIGHT = 192;
    private static final int TOP = 2;
    private static final int TEXT_LEFT = 36;
    private static final int TEXT_TOP = 32;
    private static final int TEXT_WIDTH = 114;
    private static final int LINE_HEIGHT = 9;
    private static final int LINES_PER_PAGE = 14;
    // the name of a thing takes two lines, with its picture next to it
    private static final int HEADER_LINES = 2;
    private static final int ICON_WIDTH = 20;
    private static final int INK = 0xFF000000;
    private static final int LINK = 0xFF2040A0;

    // one paragraph, with the thing it is about if there is one
    private record Entry(ItemStack icon, List<FormattedCharSequence> lines) {
    }

    private record Chapter(Component title, List<Entry> entries, List<List<GuidePages.Piece>> pages) {
    }

    private final List<Chapter> chapters = new ArrayList<>();
    // -1 is the list of chapters
    private int chapter = -1;
    private int page;
    private PageButton next;
    private PageButton previous;
    private Button contents;

    public GuideBookScreen() {
        super(Component.translatable("item.logisticspipes.guide_book"));
    }

    private int left() {
        return (width - BOOK_WIDTH) / 2;
    }

    @Override
    protected void init() {
        if (chapters.isEmpty()) {
            buildChapters();
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 + 2, TOP + BOOK_HEIGHT, 98, 20).build());
        contents = addRenderableWidget(Button.builder(Component.translatable("logisticspipes.guide.contents"), button -> {
            chapter = -1;
            page = 0;
            updateButtons();
        }).bounds(width / 2 - 100, TOP + BOOK_HEIGHT, 98, 20).build());
        next = addRenderableWidget(new PageButton(left() + 116, TOP + 159, true, button -> turn(1), true));
        previous = addRenderableWidget(new PageButton(left() + 43, TOP + 159, false, button -> turn(-1), true));
        updateButtons();
    }

    private void buildChapters() {
        Map<GuideChapter, List<Item>> registered = GuideBookItem.entries(I18n::exists);
        for (GuideChapter kind : GuideChapter.values()) {
            List<Entry> entries = new ArrayList<>();
            if (kind == GuideChapter.GETTING_STARTED) {
                for (int paragraph = 1; I18n.exists(startKey(paragraph)); paragraph++) {
                    entries.add(new Entry(ItemStack.EMPTY, font.split(Component.translatable(startKey(paragraph)), TEXT_WIDTH)));
                }
            }
            for (Item item : registered.getOrDefault(kind, List.of())) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                // the name wraps beside the picture, a long one only shows its first line
                List<FormattedCharSequence> name = font.split(item.getDescription(), TEXT_WIDTH - ICON_WIDTH);
                lines.add(name.get(0));
                lines.add(FormattedCharSequence.EMPTY);
                lines.addAll(font.split(Component.translatable(GuideBookItem.descriptionKey(item)), TEXT_WIDTH));
                entries.add(new Entry(new ItemStack(item), lines));
            }
            if (entries.isEmpty()) {
                continue;
            }
            List<List<GuidePages.Piece>> pages = GuidePages.layout(entries.stream().map(entry -> entry.lines().size()).toList(),
                    LINES_PER_PAGE);
            chapters.add(new Chapter(Component.translatable("logisticspipes.guide.chapter." + kind.name().toLowerCase(Locale.ROOT)),
                    entries, pages));
        }
    }

    private static String startKey(int paragraph) {
        return "logisticspipes.guide.start." + paragraph;
    }

    private void turn(int by) {
        if (chapter < 0) {
            return;
        }
        page += by;
        // past the last page of a chapter is the next one, before the first the one before
        if (page >= chapters.get(chapter).pages().size()) {
            if (chapter + 1 < chapters.size()) {
                chapter++;
                page = 0;
            } else {
                page--;
            }
        } else if (page < 0) {
            if (chapter == 0) {
                chapter = -1;
                page = 0;
            } else {
                chapter--;
                page = chapters.get(chapter).pages().size() - 1;
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean last = chapter == chapters.size() - 1 && page == chapters.get(chapter).pages().size() - 1;
        next.visible = chapter >= 0 && !last;
        previous.visible = chapter >= 0;
        contents.active = chapter >= 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (chapter < 0 && button == 0) {
            int line = (int) Math.floor((mouseY - TOP - TEXT_TOP) / LINE_HEIGHT) - 1;
            int x = (int) mouseX - left() - TEXT_LEFT;
            if (line >= 0 && line < chapters.size() && x >= 0 && x < TEXT_WIDTH) {
                chapter = line;
                page = 0;
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(BookViewScreen.BOOK_LOCATION, left(), TOP, 0, 0, BOOK_WIDTH, BOOK_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left() + TEXT_LEFT;
        int y = TOP + TEXT_TOP;
        if (chapter < 0) {
            graphics.drawString(font, title, x, y, INK, false);
            for (int index = 0; index < chapters.size(); index++) {
                graphics.drawString(font, chapters.get(index).title(), x + 4, y + (index + 1) * LINE_HEIGHT, LINK, false);
            }
            return;
        }
        Chapter shown = chapters.get(chapter);
        graphics.drawString(font, shown.title(), x, TOP + 16, INK, false);
        Component number = Component.translatable("logisticspipes.guide.page", page + 1, shown.pages().size());
        graphics.drawString(font, number, x + TEXT_WIDTH - font.width(number), TOP + 16, INK, false);
        int line = 0;
        for (GuidePages.Piece piece : shown.pages().get(page)) {
            Entry entry = shown.entries().get(piece.entry());
            boolean header = !entry.icon().isEmpty() && piece.from() == 0;
            if (header) {
                graphics.renderItem(entry.icon(), x, y + line * LINE_HEIGHT);
            }
            for (int index = piece.from(); index < piece.to(); index++) {
                int indent = header && index < HEADER_LINES ? ICON_WIDTH : 0;
                // the name sits halfway down beside the picture
                int lift = header && index == 0 ? 4 : 0;
                graphics.drawString(font, entry.lines().get(index), x + indent, y + line * LINE_HEIGHT + lift, INK, false);
                line++;
            }
            line++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
