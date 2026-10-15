package routedpipes.adapter.minecraft.menu;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import routedpipes.adapter.minecraft.FluidContainerItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.domain.crafting.FluidSlots;
import routedpipes.domain.crafting.SatelliteName;
import routedpipes.domain.item.FuzzyFlags;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.ProviderMode;
import routedpipes.domain.logistics.SupplyMode;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// settings of basic, void, provider, supplier, crafting and satellite pipes: marker slots that only remember an item and a count
// a satellite has its name typed in, a crafter is told the name of the satellite to use, both as text
public class PipeConfigMenu extends AbstractContainerMenu implements MarkerMenu, TextMenu {

    public static final int BUTTON_IMPORT_RECIPE = 2;
    public static final int BUTTON_DEFAULT_ROUTE = 3;
    public static final int BUTTON_MODE = 4;
    public static final int BUTTON_EXCLUDE = 5;
    // not 0 with the upgrade that gives every ingredient a satellite of its own
    public static final int DATA_ADVANCED_SATELLITE = 0;
    public static final int DATA_DEFAULT_ROUTE = 1;
    // which provider or supply mode, -1 where there is none to choose
    public static final int DATA_MODE = 2;
    public static final int DATA_EXCLUDE = 3;
    public static final int DATA_PRIORITY = 4;
    // whether there is a slot for what else the machine puts out, and how many cleanup upgrades there are
    public static final int DATA_BYPRODUCT = 5;
    public static final int DATA_CLEANUP = 6;
    public static final int DATA_COUNT = 7;
    public static final int BUTTON_IMPORT_CLEANUP = 8;
    public static final int CLEANUP_PER_UPGRADE = 3;
    public static final int BUTTON_PRIORITY_DOWN = 6;
    public static final int BUTTON_PRIORITY_UP = 7;
    public static final int MAX_PRIORITY = 99;
    private static final int MAX_MARKER_COUNT = 64;
    // a second set of numbers for what upgrades add to the marker slots
    // not 0 with a pattern upgrade, the switch for what does not fit its slot, and the inventory slot of every marker
    public static final int EXTRA_PATTERN = 0;
    public static final int EXTRA_LIMITED = 1;
    public static final int EXTRA_PATTERN_SLOT = 2;
    // not 0 with a fuzzy upgrade, and the fuzzy flags of every marker and of the result of a recipe as bits
    public static final int EXTRA_FUZZY = EXTRA_PATTERN_SLOT + PipeBlockEntity.OUTPUT_SLOT;
    public static final int EXTRA_FUZZY_FLAGS = EXTRA_FUZZY + 1;
    public static final int FUZZY_SLOTS = PipeBlockEntity.OUTPUT_SLOT + 1;
    public static final int EXTRA_COUNT = EXTRA_FUZZY_FLAGS + FUZZY_SLOTS;
    // plus four per slot, one for every flag
    public static final int BUTTON_FUZZY = 50;
    public static final int BUTTON_LIMITED = 9;
    // plus two per marker slot: down and up
    public static final int BUTTON_PATTERN_SLOT = 100;
    public static final int MAX_PATTERN_SLOT = 98;
    // a pattern has no use for the mode that fills all the room there is
    private static final int PATTERN_MODES = SupplyMode.INFINITE.ordinal();
    // a third set for fluid crafting: how many upgrades, then the amount of every fluid slot in buckets and the rest
    // menu data is sent as short numbers, an amount would not fit
    public static final int FLUID_UPGRADES = 0;
    public static final int FLUID_BUCKETS = 1;
    public static final int FLUID_REST = FLUID_BUCKETS + FluidSlots.MAX;
    public static final int FLUID_DATA_COUNT = FLUID_REST + FluidSlots.MAX;
    public static final int BUCKET = 1000;
    // plus eight per fluid slot, one for every step, taking away and adding by turns
    public static final int BUTTON_FLUID_AMOUNT = 150;
    public static final int[] FLUID_STEPS = {-1, 1, -10, 10, -100, 100, -1000, 1000};
    // the fluid slots sit in columns left of the window, the first one next to it
    public static final int FLUID_COLUMN = 46;
    public static final int FLUID_SLOT_Y = 18;

    private final PipeKind kind;
    private final ContainerData data;
    private final ContainerData extras;
    private final Predicate<Player> usable;
    private final Runnable importRecipe;
    private final int markerSlots;
    private final SatelliteNames names;
    private final ContainerData fluids;
    private final Player player;
    private final Supplier<Map<ItemType, Integer>> reachable;

    // client side
    public PipeConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind) {
        this(type, id, inventory, kind, new SimpleContainer(PipeBlockEntity.CONFIG_SLOTS), new SimpleContainerData(DATA_COUNT),
                player -> true, () -> { });
    }

    public PipeConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind, Container config,
            ContainerData data, Predicate<Player> usable, Runnable importRecipe) {
        this(type, id, inventory, kind, config, data, new SimpleContainerData(EXTRA_COUNT), usable, importRecipe);
    }

    public PipeConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind, Container config,
            ContainerData data, ContainerData extras, Predicate<Player> usable, Runnable importRecipe) {
        this(type, id, inventory, kind, config, data, extras, usable, importRecipe, null);
    }

    // satellites is what the pipe knows about satellite names, null on the client and where there are none
    public PipeConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind, Container config,
            ContainerData data, ContainerData extras, Predicate<Player> usable, Runnable importRecipe,
            SatelliteNames.Source satellites) {
        this(type, id, inventory, kind, config, data, extras, usable, importRecipe, satellites, new SimpleContainerData(FLUID_DATA_COUNT));
    }

    // fluids is what a crafter knows about fluid crafting
    public PipeConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind, Container config,
            ContainerData data, ContainerData extras, Predicate<Player> usable, Runnable importRecipe,
            SatelliteNames.Source satellites, ContainerData fluids) {
        this(type, id, inventory, kind, config, data, extras, usable, importRecipe, satellites, fluids, Map::of);
    }

    // reachable is what the network of the pipe can bring
    public PipeConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind, Container config,
            ContainerData data, ContainerData extras, Predicate<Player> usable, Runnable importRecipe,
            SatelliteNames.Source satellites, ContainerData fluids, Supplier<Map<ItemType, Integer>> reachable) {
        super(type, id);
        this.reachable = reachable;
        this.names = new SatelliteNames(satellites);
        this.fluids = fluids;
        this.player = inventory.player;
        this.kind = kind;
        this.data = data;
        this.extras = extras;
        this.usable = usable;
        this.importRecipe = importRecipe;
        if (kind != PipeKind.SATELLITE) {
            int left = kind == PipeKind.SUPPLIER ? 62 : kind == PipeKind.PROVIDER ? 8 : 30;
            for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
                addSlot(new MarkerSlot(config, slot, left + slot % 3 * 18, 17 + slot / 3 * 18));
            }
        }
        if (kind == PipeKind.CRAFTING) {
            addSlot(new MarkerSlot(config, PipeBlockEntity.OUTPUT_SLOT, 124, 26));
            addSlot(new MarkerSlot(config, PipeBlockEntity.BYPRODUCT_SLOT, 148, 26, () -> data.get(DATA_BYPRODUCT) != 0));
            for (int index = 0; index < PipeBlockEntity.CLEANUP_SLOTS; index++) {
                int number = index;
                addSlot(new MarkerSlot(config, PipeBlockEntity.CLEANUP_FIRST + index, 8 + index % 6 * 18, 96 + index / 6 * 18,
                        () -> number < cleanupSlots()));
            }
            for (int index = 0; index < FluidSlots.MAX; index++) {
                int number = index;
                addSlot(new MarkerSlot(config, PipeBlockEntity.FLUID_FIRST + index, fluidColumnLeft(index) + (FLUID_COLUMN - 16) / 2,
                        FLUID_SLOT_Y, () -> number < fluidSlots()));
            }
        }
        markerSlots = slots.size();
        int top = inventoryTop(kind);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, top + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, top + 58));
        }
        addSlot(names.slot());
        addDataSlots(data);
        addDataSlots(extras);
        addDataSlots(fluids);
    }

    // where the column of a fluid slot starts, left of the window
    public static int fluidColumnLeft(int slot) {
        return -2 - (slot + 1) * FLUID_COLUMN;
    }

    // how many fluids the recipe of a crafter takes, one per fluid crafting upgrade
    public int fluidSlots() {
        return kind == PipeKind.CRAFTING ? FluidSlots.open(fluids.get(FLUID_UPGRADES)) : 0;
    }

    // mB per run
    public int fluidAmount(int slot) {
        return fluids.get(FLUID_BUCKETS + slot) * BUCKET + fluids.get(FLUID_REST + slot);
    }

    public ItemStack fluidMarker(int slot) {
        return slots.get(PipeBlockEntity.FLUID_FIRST + slot).getItem();
    }

    // the fluid satellite of all fluids, or with the upgrade the one of a fluid slot
    public String fluidSatellite(int slot) {
        return names.name(advancedSatellite() ? SatelliteNames.FIELD_FLUID_SLOT + slot : SatelliteNames.FIELD_FLUID);
    }

    // the fluid satellites a crafter has to pick from, nearest first
    public List<String> fluidSatellitesInReach() {
        return names.offeredFluid();
    }

    private static boolean fluidSlot(int slot) {
        return slot >= PipeBlockEntity.FLUID_FIRST && slot < PipeBlockEntity.FLUID_FIRST + FluidSlots.MAX;
    }

    private void setFluidAmount(int slot, int amount) {
        fluids.set(FLUID_BUCKETS + slot, amount / BUCKET);
        fluids.set(FLUID_REST + slot, amount % BUCKET);
    }

    // the name of a satellite pipe, or the one the bottom row of a crafter goes to, empty for none
    public String satelliteName() {
        return names.name(SatelliteNames.FIELD_OWN);
    }

    // the satellites a crafter has to pick from, nearest first
    public List<String> satellitesInReach() {
        return names.offered();
    }

    @Override
    public void broadcastChanges() {
        names.tick();
        super.broadcastChanges();
    }

    // a satellite gets its own name, a crafter the satellite of its bottom row or with the upgrade the one of a slot
    @Override
    public void setText(int field, String text) {
        boolean own = field == SatelliteNames.FIELD_OWN;
        boolean fits = kind == PipeKind.SATELLITE ? own : kind == PipeKind.CRAFTING && own != advancedSatellite();
        if (field >= SatelliteNames.FIELD_FLUID) {
            // the fluid satellite of all fluids, or with the upgrade one of the open fluid slots
            fits = advancedSatellite() ? field - SatelliteNames.FIELD_FLUID_SLOT >= 0 && field - SatelliteNames.FIELD_FLUID_SLOT < fluidSlots()
                    : field == SatelliteNames.FIELD_FLUID && fluidSlots() > 0;
        }
        if (!fits || field < 0 || field >= SatelliteNames.FIELDS) {
            return;
        }
        SatelliteName.Verdict verdict = names.setName(field, text);
        if (verdict != SatelliteName.Verdict.ACCEPTED) {
            player.displayClientMessage(Component.translatable("routedpipes.gui.satellite.name."
                    + verdict.name().toLowerCase(Locale.ROOT)), false);
        }
    }

    // whether the markers of a supplier stand for slots of the inventory
    public boolean patterned() {
        return kind == PipeKind.SUPPLIER && mode() >= 0 && extras.get(EXTRA_PATTERN) != 0;
    }

    public boolean limited() {
        return extras.get(EXTRA_LIMITED) != 0;
    }

    // counted from 0
    public int patternSlot(int marker) {
        return extras.get(EXTRA_PATTERN_SLOT + marker);
    }

    // how many of the first slots have fuzzy flags to set, 0 without the upgrade for it
    public int fuzzySlots() {
        if (extras.get(EXTRA_FUZZY) == 0 || kind != PipeKind.BASIC && kind != PipeKind.CRAFTING) {
            return 0;
        }
        return kind == PipeKind.CRAFTING ? FUZZY_SLOTS : PipeBlockEntity.OUTPUT_SLOT;
    }

    public FuzzyFlags fuzzyFlags(int slot) {
        return FuzzyFlags.of(extras.get(EXTRA_FUZZY_FLAGS + slot));
    }

    // the mode as the button shows it
    public int shownMode() {
        return Math.floorMod(Math.max(0, mode()), modeCount());
    }

    @Override
    public int markerSlots() {
        return markerSlots;
    }

    @Override
    public Map<ItemType, Integer> available() {
        return reachable.get();
    }

    @Override
    public void setMarker(int slot, ItemStack stack) {
        // a fluid slot only ever holds the marker of a fluid
        slots.get(slot).set(kind == PipeKind.CRAFTING && fluidSlot(slot) ? FluidContainerItem.markerOf(stack) : stack);
    }

    public PipeKind kind() {
        return kind;
    }

    public boolean defaultRoute() {
        return data.get(DATA_DEFAULT_ROUTE) != 0;
    }

    // crafting settings have a row more than the others
    public static int inventoryTop(PipeKind kind) {
        return kind == PipeKind.CRAFTING ? 144 : 84;
    }

    public boolean advancedSatellite() {
        return data.get(DATA_ADVANCED_SATELLITE) != 0;
    }

    public String slotSatellite(int slot) {
        return names.name(SatelliteNames.FIELD_SLOT + slot);
    }

    public boolean hasByproduct() {
        return data.get(DATA_BYPRODUCT) != 0;
    }

    public int cleanupSlots() {
        return Math.min(PipeBlockEntity.CLEANUP_SLOTS, data.get(DATA_CLEANUP) * CLEANUP_PER_UPGRADE);
    }

    // the recipe becomes the list of what is taken out of the machine again
    private void importCleanup() {
        int next = PipeBlockEntity.CLEANUP_FIRST;
        for (int slot = 0; slot <= PipeBlockEntity.OUTPUT_SLOT; slot++) {
            ItemStack marker = slots.get(slot).getItem();
            boolean listed = false;
            for (int other = PipeBlockEntity.CLEANUP_FIRST; other < next; other++) {
                listed |= ItemStack.isSameItem(slots.get(other).getItem(), marker);
            }
            if (!marker.isEmpty() && !listed && next < PipeBlockEntity.CLEANUP_FIRST + PipeBlockEntity.CLEANUP_SLOTS) {
                slots.get(next++).set(marker.copyWithCount(1));
            }
        }
        while (next < PipeBlockEntity.CLEANUP_FIRST + PipeBlockEntity.CLEANUP_SLOTS) {
            slots.get(next++).set(ItemStack.EMPTY);
        }
        data.set(DATA_EXCLUDE, 0);
    }

    public int priority() {
        return data.get(DATA_PRIORITY);
    }

    public int mode() {
        return data.get(DATA_MODE);
    }

    public boolean exclude() {
        return data.get(DATA_EXCLUDE) != 0;
    }

    private int modeCount() {
        if (patterned()) {
            return PATTERN_MODES;
        }
        return kind == PipeKind.PROVIDER ? ProviderMode.values().length : SupplyMode.values().length;
    }

    // left click copies the held stack or clears, right click changes the count by one
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < markerSlots && !slots.get(slotId).isActive()) {
            return;
        }
        if (slotId < 0 || slotId >= markerSlots) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (clickType != ClickType.PICKUP) {
            return;
        }
        Slot slot = slots.get(slotId);
        ItemStack held = getCarried();
        ItemStack marker = slot.getItem();
        boolean rightClick = button == 1;
        if (kind == PipeKind.CRAFTING && fluidSlot(slotId)) {
            // the fluid of what it is clicked with, any other click clears it
            slot.set(rightClick ? ItemStack.EMPTY : FluidContainerItem.markerOf(held));
            return;
        }
        if (held.isEmpty()) {
            slot.set(rightClick && marker.getCount() > 1 ? marker.copyWithCount(marker.getCount() - 1) : ItemStack.EMPTY);
        } else if (ItemStack.isSameItemSameComponents(held, marker)) {
            int added = rightClick ? 1 : held.getCount();
            slot.set(marker.copyWithCount(Math.min(MAX_MARKER_COUNT, marker.getCount() + added)));
        } else {
            slot.set(held.copyWithCount(rightClick ? 1 : held.getCount()));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_IMPORT_RECIPE && kind == PipeKind.CRAFTING) {
            importRecipe.run();
            return true;
        }
        if (id == BUTTON_DEFAULT_ROUTE && kind == PipeKind.BASIC) {
            data.set(DATA_DEFAULT_ROUTE, defaultRoute() ? 0 : 1);
            return true;
        }
        if (id == BUTTON_MODE && (kind == PipeKind.PROVIDER || kind == PipeKind.SUPPLIER) && mode() >= 0) {
            data.set(DATA_MODE, (mode() + 1) % modeCount());
            return true;
        }
        if (id == BUTTON_IMPORT_CLEANUP && kind == PipeKind.CRAFTING && cleanupSlots() > 0) {
            importCleanup();
            return true;
        }
        if (id == BUTTON_EXCLUDE && (kind == PipeKind.PROVIDER || kind == PipeKind.CRAFTING)) {
            data.set(DATA_EXCLUDE, exclude() ? 0 : 1);
            return true;
        }
        if ((id == BUTTON_PRIORITY_DOWN || id == BUTTON_PRIORITY_UP) && kind == PipeKind.CRAFTING) {
            int change = id == BUTTON_PRIORITY_UP ? 1 : -1;
            data.set(DATA_PRIORITY, Mth.clamp(priority() + change, -MAX_PRIORITY, MAX_PRIORITY));
            return true;
        }
        if (id == BUTTON_LIMITED && patterned()) {
            extras.set(EXTRA_LIMITED, limited() ? 0 : 1);
            return true;
        }
        int fluidButton = id - BUTTON_FLUID_AMOUNT;
        if (fluidButton >= 0 && fluidButton < fluidSlots() * FLUID_STEPS.length) {
            int slot = fluidButton / FLUID_STEPS.length;
            setFluidAmount(slot, FluidSlots.changed(fluidAmount(slot), FLUID_STEPS[fluidButton % FLUID_STEPS.length]));
            return true;
        }
        int fuzzyButton = id - BUTTON_FUZZY;
        if (fuzzyButton >= 0 && fuzzyButton < fuzzySlots() * FuzzyFlags.COUNT) {
            int slot = fuzzyButton / FuzzyFlags.COUNT;
            extras.set(EXTRA_FUZZY_FLAGS + slot, fuzzyFlags(slot).bits() ^ 1 << fuzzyButton % FuzzyFlags.COUNT);
            return true;
        }
        int patternButton = id - BUTTON_PATTERN_SLOT;
        if (patterned() && patternButton >= 0 && patternButton < PipeBlockEntity.OUTPUT_SLOT * 2) {
            int marker = patternButton / 2;
            int change = patternButton % 2 == 1 ? 1 : -1;
            extras.set(EXTRA_PATTERN_SLOT + marker, Mth.clamp(patternSlot(marker) + change, 0, MAX_PATTERN_SLOT));
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return usable.test(player);
    }
}
