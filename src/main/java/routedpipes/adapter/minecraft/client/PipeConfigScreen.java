package routedpipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.MarkerMenu;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.minecraft.menu.SatelliteNames;
import routedpipes.domain.crafting.FluidSlots;
import routedpipes.domain.item.FuzzyFlags;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class PipeConfigScreen extends AbstractContainerScreen<PipeConfigMenu> implements MarkerScreen {

    private static final int TEXT = 0x404040;
    private static final int SATELLITE_TINT = 0xFF6F8FBF;
    private static final int SELECTED = 0xFFFFD040;
    private static final int PATTERN_NUMBER = 0xFFFF55;

    private Button defaultRoute;
    private Button mode;
    private Button exclude;
    private Button importCleanup;
    // the ingredient whose satellite is being set, only with the upgrade for that
    private int satelliteSlot;
    private Button previousSlot;
    private Button nextSlot;
    // shows the satellite of a crafter and opens the list to pick another
    private Button select;
    private final SatellitePicker picker = new SatellitePicker();
    private static final int SELECT_WIDTH = 76;
    // only a satellite pipe has one
    private SatelliteNameField nameField;
    // the fluid panel left of the window: the amount buttons and fluid satellite of every column, and the satellite of all
    private final List<List<Button>> fluidSteps = new ArrayList<>();
    private final List<Button> fluidSelects = new ArrayList<>();
    private Button fluidSelect;
    private static final int FLUID_PANEL_HEIGHT = 136;
    private static final int FLUID_STEPS_TOP = 48;
    private static final int FLUID_SELECT_TOP = 100;
    private static final int FLUID_SHARED_TOP = 118;
    // the marker whose inventory slot is being set, only with a pattern upgrade
    private int patternMarker;
    private Button limited;
    private List<Button> patternButtons = List.of();
    private final FuzzyPanel fuzzy = new FuzzyPanel();

    public PipeConfigScreen(PipeConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = PipeConfigMenu.inventoryTop(menu.kind()) + 82;
        inventoryLabelY = imageHeight - 94;
    }

    private boolean hasSatelliteControls() {
        return menu.kind() == PipeKind.CRAFTING || menu.kind() == PipeKind.SATELLITE;
    }

    // crafting pipes show the satellite under the output slot, satellite pipes in the middle
    private int satelliteX() {
        return menu.kind() == PipeKind.CRAFTING ? 132 : imageWidth / 2;
    }

    private int satelliteY() {
        return menu.kind() == PipeKind.CRAFTING ? 58 : 40;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind() == PipeKind.BASIC) {
            defaultRoute = addRenderableWidget(Button.builder(defaultRouteLabel(),
                    button -> press(PipeConfigMenu.BUTTON_DEFAULT_ROUTE)).bounds(leftPos + 92, topPos + 34, 76, 16).build());
        }
        if (menu.kind() == PipeKind.PROVIDER) {
            mode = addRenderableWidget(Button.builder(modeLabel(), button -> press(PipeConfigMenu.BUTTON_MODE))
                    .bounds(leftPos + 68, topPos + 22, 100, 16).build());
            exclude = addRenderableWidget(Button.builder(excludeLabel(), button -> press(PipeConfigMenu.BUTTON_EXCLUDE))
                    .bounds(leftPos + 68, topPos + 42, 100, 16).build());
        }
        if (menu.kind() == PipeKind.SUPPLIER) {
            mode = addRenderableWidget(Button.builder(modeLabel(), button -> press(PipeConfigMenu.BUTTON_MODE))
                    .bounds(leftPos + 120, topPos + 34, 48, 16).build());
            limited = addRenderableWidget(Button.builder(limitedLabel(), button -> press(PipeConfigMenu.BUTTON_LIMITED))
                    .bounds(leftPos + 120, topPos + 54, 48, 16).build());
            patternButtons = List.of(
                    addRenderableWidget(Button.builder(Component.literal("-"), button -> pressPatternSlot(false))
                            .bounds(leftPos + 8, topPos + 30, 12, 12).build()),
                    addRenderableWidget(Button.builder(Component.literal("+"), button -> pressPatternSlot(true))
                            .bounds(leftPos + 44, topPos + 30, 12, 12).build()),
                    addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPatternMarker(-1))
                            .bounds(leftPos + 8, topPos + 50, 12, 12).build()),
                    addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPatternMarker(1))
                            .bounds(leftPos + 44, topPos + 50, 12, 12).build()));
            showPatternControls();
        }
        if (menu.kind() == PipeKind.CRAFTING) {
            exclude = addRenderableWidget(Button.builder(excludeLabel(), button -> press(PipeConfigMenu.BUTTON_EXCLUDE))
                    .bounds(leftPos + 120, topPos + 95, 48, 16).build());
            importCleanup = addRenderableWidget(Button.builder(Component.translatable("routedpipes.gui.import"),
                    button -> press(PipeConfigMenu.BUTTON_IMPORT_CLEANUP)).bounds(leftPos + 120, topPos + 114, 48, 16).build());
            addRenderableWidget(Button.builder(Component.literal("<"), button -> press(PipeConfigMenu.BUTTON_PRIORITY_DOWN))
                    .bounds(leftPos + 96, topPos + 74, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> press(PipeConfigMenu.BUTTON_PRIORITY_UP))
                    .bounds(leftPos + 132, topPos + 74, 14, 14).build());
            addRenderableWidget(Button.builder(Component.translatable("routedpipes.gui.import"),
                    button -> press(PipeConfigMenu.BUTTON_IMPORT_RECIPE)).bounds(leftPos + 85, topPos + 25, 38, 14).build());
        }
        if (!hasSatelliteControls()) {
            return;
        }
        int x = leftPos + satelliteX();
        int y = topPos + satelliteY();
        if (menu.kind() == PipeKind.SATELLITE) {
            nameField = new SatelliteNameField(font, x, y - 3, menu.containerId);
            addRenderableWidget(nameField.box());
            addRenderableWidget(nameField.save());
            return;
        }
        previousSlot = addRenderableWidget(Button.builder(Component.literal("<"), button -> turnSatelliteSlot(-1))
                .bounds(x - 30, y + 12, 10, 10).build());
        nextSlot = addRenderableWidget(Button.builder(Component.literal(">"), button -> turnSatelliteSlot(1))
                .bounds(x + 20, y + 12, 10, 10).build());
        select = addRenderableWidget(Button.builder(selectLabel(), button -> picker.open(satelliteField(),
                leftPos + imageWidth - 126, y + 12)).bounds(x - SELECT_WIDTH / 2, y - 3, SELECT_WIDTH, 14).build());
        addFluidPanel();
    }

    private void addFluidPanel() {
        fluidSteps.clear();
        fluidSelects.clear();
        for (int slot = 0; slot < FluidSlots.MAX; slot++) {
            int left = leftPos + PipeConfigMenu.fluidColumnLeft(slot);
            List<Button> steps = new ArrayList<>();
            for (int step = 0; step < PipeConfigMenu.FLUID_STEPS.length; step++) {
                int id = PipeConfigMenu.BUTTON_FLUID_AMOUNT + slot * PipeConfigMenu.FLUID_STEPS.length + step;
                boolean adds = PipeConfigMenu.FLUID_STEPS[step] > 0;
                steps.add(addRenderableWidget(Button.builder(Component.literal(adds ? "+" : "-"), button -> press(id))
                        .bounds(adds ? left + PipeConfigMenu.FLUID_COLUMN - 12 : left + 2, topPos + FLUID_STEPS_TOP + step / 2 * 12, 10, 10)
                        .build()));
            }
            fluidSteps.add(steps);
            int field = SatelliteNames.FIELD_FLUID_SLOT + slot;
            fluidSelects.add(addRenderableWidget(Button.builder(Component.empty(), button -> picker.open(field,
                    leftPos - 124, topPos + FLUID_SELECT_TOP + 14)).bounds(left + 2, topPos + FLUID_SELECT_TOP, PipeConfigMenu.FLUID_COLUMN - 4, 14)
                    .build()));
        }
        fluidSelect = addRenderableWidget(Button.builder(Component.empty(), button -> picker.open(SatelliteNames.FIELD_FLUID,
                leftPos - 124, topPos + FLUID_SHARED_TOP + 14)).bounds(leftPos - 2 - PipeConfigMenu.FLUID_COLUMN + 2, topPos + FLUID_SHARED_TOP,
                PipeConfigMenu.FLUID_COLUMN - 4, 14).build());
    }

    private static Component satelliteLabel(String name, int width, net.minecraft.client.gui.Font font) {
        return name.isEmpty() ? Component.translatable("routedpipes.gui.satellite.off") : Component.literal(font.plainSubstrByWidth(name, width - 6));
    }

    // a column takes changes to its amount once it has a fluid and a satellite to send it to
    private void showFluidPanel() {
        int open = menu.fluidSlots();
        boolean advanced = menu.advancedSatellite();
        for (int slot = 0; slot < FluidSlots.MAX; slot++) {
            boolean shown = slot < open;
            boolean usable = shown && !menu.fluidSatellite(slot).isEmpty() && !menu.fluidMarker(slot).isEmpty();
            for (Button step : fluidSteps.get(slot)) {
                step.visible = shown;
                step.active = usable;
            }
            Button select = fluidSelects.get(slot);
            select.visible = shown && advanced;
            select.setMessage(satelliteLabel(menu.fluidSatellite(slot), select.getWidth(), font));
        }
        // the satellite of all fluids sits under the whole panel
        fluidSelect.visible = open > 0 && !advanced;
        fluidSelect.setX(leftPos + PipeConfigMenu.fluidColumnLeft(Math.max(0, open - 1)) + 2);
        fluidSelect.setWidth(Math.max(1, open) * PipeConfigMenu.FLUID_COLUMN - 4);
        fluidSelect.setMessage(satelliteLabel(menu.fluidSatellite(0), fluidSelect.getWidth(), font));
    }

    private int fluidPanelLeft() {
        return leftPos + PipeConfigMenu.fluidColumnLeft(menu.fluidSlots() - 1) - 2;
    }

    // a click on the fluid panel is no click outside the window, which would drop what the mouse holds
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        boolean onFluids = menu.fluidSlots() > 0 && mouseX >= fluidPanelLeft() && mouseX < leftPos
                && mouseY >= topPos && mouseY < topPos + FLUID_PANEL_HEIGHT;
        return !onFluids && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // the list a pick comes from and the name it replaces
    private List<String> pickable() {
        return picker.field() >= SatelliteNames.FIELD_FLUID ? menu.fluidSatellitesInReach() : menu.satellitesInReach();
    }

    private String picked() {
        if (picker.field() == SatelliteNames.FIELD_FLUID) {
            return menu.fluidSatellite(0);
        }
        return picker.field() > SatelliteNames.FIELD_FLUID ? menu.fluidSatellite(picker.field() - SatelliteNames.FIELD_FLUID_SLOT)
                : shownSatellite();
    }

    private void turnPatternMarker(int change) {
        patternMarker = Math.floorMod(patternMarker + change, PipeBlockEntity.OUTPUT_SLOT);
    }

    private void pressPatternSlot(boolean up) {
        press(PipeConfigMenu.BUTTON_PATTERN_SLOT + patternMarker * 2 + (up ? 1 : 0));
    }

    private Component limitedLabel() {
        return Component.translatable(menu.limited() ? "routedpipes.gui.pattern.limited" : "routedpipes.gui.pattern.unlimited");
    }

    private void showPatternControls() {
        limited.visible = menu.patterned();
        limited.setMessage(limitedLabel());
        patternButtons.forEach(button -> button.visible = menu.patterned());
    }

    private void turnSatelliteSlot(int change) {
        satelliteSlot = Math.floorMod(satelliteSlot + change, PipeBlockEntity.OUTPUT_SLOT);
    }

    // with the upgrade a pick is for the chosen ingredient, without it for the bottom row
    private int satelliteField() {
        return menu.advancedSatellite() ? SatelliteNames.FIELD_SLOT + satelliteSlot : SatelliteNames.FIELD_OWN;
    }

    private String shownSatellite() {
        return menu.advancedSatellite() ? menu.slotSatellite(satelliteSlot) : menu.satelliteName();
    }

    private Component selectLabel() {
        String name = shownSatellite();
        return name.isEmpty() ? Component.translatable("routedpipes.gui.satellite.off")
                : Component.literal(font.plainSubstrByWidth(name, SELECT_WIDTH - 6));
    }

    private Component defaultRouteLabel() {
        return Component.translatable(menu.defaultRoute() ? "routedpipes.gui.default_route.on" : "routedpipes.gui.default_route.off");
    }

    private Component modeLabel() {
        String group = menu.kind() == PipeKind.PROVIDER ? "provider_mode" : "supply_mode";
        return Component.translatable("routedpipes.gui." + group + "." + menu.shownMode());
    }

    // for a crafter the list is what stays in the machine or what goes
    private Component excludeLabel() {
        if (menu.kind() == PipeKind.CRAFTING) {
            return Component.translatable(menu.exclude() ? "routedpipes.gui.cleanup.keep" : "routedpipes.gui.cleanup.remove");
        }
        return Component.translatable(menu.exclude() ? "routedpipes.gui.exclude" : "routedpipes.gui.include");
    }

    @Override
    protected void containerTick() {
        if (defaultRoute != null) {
            defaultRoute.setMessage(defaultRouteLabel());
        }
        if (mode != null) {
            // modules that share this layout have no mode to choose
            mode.visible = menu.mode() >= 0;
            mode.setMessage(modeLabel());
        }
        if (limited != null) {
            showPatternControls();
        }
        if (previousSlot != null) {
            previousSlot.visible = menu.advancedSatellite();
            nextSlot.visible = menu.advancedSatellite();
            select.setMessage(selectLabel());
            showFluidPanel();
        }
        if (nameField != null) {
            nameField.show(menu.satelliteName());
        }
        if (importCleanup != null) {
            importCleanup.visible = menu.cleanupSlots() > 0;
            exclude.visible = menu.cleanupSlots() > 0;
        }
        if (exclude != null) {
            exclude.setMessage(excludeLabel());
        }
    }

    private void press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // with a fuzzy upgrade the flags of a marker open under it when the mouse rests there
        Slot marker = hoveredSlot != null && hoveredSlot.index < menu.fuzzySlots() && hoveredSlot.hasItem() ? hoveredSlot : null;
        fuzzy.hover(marker == null ? -1 : marker.index, marker == null ? 0 : leftPos + marker.x, marker == null ? 0 : topPos + marker.y,
                mouseX, mouseY);
        if (picker.isOpen()) {
            picker.draw(graphics, font, pickable(), picked(), mouseX, mouseY);
        } else if (fuzzy.open() >= 0 && fuzzy.open() < menu.fuzzySlots()) {
            fuzzy.draw(graphics, font, menu.fuzzyFlags(fuzzy.open()));
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null && nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.isOpen()) {
            picker.scroll(scrollY, pickable());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picker.isOpen()) {
            // a click on a name picks it, any click closes the list
            picker.nameAt(mouseX, mouseY, pickable()).ifPresent(name -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ClientMenuLink.setText(menu.containerId, picker.field(), name);
            });
            picker.close();
            return true;
        }
        int flag = fuzzy.flagAt(mouseX, mouseY);
        if (flag < 0 || fuzzy.open() >= menu.fuzzySlots()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        press(PipeConfigMenu.BUTTON_FUZZY + fuzzy.open() * FuzzyFlags.COUNT + flag);
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.fluidSlots() > 0) {
            Panel.draw(graphics, fluidPanelLeft(), topPos, leftPos - fluidPanelLeft() + 3, FLUID_PANEL_HEIGHT, menu);
        }
        Panel.draw(graphics, leftPos, topPos, imageWidth, imageHeight, menu);
        if (menu.patterned()) {
            graphics.renderOutline(leftPos + menu.getSlot(patternMarker).x - 1, topPos + menu.getSlot(patternMarker).y - 1, 18, 18, SELECTED);
        }
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT && menu.kind() == PipeKind.CRAFTING; slot++) {
            // without the upgrade the bottom row of the recipe is what goes to the satellite
            boolean toSatellite = menu.advancedSatellite() ? !menu.slotSatellite(slot).isEmpty()
                    : !menu.satelliteName().isEmpty() && slot >= PipeBlockEntity.SATELLITE_ROW;
            int x = leftPos + menu.getSlot(slot).x;
            int y = topPos + menu.getSlot(slot).y;
            if (toSatellite) {
                graphics.fill(x, y, x + 16, y + 16, SATELLITE_TINT);
            }
            if (menu.advancedSatellite() && slot == satelliteSlot) {
                graphics.renderOutline(x - 1, y - 1, 18, 18, SELECTED);
            }
        }
    }

    // per column the amount a run takes and what each pair of buttons changes it by
    private void fluidLabels(GuiGraphics graphics) {
        if (menu.fluidSlots() == 0) {
            return;
        }
        Component title = Component.translatable("routedpipes.gui.fluid_crafting");
        graphics.drawString(font, title, fluidPanelLeft() - leftPos + 6, 6, TEXT, false);
        for (int slot = 0; slot < menu.fluidSlots(); slot++) {
            int middle = PipeConfigMenu.fluidColumnLeft(slot) + PipeConfigMenu.FLUID_COLUMN / 2;
            String amount = menu.fluidAmount(slot) + "mB";
            graphics.drawString(font, amount, middle - font.width(amount) / 2, 38, TEXT, false);
            for (int row = 0; row < PipeConfigMenu.FLUID_STEPS.length / 2; row++) {
                String step = Integer.toString(PipeConfigMenu.FLUID_STEPS[row * 2 + 1]);
                graphics.drawString(font, step, middle - font.width(step) / 2, FLUID_STEPS_TOP + 1 + row * 12, TEXT, false);
            }
        }
    }

    // inventory slots are shown counted from 1, the way players count them
    private void patternLabels(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("routedpipes.gui.pattern.into"), 8, 19, TEXT, false);
        String target = Integer.toString(menu.patternSlot(patternMarker) + 1);
        graphics.drawString(font, target, 32 - font.width(target) / 2, 32, TEXT, false);
        String marker = Integer.toString(patternMarker + 1);
        graphics.drawString(font, marker, 32 - font.width(marker) / 2, 52, TEXT, false);
        // every marker carries the number of its slot, drawn over the item
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            if (menu.getSlot(slot).hasItem()) {
                graphics.drawString(font, Integer.toString(menu.patternSlot(slot) + 1), menu.getSlot(slot).x, menu.getSlot(slot).y,
                        PATTERN_NUMBER, true);
            }
        }
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.kind() == PipeKind.CRAFTING && menu.hasByproduct()) {
            graphics.drawString(font, Component.translatable("routedpipes.gui.byproduct"), 144, 16, TEXT, false);
        }
        if (menu.kind() == PipeKind.CRAFTING) {
            graphics.drawString(font, Component.translatable("routedpipes.gui.priority"), 30, 77, TEXT, false);
            String priority = Integer.toString(menu.priority());
            graphics.drawString(font, priority, 121 - font.width(priority) / 2, 77, TEXT, false);
        }
        if (menu.patterned()) {
            patternLabels(graphics);
        }
        fluidLabels(graphics);
        for (int slot = 0; slot < menu.fuzzySlots(); slot++) {
            FuzzyPanel.mark(graphics, menu.getSlot(slot).x, menu.getSlot(slot).y, menu.fuzzyFlags(slot));
        }
        if (!hasSatelliteControls()) {
            return;
        }
        Component label = Component.translatable("routedpipes.gui.satellite");
        graphics.drawString(font, label, satelliteX() - font.width(label) / 2, satelliteY() - 14, TEXT, false);
        if (menu.kind() == PipeKind.CRAFTING && menu.advancedSatellite()) {
            Component slot = Component.translatable("routedpipes.gui.satellite.slot", satelliteSlot + 1);
            graphics.drawString(font, slot, satelliteX() - font.width(slot) / 2, satelliteY() + 13, TEXT, false);
        }
    }
}
