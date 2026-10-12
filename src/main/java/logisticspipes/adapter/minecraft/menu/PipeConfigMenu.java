package logisticspipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.logistics.PipeKind;
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

// settings of supplier, crafting and satellite pipes: marker slots that only remember an item and a count
public class PipeConfigMenu extends AbstractContainerMenu {

    public static final int BUTTON_SATELLITE_DOWN = 0;
    public static final int BUTTON_SATELLITE_UP = 1;
    public static final int BUTTON_IMPORT_RECIPE = 2;
    public static final int MAX_SATELLITE_ID = 99;
    private static final int MAX_MARKER_COUNT = 64;

    private final PipeKind kind;
    private final ContainerData data;
    private final Predicate<Player> usable;
    private final Runnable importRecipe;
    private final int markerSlots;

    // client side
    public PipeConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind) {
        this(type, id, inventory, kind, new SimpleContainer(PipeBlockEntity.CONFIG_SLOTS), new SimpleContainerData(1),
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
            int left = kind == PipeKind.CRAFTING ? 30 : 62;
            for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
                addSlot(new MarkerSlot(config, slot, left + slot % 3 * 18, 17 + slot / 3 * 18));
            }
        }
        if (kind == PipeKind.CRAFTING) {
            addSlot(new MarkerSlot(config, PipeBlockEntity.OUTPUT_SLOT, 124, 26));
        }
        markerSlots = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    public PipeKind kind() {
        return kind;
    }

    public int satelliteId() {
        return data.get(0);
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
        if (id != BUTTON_SATELLITE_DOWN && id != BUTTON_SATELLITE_UP) {
            return false;
        }
        int change = id == BUTTON_SATELLITE_UP ? 1 : -1;
        data.set(0, Mth.clamp(data.get(0) + change, 0, MAX_SATELLITE_ID));
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
