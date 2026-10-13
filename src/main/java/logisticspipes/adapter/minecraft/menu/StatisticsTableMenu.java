package logisticspipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.StatisticsTableBlockEntity;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.listing.Page;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// the statistics table: the tracked items with the history of the selected one, the list an item to track is picked from,
// and what the crafters of the network still have to make
public class StatisticsTableMenu extends AbstractContainerMenu implements TextMenu {

    // the first two are tabs and the button with their number switches to them, the last one is opened by the add button
    public enum View {
        AMOUNTS, TASKS, ADD
    }

    public static final int BUTTON_ADD = 2;
    public static final int BUTTON_REMOVE = 3;
    public static final int BUTTON_PREVIOUS_ITEMS = 4;
    public static final int BUTTON_NEXT_ITEMS = 5;
    public static final int BUTTON_PREVIOUS_PAGE = 6;
    public static final int BUTTON_NEXT_PAGE = 7;
    // tracks the item picked in the list
    public static final int BUTTON_CONFIRM = 8;
    // looks the list up again, on the tasks tab that is what fetches the tasks
    public static final int BUTTON_REFRESH = 9;
    public static final int TEXT_SEARCH = 0;
    public static final int TRACKED_SLOTS = 9;
    public static final int SELECTED_SLOT = TRACKED_SLOTS;
    public static final int FIRST_LIST_SLOT = SELECTED_SLOT + 1;
    public static final int LIST_COLUMNS = 9;
    public static final int LIST_ROWS = 7;
    public static final int LIST_SLOTS = LIST_COLUMNS * LIST_ROWS;
    // the selected item carries its history in here, oldest sample first
    private static final String SAMPLES_TAG = "LogisticsPipesSamples";
    private static final int REFRESH_INTERVAL = 20;

    private static final int DATA_VIEW = 0;
    private static final int DATA_ITEMS_PAGE = 1;
    private static final int DATA_ITEMS_PAGES = 2;
    private static final int DATA_SELECTED = 3;
    private static final int DATA_LIST_PAGE = 4;
    private static final int DATA_LIST_PAGES = 5;
    private static final int DATA_PICKED = 6;
    private static final int DATA_HAS_PIPE = 7;

    private final ContainerData data = new SimpleContainerData(8);
    private final Container tracked = new SimpleContainer(TRACKED_SLOTS);
    private final Container selectedItem = new SimpleContainer(1);
    private final Container list = new SimpleContainer(LIST_SLOTS);
    private final StatisticsTableBlockEntity table;
    private List<ItemType> shownItems = List.of();
    private List<ItemType> shownList = List.of();
    private Map<ItemType, Integer> tasks = Map.of();
    private ItemType selected;
    private ItemType picked;
    private String search = "";
    private int ticks;

    // client side
    public StatisticsTableMenu(MenuType<?> type, int id) {
        this(type, id, null);
    }

    public StatisticsTableMenu(MenuType<?> type, int id, StatisticsTableBlockEntity table) {
        super(type, id);
        this.table = table;
        for (int slot = 0; slot < TRACKED_SLOTS; slot++) {
            addSlot(new MarkerSlot(tracked, slot, 8 + slot * 18, 42, () -> view() == View.AMOUNTS));
        }
        addSlot(new MarkerSlot(selectedItem, 0, 8, 86, () -> view() == View.AMOUNTS));
        for (int slot = 0; slot < LIST_SLOTS; slot++) {
            addSlot(new MarkerSlot(list, slot, 8 + slot % LIST_COLUMNS * 18, 64 + slot / LIST_COLUMNS * 18,
                    () -> view() != View.AMOUNTS));
        }
        data.set(DATA_ITEMS_PAGES, 1);
        data.set(DATA_LIST_PAGES, 1);
        data.set(DATA_SELECTED, -1);
        data.set(DATA_PICKED, -1);
        addDataSlots(data);
        if (table != null) {
            refresh();
        }
    }

    public View view() {
        return View.values()[Math.floorMod(data.get(DATA_VIEW), View.values().length)];
    }

    public int itemsPage() {
        return data.get(DATA_ITEMS_PAGE);
    }

    public int itemsPages() {
        return data.get(DATA_ITEMS_PAGES);
    }

    // which of the tracked slots holds the selected item, -1 when none of them does
    public int selectedSlot() {
        return data.get(DATA_SELECTED);
    }

    public int listPage() {
        return data.get(DATA_LIST_PAGE);
    }

    public int listPages() {
        return data.get(DATA_LIST_PAGES);
    }

    // which of the list slots holds the item picked for tracking, -1 when none of them does
    public int pickedSlot() {
        return data.get(DATA_PICKED);
    }

    // false while there is no routed pipe next to the table
    public boolean hasPipe() {
        return data.get(DATA_HAS_PIPE) != 0;
    }

    // the history of the selected item, oldest sample first
    public long[] samples() {
        return selectedItem.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongArray(SAMPLES_TAG);
    }

    private static ItemStack plain(ItemType type) {
        return McItemType.stackOf(new ItemBatch(type, 1));
    }

    private void refresh() {
        Optional<PipeBlockEntity> pipe = table.pipe();
        data.set(DATA_HAS_PIPE, pipe.isPresent() ? 1 : 0);
        Page<ItemType> items = Page.of(table.tracking().items(), data.get(DATA_ITEMS_PAGE), TRACKED_SLOTS);
        data.set(DATA_ITEMS_PAGE, items.number());
        data.set(DATA_ITEMS_PAGES, items.pages());
        shownItems = items.entries();
        for (int slot = 0; slot < TRACKED_SLOTS; slot++) {
            tracked.setItem(slot, slot < shownItems.size() ? plain(shownItems.get(slot)) : ItemStack.EMPTY);
        }
        ItemStack shown = ItemStack.EMPTY;
        if (selected != null && table.tracking().history(selected).isPresent()) {
            long[] samples = table.tracking().history(selected).get().oldestFirst();
            shown = plain(selected);
            CustomData.update(DataComponents.CUSTOM_DATA, shown, tag -> tag.putLongArray(SAMPLES_TAG, samples));
        } else {
            selected = null;
        }
        selectedItem.setItem(0, shown);
        data.set(DATA_SELECTED, selected == null ? -1 : shownItems.indexOf(selected));
        refreshList(pipe);
    }

    // the add list offers everything the network has or can craft, the tasks tab what was fetched last
    private void refreshList(Optional<PipeBlockEntity> pipe) {
        List<Map.Entry<ItemType, Integer>> entries = new ArrayList<>();
        if (view() == View.TASKS) {
            entries.addAll(tasks.entrySet());
        } else if (view() == View.ADD && pipe.isPresent()) {
            Map<ItemType, Integer> offered = new LinkedHashMap<>(pipe.get().available());
            offered.putAll(pipe.get().amounts());
            entries.addAll(offered.entrySet());
            entries.removeIf(entry -> !matchesSearch(entry.getKey()));
        }
        entries.sort(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(((McItemType) entry.getKey()).item())));
        Page<Map.Entry<ItemType, Integer>> page = Page.of(entries, data.get(DATA_LIST_PAGE), LIST_SLOTS);
        data.set(DATA_LIST_PAGE, page.number());
        data.set(DATA_LIST_PAGES, page.pages());
        List<ItemType> types = new ArrayList<>();
        for (int slot = 0; slot < LIST_SLOTS; slot++) {
            if (slot < page.entries().size()) {
                Map.Entry<ItemType, Integer> entry = page.entries().get(slot);
                list.setItem(slot, RequestMenu.listed(entry.getKey(), entry.getValue()));
                types.add(entry.getKey());
            } else {
                list.setItem(slot, ItemStack.EMPTY);
            }
        }
        shownList = types;
        data.set(DATA_PICKED, picked == null || view() != View.ADD ? -1 : shownList.indexOf(picked));
    }

    // by display name or item id, like the request screen
    private boolean matchesSearch(ItemType type) {
        if (search.isEmpty()) {
            return true;
        }
        return plain(type).getHoverName().getString().toLowerCase(Locale.ROOT).contains(search)
                || BuiltInRegistries.ITEM.getKey(((McItemType) type).item()).toString().contains(search);
    }

    @Override
    public void setText(int field, String text) {
        if (table == null || field != TEXT_SEARCH) {
            return;
        }
        search = text.strip().toLowerCase(Locale.ROOT);
        data.set(DATA_LIST_PAGE, 0);
        refresh();
    }

    @Override
    public void broadcastChanges() {
        if (table != null && ++ticks % REFRESH_INTERVAL == 0) {
            refresh();
        }
        super.broadcastChanges();
    }

    // clicking a tracked item selects it, clicking one in the add list picks it, nothing is ever picked up
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (table == null || slotId < 0) {
            return;
        }
        if (slotId < shownItems.size()) {
            selected = shownItems.get(slotId);
        } else if (view() == View.ADD && slotId >= FIRST_LIST_SLOT && slotId - FIRST_LIST_SLOT < shownList.size()) {
            picked = shownList.get(slotId - FIRST_LIST_SLOT);
        }
        refresh();
    }

    // runs on the client too, so that the slots of a view are there before the server has answered
    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button < 0 || button > BUTTON_REFRESH) {
            return false;
        }
        if (button <= BUTTON_ADD) {
            data.set(DATA_VIEW, button);
            data.set(DATA_LIST_PAGE, 0);
            picked = null;
        }
        if (table == null) {
            return true;
        }
        if (button == BUTTON_REMOVE && selected != null) {
            table.untrack(selected);
        } else if (button == BUTTON_PREVIOUS_ITEMS || button == BUTTON_NEXT_ITEMS) {
            data.set(DATA_ITEMS_PAGE, data.get(DATA_ITEMS_PAGE) + (button == BUTTON_NEXT_ITEMS ? 1 : -1));
        } else if (button == BUTTON_PREVIOUS_PAGE || button == BUTTON_NEXT_PAGE) {
            data.set(DATA_LIST_PAGE, data.get(DATA_LIST_PAGE) + (button == BUTTON_NEXT_PAGE ? 1 : -1));
        } else if (button == BUTTON_CONFIRM && picked != null) {
            track(player);
        } else if (button == BUTTON_REFRESH && view() == View.TASKS) {
            tasks = table.pipe().map(PipeBlockEntity::craftingBacklog).orElse(Map.of());
        }
        refresh();
        return true;
    }

    // back to the amounts with the new item selected, or a word about why it was not added
    private void track(Player player) {
        if (table.track(picked)) {
            selected = picked;
            data.set(DATA_VIEW, View.AMOUNTS.ordinal());
            data.set(DATA_ITEMS_PAGE, table.tracking().items().indexOf(picked) / TRACKED_SLOTS);
        } else {
            player.displayClientMessage(Component.translatable(table.pipe().isPresent()
                    ? "routedpipes.statistics.already_tracked" : "routedpipes.statistics.no_pipe")
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return table == null || Container.stillValidBlockEntity(table, player);
    }
}
