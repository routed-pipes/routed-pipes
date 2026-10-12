package logisticspipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.DiskDrive;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.application.RequestOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
    // shows the disk of a pipe that takes one, a click on it throws the disk out
    public static final int DISK_SLOT = PAGE_SIZE;
    public static final int MAX_AMOUNT = 9999;
    public static final int TEXT_SEARCH = 0;
    // listed stacks carry their network wide amount in here, the stack count is always 1
    private static final String COUNT_TAG = "LogisticsPipesCount";
    private static final int REFRESH_INTERVAL = 20;

    private static final int DATA_SELECTED = 0;
    private static final int DATA_AMOUNT = 1;
    private static final int DATA_PAGE = 2;
    private static final int DATA_PAGES = 3;
    private static final int DATA_TAKES_DISK = 4;

    private final Container listing = new SimpleContainer(PAGE_SIZE);
    private final ContainerData data = new SimpleContainerData(5);
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
            addSlot(new MarkerSlot(listing, slot, 8 + slot % COLUMNS * 18, 18 + slot / COLUMNS * 18));
        }
        addSlot(new MarkerSlot(drive == null ? new SimpleContainer(1) : drive.slot(), 0, 152, 162, this::takesDisk));
        data.set(DATA_SELECTED, -1);
        data.set(DATA_AMOUNT, 1);
        data.set(DATA_PAGES, 1);
        data.set(DATA_TAKES_DISK, drive == null ? 0 : 1);
        addDataSlots(data);
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

    public int page() {
        return data.get(DATA_PAGE);
    }

    public int pages() {
        return data.get(DATA_PAGES);
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
        } else if (id == BUTTON_REQUEST) {
            request(player);
        } else if (id == BUTTON_PREVIOUS_PAGE || id == BUTTON_NEXT_PAGE) {
            data.set(DATA_PAGE, data.get(DATA_PAGE) + (id == BUTTON_NEXT_PAGE ? 1 : -1));
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
            player.displayClientMessage(Component.translatable("logisticspipes.request.started",
                    data.get(DATA_AMOUNT), name(selected)), false);
        } else if (!outcome.powered()) {
            player.displayClientMessage(Component.translatable("logisticspipes.request.no_power")
                    .withStyle(ChatFormatting.RED), false);
        } else {
            MutableComponent missing = Component.empty();
            outcome.missing().forEach((type, count) -> missing.append("\n").append(
                    Component.translatable("logisticspipes.request.missing_entry", count, name(type))));
            player.displayClientMessage(Component.translatable("logisticspipes.request.missing")
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
