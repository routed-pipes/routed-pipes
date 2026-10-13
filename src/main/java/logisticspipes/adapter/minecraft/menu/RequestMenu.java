package logisticspipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.DiskDrive;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.listing.Page;
import logisticspipes.domain.request.RequestSummary;
import logisticspipes.application.RequestOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// lists what the network can deliver and orders it, everything runs over plain menu slots and buttons
public class RequestMenu extends AbstractContainerMenu implements TextMenu {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int PAGE_SIZE = COLUMNS * ROWS;
    public static final int[] AMOUNT_STEPS = {-64, -10, -1, 1, 10, 64};
    public static final int BUTTON_REQUEST = 6;
    public static final int BUTTON_PREVIOUS_PAGE = 7;
    public static final int BUTTON_NEXT_PAGE = 8;
    public static final int BUTTON_DISK = 9;
    // shows what the picked request would take without making it, and goes back to the list
    public static final int BUTTON_PREVIEW = 10;
    // gives up on the crafted items the pipe is still waiting for
    public static final int BUTTON_CANCEL = 11;
    // shows the disk of a pipe that takes one, a click on it throws the disk out
    public static final int DISK_SLOT = PAGE_SIZE;
    // the preview takes the place of the list while it is on show
    public static final int PREVIEW_ROWS = 5;
    public static final int PREVIEW_SIZE = COLUMNS * PREVIEW_ROWS;
    public static final int FIRST_PREVIEW_SLOT = DISK_SLOT + 1;
    // the first slot a menu built on this one adds
    protected static final int OWN_SLOTS = FIRST_PREVIEW_SLOT + PREVIEW_SIZE;
    public static final int MAX_AMOUNT = 9999;
    public static final int TEXT_SEARCH = 0;
    // listed stacks carry their network wide amount in here, the stack count is always 1
    private static final String COUNT_TAG = "LogisticsPipesCount";
    // previewed stacks say where their items come from in here
    private static final String SOURCE_TAG = "LogisticsPipesSource";
    private static final int REFRESH_INTERVAL = 20;

    private static final int DATA_SELECTED = 0;
    private static final int DATA_AMOUNT = 1;
    private static final int DATA_PAGE = 2;
    private static final int DATA_PAGES = 3;
    private static final int DATA_TAKES_DISK = 4;
    private static final int DATA_OPEN_ORDERS = 5;
    private static final int PREVIEW_SHOWING = 0;
    private static final int PREVIEW_PAGE = 1;
    private static final int PREVIEW_PAGES = 2;

    // where the items of a previewed request come from
    public enum Source {
        STOCK, CRAFTED, MISSING
    }

    private final Container listing = new SimpleContainer(PAGE_SIZE);
    private final ContainerData data = new SimpleContainerData(6);
    private final Container previewed = new SimpleContainer(PREVIEW_SIZE);
    private final ContainerData preview = new SimpleContainerData(3);
    private final LongData previewEnergy;
    private List<ItemStack> previewEntries = List.of();
    private long energy;
    private final PipeBlockEntity pipe;
    // null unless the pipe takes a disk
    private final DiskDrive drive;
    private final Predicate<Player> usable;
    private List<ItemType> listed = List.of();
    private ItemType selected;
    private String search = "";
    private int ticks;

    // client side
    public RequestMenu(MenuType<?> type, int id) {
        this(type, id, null, player -> true);
    }

    public RequestMenu(MenuType<?> type, int id, PipeBlockEntity pipe, Predicate<Player> usable) {
        this(type, id, pipe, usable, null);
    }

    public RequestMenu(MenuType<?> type, int id, PipeBlockEntity pipe, Predicate<Player> usable, DiskDrive drive) {
        super(type, id);
        this.pipe = pipe;
        this.usable = usable;
        this.drive = drive;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            addSlot(new MarkerSlot(listing, slot, 8 + slot % COLUMNS * 18, 18 + slot / COLUMNS * 18, () -> !previewing()));
        }
        addSlot(new MarkerSlot(drive == null ? new SimpleContainer(1) : drive.slot(), 0, 152, 162, this::takesDisk));
        for (int slot = 0; slot < PREVIEW_SIZE; slot++) {
            addSlot(new MarkerSlot(previewed, slot, 8 + slot % COLUMNS * 18, 18 + slot / COLUMNS * 18, this::previewing));
        }
        data.set(DATA_SELECTED, -1);
        data.set(DATA_AMOUNT, 1);
        data.set(DATA_PAGES, 1);
        data.set(DATA_TAKES_DISK, drive == null ? 0 : 1);
        addDataSlots(data);
        preview.set(PREVIEW_PAGES, 1);
        addDataSlots(preview);
        previewEnergy = pipe == null ? new LongData(1) : new LongData(() -> energy);
        addDataSlots(previewEnergy);
        if (pipe != null) {
            refresh();
        }
    }

    public int selectedSlot() {
        return data.get(DATA_SELECTED);
    }

    public int amount() {
        return data.get(DATA_AMOUNT);
    }

    // of the preview while that is on show
    public int page() {
        return previewing() ? preview.get(PREVIEW_PAGE) : data.get(DATA_PAGE);
    }

    public int pages() {
        return previewing() ? preview.get(PREVIEW_PAGES) : data.get(DATA_PAGES);
    }

    // false for menus that pick items for something else than ordering them
    public boolean previews() {
        return true;
    }

    public boolean previewing() {
        return preview.get(PREVIEW_SHOWING) != 0;
    }

    // what moving the items of the previewed request costs
    public long previewEnergy() {
        return previewEnergy.value(0);
    }

    // empty for a stack that is not part of a preview
    public static Optional<Source> sourceOf(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(SOURCE_TAG)
                ? Optional.of(Source.values()[Math.floorMod(tag.getInt(SOURCE_TAG), Source.values().length)]) : Optional.empty();
    }

    // how many crafted items the pipe is still waiting for, counted no further than a data slot holds
    public int openOrders() {
        return data.get(DATA_OPEN_ORDERS);
    }

    public boolean takesDisk() {
        return data.get(DATA_TAKES_DISK) != 0;
    }

    public boolean holdsDisk() {
        return takesDisk() && slots.get(DISK_SLOT).hasItem();
    }

    // null if nothing is selected
    protected ItemType selected() {
        return selected;
    }

    public static int listedCount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(COUNT_TAG);
    }

    // one of the item with the amount to show written on it, see listedCount
    public static ItemStack listed(ItemType type, int count) {
        ItemStack stack = McItemType.stackOf(new ItemBatch(type, 1));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(COUNT_TAG, count));
        return stack;
    }

    private void refresh() {
        data.set(DATA_OPEN_ORDERS, Math.min(pipe.openOrders(), Short.MAX_VALUE));
        List<Map.Entry<ItemType, Integer>> entries = new ArrayList<>(pipe.available().entrySet());
        entries.removeIf(entry -> !matchesSearch(entry.getKey()));
        entries.sort(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(((McItemType) entry.getKey()).item())));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Mth.clamp(data.get(DATA_PAGE), 0, pages - 1);
        data.set(DATA_PAGE, page);
        data.set(DATA_PAGES, pages);
        List<ItemType> shown = new ArrayList<>();
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= entries.size()) {
                listing.setItem(slot, ItemStack.EMPTY);
                continue;
            }
            ItemType type = entries.get(index).getKey();
            int count = entries.get(index).getValue();
            listing.setItem(slot, listed(type, count));
            shown.add(type);
        }
        listed = shown;
        data.set(DATA_SELECTED, selected == null ? -1 : listed.indexOf(selected));
    }

    // plans the picked item and amount and lists what that takes, nothing is ordered
    private void showPreview() {
        if (selected == null) {
            return;
        }
        RequestSummary summary = pipe.previewRequest(new ItemBatch(selected, data.get(DATA_AMOUNT)));
        List<ItemStack> entries = new ArrayList<>();
        addPreviewed(entries, Source.STOCK, summary.fromStock());
        addPreviewed(entries, Source.CRAFTED, summary.crafted());
        addPreviewed(entries, Source.MISSING, summary.missing());
        previewEntries = entries;
        energy = summary.energy();
        preview.set(PREVIEW_SHOWING, 1);
        showPreviewPage();
    }

    private static void addPreviewed(List<ItemStack> entries, Source source, Map<ItemType, Integer> items) {
        items.entrySet().stream()
                .sorted(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(((McItemType) entry.getKey()).item())))
                .forEach(entry -> {
                    ItemStack stack = listed(entry.getKey(), entry.getValue());
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SOURCE_TAG, source.ordinal()));
                    entries.add(stack);
                });
    }

    private void showPreviewPage() {
        Page<ItemStack> page = Page.of(previewEntries, preview.get(PREVIEW_PAGE), PREVIEW_SIZE);
        preview.set(PREVIEW_PAGE, page.number());
        preview.set(PREVIEW_PAGES, page.pages());
        for (int slot = 0; slot < PREVIEW_SIZE; slot++) {
            previewed.setItem(slot, slot < page.entries().size() ? page.entries().get(slot) : ItemStack.EMPTY);
        }
    }

    private void closePreview() {
        preview.set(PREVIEW_SHOWING, 0);
        preview.set(PREVIEW_PAGE, 0);
    }

    // by display name or item id, the way most people look for things
    private boolean matchesSearch(ItemType type) {
        if (search.isEmpty()) {
            return true;
        }
        McItemType item = (McItemType) type;
        return name(type).getString().toLowerCase(Locale.ROOT).contains(search)
                || BuiltInRegistries.ITEM.getKey(item.item()).toString().contains(search);
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

    // clicking a listed item selects it, nothing is ever picked up
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == DISK_SLOT) {
            if (drive != null && clickType == ClickType.PICKUP) {
                drive.eject();
            }
            return;
        }
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
            data.set(DATA_AMOUNT, Mth.clamp(data.get(DATA_AMOUNT) + AMOUNT_STEPS[id], 1, MAX_AMOUNT));
            if (previewing()) {
                showPreview();
            }
        } else if (id == BUTTON_REQUEST) {
            request(player);
            closePreview();
        } else if (id == BUTTON_PREVIEW && previews()) {
            if (previewing()) {
                closePreview();
            } else {
                showPreview();
            }
        } else if (previewing() && (id == BUTTON_PREVIOUS_PAGE || id == BUTTON_NEXT_PAGE)) {
            preview.set(PREVIEW_PAGE, preview.get(PREVIEW_PAGE) + (id == BUTTON_NEXT_PAGE ? 1 : -1));
            showPreviewPage();
        } else if (id == BUTTON_PREVIOUS_PAGE || id == BUTTON_NEXT_PAGE) {
            data.set(DATA_PAGE, data.get(DATA_PAGE) + (id == BUTTON_NEXT_PAGE ? 1 : -1));
            refresh();
        } else if (id == BUTTON_CANCEL) {
            int cancelled = pipe.cancelOrders();
            if (cancelled > 0) {
                player.displayClientMessage(Component.translatable("routedpipes.request.cancelled", cancelled), false);
            }
            refresh();
        } else if (id == BUTTON_DISK && drive != null && drive.holdsDisk()) {
            drive.open(player);
        } else {
            return false;
        }
        return true;
    }

    protected void request(Player player) {
        if (selected == null) {
            return;
        }
        RequestOutcome outcome = pipe.request(new ItemBatch(selected, data.get(DATA_AMOUNT)));
        if (outcome.started()) {
            player.displayClientMessage(Component.translatable("routedpipes.request.started",
                    data.get(DATA_AMOUNT), name(selected)), false);
        } else if (!outcome.powered()) {
            player.displayClientMessage(Component.translatable("routedpipes.request.no_power")
                    .withStyle(ChatFormatting.RED), false);
        } else {
            MutableComponent missing = Component.empty();
            outcome.missing().forEach((type, count) -> missing.append("\n").append(
                    Component.translatable("routedpipes.request.missing_entry", count, name(type))));
            player.displayClientMessage(Component.translatable("routedpipes.request.missing")
                    .withStyle(ChatFormatting.RED).append(missing), false);
        }
        refresh();
    }

    private static Component name(ItemType type) {
        return McItemType.stackOf(new ItemBatch(type, 1)).getHoverName();
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
