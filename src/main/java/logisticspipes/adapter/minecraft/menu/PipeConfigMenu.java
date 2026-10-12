package logisticspipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
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
    public static final int DATA_COUNT = 5;
    public static final int BUTTON_PRIORITY_DOWN = 6;
    public static final int BUTTON_PRIORITY_UP = 7;
    public static final int MAX_PRIORITY = 99;
    public static final int MAX_SATELLITE_ID = 99;
    private static final int MAX_MARKER_COUNT = 64;

    private final PipeKind kind;
    private final ContainerData data;
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
        super(type, id);
        this.kind = kind;
        this.data = data;
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
        return kind == PipeKind.CRAFTING ? 104 : 84;
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
        return kind == PipeKind.PROVIDER ? ProviderMode.values().length : SupplyMode.values().length;
    }

    // left click copies the held stack or clears, right click changes the count by one
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
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
        if (id == BUTTON_EXCLUDE && kind == PipeKind.PROVIDER) {
            data.set(DATA_EXCLUDE, exclude() ? 0 : 1);
            return true;
        }
        if ((id == BUTTON_PRIORITY_DOWN || id == BUTTON_PRIORITY_UP) && kind == PipeKind.CRAFTING) {
            int change = id == BUTTON_PRIORITY_UP ? 1 : -1;
            data.set(DATA_PRIORITY, Mth.clamp(priority() + change, -MAX_PRIORITY, MAX_PRIORITY));
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
