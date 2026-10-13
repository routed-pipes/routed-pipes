package routedpipes.adapter.minecraft.menu;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import routedpipes.adapter.minecraft.FluidPipe;
import routedpipes.domain.crafting.SatelliteName;
import routedpipes.domain.fluid.FluidSupply;
import routedpipes.domain.logistics.PipeKind;
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

// settings of fluid pipes: slots that remember a fluid, and what a supplier needs on top
// a satellite has nothing but its name, which comes in as text
public class FluidConfigMenu extends AbstractContainerMenu implements TextMenu {

    public static final int BUTTON_PARTIAL = 2;
    public static final int BUTTON_MINIMUM = 3;
    // the buttons after these change the amount a supplier keeps
    public static final int BUTTON_FIRST_STEP = 4;
    public static final int[] AMOUNT_STEPS = {-1000, -100, -10, -1, 1, 10, 100, 1000};
    // menu data is sent as short numbers, so the amount goes over in two parts
    public static final int DATA_BUCKETS = 0;
    public static final int DATA_REST = 1;
    public static final int DATA_PARTIAL = 2;
    public static final int DATA_MINIMUM = 3;
    public static final int DATA_COUNT = 4;
    public static final int BUCKET = 1000;
    public static final int MAX_AMOUNT = 32000 * BUCKET;
    private static final int MAX_CONTAINERS = 64;

    private final PipeKind kind;
    private final ContainerData data;
    private final Predicate<Player> usable;
    // turns a clicked item into what the slot remembers of it, empty if the item holds no fluid, null on the client
    private final UnaryOperator<ItemStack> markerOf;
    private final Player player;
    private boolean closed;
    private final int markerSlots;
    private final SatelliteNames names;

    // client side
    public FluidConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind) {
        this(type, id, inventory, kind, new SimpleContainer(FluidPipe.FILTER_SLOTS), new SimpleContainerData(DATA_COUNT),
                player -> true, null, null);
    }

    // satellites is what a satellite pipe knows about names, null for the other pipes and on the client
    public FluidConfigMenu(MenuType<?> type, int id, Inventory inventory, PipeKind kind, Container filter, ContainerData data,
            Predicate<Player> usable, UnaryOperator<ItemStack> markerOf, SatelliteNames.Source satellites) {
        super(type, id);
        this.names = new SatelliteNames(satellites);
        this.kind = kind;
        this.data = data;
        this.usable = usable;
        this.markerOf = markerOf;
        this.player = inventory.player;
        markerSlots = markerSlots(kind);
        for (int slot = 0; slot < markerSlots; slot++) {
            if (kind == PipeKind.FLUID_CONTAINER_SUPPLIER) {
                addSlot(new MarkerSlot(filter, slot, 62 + slot % 3 * 18, 17 + slot / 3 * 18));
            } else {
                // one fluid with its name beside it, or a row of them
                addSlot(new MarkerSlot(filter, slot, markerSlots == 1 ? 26 : 8 + slot * 18, kind == PipeKind.FLUID_SUPPLIER ? 18 : 35));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addSlot(names.slot());
        addDataSlots(data);
    }

    public PipeKind kind() {
        return kind;
    }

    // how many fluids, or for the container supplier filled containers, a pipe of that kind lists
    public static int markerSlots(PipeKind kind) {
        return switch (kind) {
            case FLUID_TERMINUS, FLUID_CONTAINER_SUPPLIER -> FluidPipe.FILTER_SLOTS;
            case FLUID_SATELLITE -> 0;
            default -> 1;
        };
    }

    // empty until the satellite has been given a name
    public String satelliteName() {
        return names.name(SatelliteNames.FIELD_OWN);
    }

    @Override
    public void setText(int field, String text) {
        if (kind != PipeKind.FLUID_SATELLITE || field != SatelliteNames.FIELD_OWN) {
            return;
        }
        SatelliteName.Verdict verdict = names.setName(field, text);
        if (verdict != SatelliteName.Verdict.ACCEPTED) {
            player.displayClientMessage(Component.translatable("routedpipes.gui.satellite.name."
                    + verdict.name().toLowerCase(Locale.ROOT)), false);
        }
    }

    // how many mB a supplier keeps in its tank
    public int amount() {
        return data.get(DATA_BUCKETS) * BUCKET + data.get(DATA_REST);
    }

    public boolean partial() {
        return data.get(DATA_PARTIAL) != 0;
    }

    public int minimum() {
        return data.get(DATA_MINIMUM);
    }

    // a fluid slot takes the fluid of whatever it is clicked with, any other click clears it
    // a container slot works like the slots of a supplier pipe: left click sets or clears, right click changes the count by one
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= markerSlots) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (markerOf == null || clickType != ClickType.PICKUP) {
            return;
        }
        Slot slot = slots.get(slotId);
        ItemStack marker = markerOf.apply(getCarried());
        if (kind != PipeKind.FLUID_CONTAINER_SUPPLIER) {
            slot.set(button == 0 ? marker : ItemStack.EMPTY);
            return;
        }
        ItemStack present = slot.getItem();
        boolean rightClick = button == 1;
        if (marker.isEmpty()) {
            slot.set(rightClick && present.getCount() > 1 ? present.copyWithCount(present.getCount() - 1) : ItemStack.EMPTY);
        } else if (ItemStack.isSameItemSameComponents(marker, present)) {
            slot.set(present.copyWithCount(Math.min(MAX_CONTAINERS, present.getCount() + (rightClick ? 1 : marker.getCount()))));
        } else {
            slot.set(marker.copyWithCount(rightClick ? 1 : marker.getCount()));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (kind != PipeKind.FLUID_SUPPLIER) {
            return false;
        }
        if (id == BUTTON_PARTIAL) {
            data.set(DATA_PARTIAL, partial() ? 0 : 1);
        } else if (id == BUTTON_MINIMUM) {
            data.set(DATA_MINIMUM, (minimum() + 1) % FluidSupply.Minimum.values().length);
        } else if (id >= BUTTON_FIRST_STEP && id < BUTTON_FIRST_STEP + AMOUNT_STEPS.length) {
            int amount = Mth.clamp(amount() + AMOUNT_STEPS[id - BUTTON_FIRST_STEP], 0, MAX_AMOUNT);
            data.set(DATA_BUCKETS, amount / BUCKET);
            data.set(DATA_REST, amount % BUCKET);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        closed = true;
    }

    // whether a player still has this open
    public boolean inUse() {
        return !closed && !player.isRemoved() && player.containerMenu == this;
    }

    @Override
    public boolean stillValid(Player player) {
        return usable.test(player);
    }
}
