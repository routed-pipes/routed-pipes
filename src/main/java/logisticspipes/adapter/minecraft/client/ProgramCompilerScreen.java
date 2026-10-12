package logisticspipes.adapter.minecraft.client;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import logisticspipes.adapter.minecraft.ProgrammerItem;
import logisticspipes.adapter.minecraft.menu.ProgramCompilerMenu;
import logisticspipes.domain.program.CompilerKnowledge;
import logisticspipes.domain.program.CompilerTask;
import logisticspipes.domain.program.Program;
import logisticspipes.domain.program.ProgramCatalog;
import logisticspipes.domain.program.ProgramCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

// the locked categories on the left, the programs of the unlocked ones on the right, or the task that is running
public class ProgramCompilerScreen extends AbstractContainerScreen<ProgramCompilerMenu> {

    private static final ResourceLocation DISK_SLOT = ResourceLocation.fromNamespaceAndPath("logisticspipes", "textures/gui/slot_disk.png");
    private static final ResourceLocation PROGRAMMER_SLOT =
            ResourceLocation.fromNamespaceAndPath("logisticspipes", "textures/gui/slot_programmer.png");
    private static final int ROWS = 5;
    private static final int ROW_HEIGHT = 10;
    private static final int LIST_TOP = 28;
    private static final int LIST_BOTTOM = LIST_TOP + ROWS * ROW_HEIGHT;
    private static final int LEFT = 8;
    private static final int MIDDLE = 78;
    private static final int RIGHT = 168;
    private static final int BUTTON_TOP = 82;
    private static final int TEXT = 0x404040;
    private static final int LIST_BACKGROUND = 0xFF373737;
    private static final int SELECTED = 0xFF5A5A8A;
    private static final int LOCKED = 0xFFFFFF;
    private static final int COMPILED = 0xAAFFAA;
    private static final int NOT_COMPILED = 0xFFAAAA;
    private static final int WARNING = 0xFF5555;

    private EditBox search;
    private Button categoryUp;
    private Button categoryDown;
    private Button unlock;
    private Button programUp;
    private Button programDown;
    private Button run;
    private ProgramCategory selectedCategory;
    private Program selectedProgram;
    private int categoryScroll;
    private int programScroll;

    public ProgramCompilerScreen(ProgramCompilerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = ProgramCompilerMenu.INVENTORY_TOP + 82;
    }

    @Override
    protected void init() {
        super.init();
        categoryUp = button("/\\", LEFT, 14, () -> categoryScroll--);
        categoryDown = button("\\/", LEFT + 15, 14, () -> categoryScroll++);
        unlock = button("", LEFT + 30, 38, this::unlock);
        unlock.setMessage(Component.translatable("logisticspipes.compiler.unlock"));
        programUp = button("/\\", MIDDLE + 2, 14, () -> programScroll--);
        programDown = button("\\/", MIDDLE + 17, 14, () -> programScroll++);
        run = button("", MIDDLE + 32, 58, this::run);
        String typed = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + 30, topPos + 9, 116, 14, Component.translatable("logisticspipes.gui.search"));
        search.setHint(Component.translatable("logisticspipes.gui.search"));
        search.setMaxLength(32);
        search.setValue(typed);
        search.setResponder(text -> programScroll = 0);
        addRenderableWidget(search);
        update();
    }

    private Button button(String label, int x, int width, Runnable pressed) {
        return addRenderableWidget(Button.builder(Component.literal(label), button -> {
            pressed.run();
            update();
        }).bounds(leftPos + x, topPos + BUTTON_TOP, width, 12).build());
    }

    private void unlock() {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ProgramCompilerMenu.unlockButton(selectedCategory));
    }

    private void run() {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, compiled(selectedProgram)
                ? ProgramCompilerMenu.flashButton(selectedProgram) : ProgramCompilerMenu.compileButton(selectedProgram));
    }

    // the categories the disk does not have yet, none without a disk
    private List<ProgramCategory> locked() {
        Optional<CompilerKnowledge> disk = menu.disk();
        return disk.isEmpty() ? List.of()
                : Arrays.stream(ProgramCategory.values()).filter(category -> !disk.get().unlocked(category)).toList();
    }

    private List<Program> unlockedPrograms() {
        Optional<CompilerKnowledge> disk = menu.disk();
        return disk.isEmpty() ? List.of()
                : ProgramCatalog.all().stream().filter(program -> disk.get().unlocked(ProgramCatalog.categoryOf(program))).toList();
    }

    // what the program list shows: the unlocked programs the search lets through
    private List<Program> programs() {
        String wanted = search.getValue().strip().toLowerCase(Locale.ROOT);
        return unlockedPrograms().stream().filter(program -> name(program).toLowerCase(Locale.ROOT).contains(wanted)).toList();
    }

    private boolean compiled(Program program) {
        return menu.disk().filter(disk -> disk.compiled(program)).isPresent();
    }

    private static String name(Program program) {
        return ProgrammerItem.itemOf(program).getDescription().getString();
    }

    private static Component name(ProgramCategory category) {
        return Component.translatable("logisticspipes.compiler.category." + category.name().toLowerCase(Locale.ROOT));
    }

    // with every category unlocked the programs get the whole width
    private boolean wide() {
        return locked().isEmpty() && !unlockedPrograms().isEmpty();
    }

    private int programsLeft() {
        return wide() ? LEFT : MIDDLE + 2;
    }

    // shows the widgets that fit what is going on and keeps selections and scrolling inside the lists
    private void update() {
        boolean idle = menu.task().isEmpty();
        List<ProgramCategory> locked = locked();
        List<Program> programs = programs();
        if (!locked.contains(selectedCategory)) {
            selectedCategory = null;
        }
        if (!programs.contains(selectedProgram)) {
            selectedProgram = null;
        }
        categoryScroll = Math.max(0, Math.min(categoryScroll, locked.size() - ROWS));
        programScroll = Math.max(0, Math.min(programScroll, programs.size() - ROWS));
        boolean categories = idle && !wide();
        categoryUp.visible = categories;
        categoryDown.visible = categories;
        unlock.visible = categories;
        unlock.active = selectedCategory != null;
        programUp.visible = idle;
        programDown.visible = idle;
        run.visible = idle;
        boolean flash = selectedProgram != null && compiled(selectedProgram);
        run.setMessage(Component.translatable(flash ? "logisticspipes.compiler.flash" : "logisticspipes.compiler.compile"));
        run.active = selectedProgram != null && (!flash || menu.holdsProgrammer());
        search.visible = idle;
        if (!idle) {
            search.setFocused(false);
        }
    }

    @Override
    protected void containerTick() {
        update();
    }

    private static boolean inList(double x, double y, int left, int right) {
        return x >= left && x < right && y >= LIST_TOP && y < LIST_BOTTOM;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        int row = (int) ((y - LIST_TOP) / ROW_HEIGHT);
        if (menu.task().isEmpty() && !wide() && inList(x, y, LEFT, MIDDLE - 2)) {
            List<ProgramCategory> locked = locked();
            selectedCategory = categoryScroll + row < locked.size() ? locked.get(categoryScroll + row) : null;
        } else if (menu.task().isEmpty() && inList(x, y, programsLeft(), RIGHT)) {
            List<Program> programs = programs();
            selectedProgram = programScroll + row < programs.size() ? programs.get(programScroll + row) : null;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        search.setFocused(false);
        update();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        int rows = scrollY > 0 ? -1 : 1;
        if (!wide() && inList(x, y, LEFT, MIDDLE - 2)) {
            categoryScroll += rows;
        } else if (inList(x, y, programsLeft(), RIGHT)) {
            programScroll += rows;
        } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        update();
        return true;
    }

    // typing in the search must not trigger hotkeys like closing the screen with the inventory key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
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
        slotBackground(graphics, DISK_SLOT, menu.getSlot(ProgramCompilerMenu.DISK_SLOT));
        slotBackground(graphics, PROGRAMMER_SLOT, menu.getSlot(ProgramCompilerMenu.PROGRAMMER_SLOT));
    }

    // the two slots show what goes into them
    private void slotBackground(GuiGraphics graphics, ResourceLocation texture, Slot slot) {
        graphics.blit(texture, leftPos + slot.x - 1, topPos + slot.y - 1, 0, 0, 18, 18, 18, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<CompilerTask> task = menu.task();
        if (task.isPresent()) {
            running(graphics, task.get());
            return;
        }
        if (!wide()) {
            List<ProgramCategory> locked = locked();
            graphics.fill(LEFT, LIST_TOP, MIDDLE - 2, LIST_BOTTOM, LIST_BACKGROUND);
            for (int row = 0; row < ROWS && categoryScroll + row < locked.size(); row++) {
                ProgramCategory category = locked.get(categoryScroll + row);
                row(graphics, LEFT, MIDDLE - 2, row, name(category).getString(), LOCKED, category == selectedCategory);
            }
        }
        List<Program> programs = programs();
        graphics.fill(programsLeft(), LIST_TOP, RIGHT, LIST_BOTTOM, LIST_BACKGROUND);
        for (int row = 0; row < ROWS && programScroll + row < programs.size(); row++) {
            Program program = programs.get(programScroll + row);
            row(graphics, programsLeft(), RIGHT, row, name(program), compiled(program) ? COMPILED : NOT_COMPILED,
                    program.equals(selectedProgram));
        }
    }

    private void row(GuiGraphics graphics, int left, int right, int row, String text, int colour, boolean selected) {
        int top = LIST_TOP + row * ROW_HEIGHT;
        if (selected) {
            graphics.fill(left, top, right, top + ROW_HEIGHT, SELECTED);
        }
        graphics.drawString(font, font.plainSubstrByWidth(text, right - left - 4), left + 2, top + 1, colour, false);
    }

    private void running(GuiGraphics graphics, CompilerTask task) {
        if (!menu.powered()) {
            centred(graphics, Component.translatable("logisticspipes.compiler.no_power"), 8);
            centred(graphics, Component.translatable("logisticspipes.compiler.connect"), 18);
        }
        graphics.drawString(font, Component.translatable("logisticspipes.compiler.processing"), LEFT + 2, 39, TEXT, false);
        graphics.fill(LEFT, 50, RIGHT, 66, 0xFF000000);
        graphics.fill(LEFT + 1, 51, RIGHT - 1, 65, 0xFFFFFFFF);
        graphics.fill(LEFT + 1, 51, LEFT + 1 + (int) ((RIGHT - LEFT - 2) * menu.progress()), 65, 0xFF00AA00);
        String name = task.program() == null ? name(task.category()).getString() : name(task.program());
        graphics.drawString(font, font.plainSubstrByWidth(name, RIGHT - LEFT - 4), LEFT + 2, 70, TEXT, false);
    }

    private void centred(GuiGraphics graphics, Component text, int y) {
        graphics.drawString(font, text, (imageWidth - font.width(text)) / 2, y, WARNING, false);
    }
}
