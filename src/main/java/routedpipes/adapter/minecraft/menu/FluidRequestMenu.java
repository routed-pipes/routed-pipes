package routedpipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import routedpipes.adapter.minecraft.FluidContainerItem;
import routedpipes.adapter.minecraft.FluidPipe;
import routedpipes.adapter.minecraft.McFluidType;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidType;
import routedpipes.domain.fluid.PackedFluid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

// lists the fluid the providers of the network hold and orders it, amounts are in mB
public class FluidRequestMenu extends AbstractContainerMenu implements TextMenu {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int PAGE_SIZE = COLUMNS * ROWS;
    public static final int BUCKET = 1000;
    public static final int[] AMOUNT_STEPS = {-16 * BUCKET, -BUCKET, -100, -1, 1, 100, BUCKET, 16 * BUCKET};
    public static final int BUTTON_REQUEST = 8;
    public static final int BUTTON_PREVIOUS_PAGE = 9;
    public static final int BUTTON_NEXT_PAGE = 10;
    public static final int MAX_AMOUNT = 1000 * BUCKET;
    public static final int TEXT_SEARCH = 0;
    private static final int REFRESH_INTERVAL = 20;

    private static final int DATA_SELECTED = 0;
    // menu data is sent as short numbers, so the amount goes over in two parts
    private static final int DATA_BUCKETS = 1;
    private static final int DATA_REST = 2;
    private static final int DATA_PAGE = 3;
    private static final int DATA_PAGES = 4;

    private final Container listing = new SimpleContainer(PAGE_SIZE);
    private final ContainerData data = new SimpleContainerData(5);
    private final FluidPipe pipe;
    private final Predicate<Player> usable;
    private List<FluidType> listed = List.of();
    private FluidType selected;
    private String search = "";
    private int ticks;

    // client side
    public FluidRequestMenu(MenuType<?> type, int id) {
        this(type, id, null, player -> true);
    }

    public FluidRequestMenu(MenuType<?> type, int id, FluidPipe pipe, Predicate<Player> usable) {
        super(type, id);
        this.pipe = pipe;
        this.usable = usable;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            addSlot(new MarkerSlot(listing, slot, 8 + slot % COLUMNS * 18, 18 + slot / COLUMNS * 18));
        }
        data.set(DATA_SELECTED, -1);
        setAmount(1);
        data.set(DATA_PAGES, 1);
        addDataSlots(data);
        if (pipe != null) {
            refresh();
        }
    }

    public int selectedSlot() {
        return data.get(DATA_SELECTED);
    }

    public int amount() {
        return data.get(DATA_BUCKETS) * BUCKET + data.get(DATA_REST);
    }

    private void setAmount(int amount) {
        data.set(DATA_BUCKETS, amount / BUCKET);
        data.set(DATA_REST, amount % BUCKET);
    }

    public int page() {
        return data.get(DATA_PAGE);
    }

    public int pages() {
        return data.get(DATA_PAGES);
    }

    // how much of the fluid in a listed slot the network holds
    public static int listedAmount(ItemStack stack) {
        return FluidContainerItem.fluidIn(stack).map(FluidBatch::amount).orElse(0);
    }

    private void refresh() {
        List<Map.Entry<FluidType, Integer>> entries = new ArrayList<>(pipe.available().entrySet());
        entries.removeIf(entry -> !matchesSearch(entry.getKey()));
        entries.sort(Comparator.comparing(entry -> BuiltInRegistries.FLUID.getKey(((McFluidType) entry.getKey()).fluid())));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Mth.clamp(data.get(DATA_PAGE), 0, pages - 1);
        data.set(DATA_PAGE, page);
        data.set(DATA_PAGES, pages);
        List<FluidType> shown = new ArrayList<>();
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= entries.size()) {
                listing.setItem(slot, ItemStack.EMPTY);
                continue;
            }
            FluidType type = entries.get(index).getKey();
            // the container in the list holds all there is of its fluid
            listing.setItem(slot, FluidContainerItem.of(new FluidBatch(type, entries.get(index).getValue())));
            shown.add(type);
        }
        listed = shown;
        data.set(DATA_SELECTED, selected == null ? -1 : listed.indexOf(selected));
    }

    // by display name or fluid id
    private boolean matchesSearch(FluidType type) {
        if (search.isEmpty()) {
            return true;
        }
        return name(type).getString().toLowerCase(Locale.ROOT).contains(search)
                || BuiltInRegistries.FLUID.getKey(((McFluidType) type).fluid()).toString().contains(search);
    }

    @Override
    public void setText(int field, String text) {
        if (pipe == null || field != TEXT_SEARCH) {
            return;
        }
        search = text.strip().toLowerCase(Locale.ROOT);
        data.set(DATA_PAGE, 0);
        refresh();
    }

    @Override
    public void broadcastChanges() {
        if (pipe != null && ++ticks % REFRESH_INTERVAL == 0) {
            refresh();
        }
        super.broadcastChanges();
    }

    // clicking a listed fluid selects it, nothing is ever picked up
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= PAGE_SIZE) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (pipe != null && slotId < listed.size()) {
            selected = listed.get(slotId);
            data.set(DATA_SELECTED, slotId);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (pipe == null) {
            return false;
        }
        if (id >= 0 && id < AMOUNT_STEPS.length) {
            int step = AMOUNT_STEPS[id];
            // going up from the single mB a fresh screen shows gives round amounts: 1 and 1000 make 1000
            int from = amount() == 1 && step > 1 ? 0 : amount();
            setAmount(Mth.clamp(from + step, 1, MAX_AMOUNT));
        } else if (id == BUTTON_REQUEST) {
            request(player);
        } else if (id == BUTTON_PREVIOUS_PAGE || id == BUTTON_NEXT_PAGE) {
            data.set(DATA_PAGE, data.get(DATA_PAGE) + (id == BUTTON_NEXT_PAGE ? 1 : -1));
            refresh();
        } else {
            return false;
        }
        return true;
    }

    private void request(Player player) {
        if (selected == null) {
            return;
        }
        RequestOutcome outcome = pipe.request(new FluidBatch(selected, amount()));
        if (outcome.started()) {
            player.displayClientMessage(Component.translatable("routedpipes.request.fluid.started", amount(), name(selected)), false);
        } else if (!outcome.powered()) {
            player.displayClientMessage(Component.translatable("routedpipes.request.no_power")
                    .withStyle(ChatFormatting.RED), false);
        } else {
            MutableComponent missing = Component.empty();
            outcome.missing().forEach((type, amount) -> missing.append("\n").append(
                    Component.translatable("routedpipes.request.fluid.missing_entry", amount, name(((PackedFluid) type).fluid()))));
            player.displayClientMessage(Component.translatable("routedpipes.request.missing")
                    .withStyle(ChatFormatting.RED).append(missing), false);
        }
        refresh();
    }

    private static Component name(FluidType type) {
        return FluidContainerItem.marker(type).getHoverName();
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
