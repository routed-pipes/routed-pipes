package logisticspipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.item.FuzzyFlags;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.ProviderMode;
import logisticspipes.domain.logistics.SupplyMode;
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

// settings of basic, provider, supplier, crafting and satellite pipes: marker slots that only remember an item and a count
public class PipeConfigMenu extends AbstractContainerMenu implements MarkerMenu {

    public static final int BUTTON_SATELLITE_DOWN = 0;
    public static final int BUTTON_SATELLITE_UP = 1;
    public static final int BUTTON_IMPORT_RECIPE = 2;
    public static final int BUTTON_DEFAULT_ROUTE = 3;
    public static final int BUTTON_MODE = 4;
    public static final int BUTTON_EXCLUDE = 5;
    public static final int DATA_SATELLITE = 0;
    public static final int DATA_DEFAULT_ROUTE = 1;
    // which provider or supply mode, -1 where there is none to choose
    public static final int DATA_MODE = 2;
    public static final int DATA_EXCLUDE = 3;
    public static final int DATA_PRIORITY = 4;
    // whether there is a slot for what else the machine puts out, and how many cleanup upgrades there are
    public static final int DATA_BYPRODUCT = 5;
    public static final int DATA_CLEANUP = 6;
    // not 0 with the upgrade that gives every ingredient a satellite of its own, followed by those nine satellites
    public static final int DATA_ADVANCED_SATELLITE = 7;
    public static final int DATA_SLOT_SATELLITE = 8;
    public static final int DATA_COUNT = 17;
    // plus two per ingredient slot: down and up
    public static final int BUTTON_SLOT_SATELLITE = 30;
    public static final int BUTTON_IMPORT_CLEANUP = 8;
    public static final int CLEANUP_PER_UPGRADE = 3;
    public static final int BUTTON_PRIORITY_DOWN = 6;
    public static final int BUTTON_PRIORITY_UP = 7;
    public static final int MAX_PRIORITY = 99;
    public static final int MAX_SATELLITE_ID = 99;
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

    private final PipeKind kind;
    private final ContainerData data;
    private final ContainerData extras;
    private final Predicate<Player> usable;
    private final Runnable importRecipe;
    private final int markerSlots;

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
        super(type, id);
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
        addDataSlots(data);
        addDataSlots(extras);
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
    public void setMarker(int slot, ItemStack stack) {
        slots.get(slot).set(stack);
    }

    public PipeKind kind() {
        return kind;
    }

    public int satelliteId() {
        return data.get(DATA_SATELLITE);
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

    public int slotSatellite(int slot) {
        return data.get(DATA_SLOT_SATELLITE + slot);
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
        int slotButton = id - BUTTON_SLOT_SATELLITE;
        if (kind == PipeKind.CRAFTING && advancedSatellite() && slotButton >= 0 && slotButton < PipeBlockEntity.OUTPUT_SLOT * 2) {
            int slot = slotButton / 2;
            int change = slotButton % 2 == 1 ? 1 : -1;
            data.set(DATA_SLOT_SATELLITE + slot, Mth.clamp(slotSatellite(slot) + change, 0, MAX_SATELLITE_ID));
            return true;
        }
        if (id != BUTTON_SATELLITE_DOWN && id != BUTTON_SATELLITE_UP) {
            return false;
        }
        int change = id == BUTTON_SATELLITE_UP ? 1 : -1;
        data.set(DATA_SATELLITE, Mth.clamp(satelliteId() + change, 0, MAX_SATELLITE_ID));
        return true;
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
