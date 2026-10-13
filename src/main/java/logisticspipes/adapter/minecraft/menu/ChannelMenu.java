package logisticspipes.adapter.minecraft.menu;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.ChannelRegistry;
import logisticspipes.adapter.minecraft.SystemConnector;
import logisticspipes.domain.channel.Channel;
import logisticspipes.domain.channel.ChannelAccess;
import logisticspipes.domain.channel.Channels;
import logisticspipes.domain.listing.Page;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

// the channels a player may use: making, changing and deleting them
// opened on an inventory system connector it also picks the channel of that pipe and shows its resistance
// and what it waits for, opened on a security station new channels can be secured with that station
public class ChannelMenu extends AbstractContainerMenu implements TextMenu {

    public static final int BUTTON_PREVIOUS_PAGE = 0;
    public static final int BUTTON_NEXT_PAGE = 1;
    // puts the pipe on the selected channel
    public static final int BUTTON_USE = 2;
    public static final int BUTTON_DELETE = 3;
    // the next kind of access for the selected channel
    public static final int BUTTON_ACCESS = 4;
    // the next kind of access for channels that are made from now on
    public static final int BUTTON_NEW_ACCESS = 5;
    public static final int BUTTON_RESISTANCE_DOWN = 6;
    public static final int BUTTON_RESISTANCE_UP = 7;
    public static final int BUTTON_RESISTANCE_DOWN_10 = 8;
    public static final int BUTTON_RESISTANCE_UP_10 = 9;
    // makes a channel with the name
    public static final int TEXT_ADD = 0;
    // gives the selected channel the name
    public static final int TEXT_RENAME = 1;
    public static final int CHANNELS_PER_PAGE = 9;
    public static final int WAITING_SLOTS = 18;
    public static final int CURRENT_SLOT = 0;
    public static final int FIRST_CHANNEL_SLOT = 1;
    public static final int FIRST_WAITING_SLOT = FIRST_CHANNEL_SLOT + CHANNELS_PER_PAGE;
    // where the part that only a connector has begins
    public static final int CONNECTOR_TOP = 120;

    private static final int DATA_PAGE = 0;
    private static final int DATA_PAGES = 1;
    private static final int DATA_SELECTED = 2;
    private static final int DATA_OWNED = 3;
    private static final int DATA_ACCESS = 4;
    private static final int DATA_NEW_ACCESS = 5;
    private static final int DATA_RESISTANCE = 6;

    private final ContainerData data = new SimpleContainerData(7);
    private final Container current = new SimpleContainer(1);
    private final Container channels = new SimpleContainer(CHANNELS_PER_PAGE);
    private final Container waiting = new SimpleContainer(WAITING_SLOTS);
    private final boolean onPipe;
    private final Player player;
    private final SystemConnector connector;
    private final UUID station;
    private final Predicate<Player> usable;
    private List<Channel> listed = List.of();
    private UUID selected;

    // client side
    public ChannelMenu(MenuType<?> type, int id, Inventory inventory, boolean onPipe) {
        this(type, id, inventory, onPipe, null, null, player -> true);
    }

    // connector is null when this is not opened on a pipe, station when it is not opened on a security station
    public ChannelMenu(MenuType<?> type, int id, Inventory inventory, SystemConnector connector, UUID station,
            Predicate<Player> usable) {
        this(type, id, inventory, connector != null, connector, station, usable);
        refresh();
    }

    private ChannelMenu(MenuType<?> type, int id, Inventory inventory, boolean onPipe, SystemConnector connector, UUID station,
            Predicate<Player> usable) {
        super(type, id);
        this.onPipe = onPipe;
        this.player = inventory.player;
        this.connector = connector;
        this.station = station;
        this.usable = usable;
        addSlot(new MarkerSlot(current, 0, 8, CONNECTOR_TOP, () -> onPipe));
        for (int slot = 0; slot < CHANNELS_PER_PAGE; slot++) {
            addSlot(new MarkerSlot(channels, slot, 8 + slot * 18, 30));
        }
        for (int slot = 0; slot < WAITING_SLOTS; slot++) {
            addSlot(new MarkerSlot(waiting, slot, 8 + slot % 9 * 18, CONNECTOR_TOP + 50 + slot / 9 * 18, () -> onPipe));
        }
        data.set(DATA_PAGES, 1);
        data.set(DATA_SELECTED, -1);
        addDataSlots(data);
    }

    public boolean onPipe() {
        return onPipe;
    }

    public int page() {
        return data.get(DATA_PAGE);
    }

    public int pages() {
        return data.get(DATA_PAGES);
    }

    // which of the channel slots holds the selected channel, -1 when none of them does
    public int selectedSlot() {
        return data.get(DATA_SELECTED);
    }

    // whether the selected channel belongs to the player, only then it can be changed
    public boolean ownsSelected() {
        return data.get(DATA_OWNED) != 0;
    }

    public ChannelAccess selectedAccess() {
        return access(data.get(DATA_ACCESS));
    }

    public ChannelAccess newAccess() {
        return access(data.get(DATA_NEW_ACCESS));
    }

    public int resistance() {
        return data.get(DATA_RESISTANCE);
    }

    private static ChannelAccess access(int ordinal) {
        return ChannelAccess.values()[Math.floorMod(ordinal, ChannelAccess.values().length)];
    }

    private ChannelRegistry registry() {
        return ChannelRegistry.of(player.level());
    }

    private List<Channel> allowed() {
        return registry().channels().allowedFor(player.getUUID(), ChannelRegistry.stations(player.level()));
    }

    private void refresh() {
        List<Channel> allowed = allowed();
        if (selected != null && allowed.stream().noneMatch(channel -> channel.id().equals(selected))) {
            selected = null;
        }
        Page<Channel> page = Page.of(allowed, data.get(DATA_PAGE), CHANNELS_PER_PAGE);
        data.set(DATA_PAGE, page.number());
        data.set(DATA_PAGES, page.pages());
        listed = page.entries();
        data.set(DATA_SELECTED, -1);
        data.set(DATA_OWNED, 0);
        for (int slot = 0; slot < CHANNELS_PER_PAGE; slot++) {
            show(channels, slot, slot < listed.size() ? paper(listed.get(slot)) : ItemStack.EMPTY);
            if (slot < listed.size() && listed.get(slot).id().equals(selected)) {
                data.set(DATA_SELECTED, slot);
                data.set(DATA_OWNED, listed.get(slot).ownedBy(player.getUUID()) ? 1 : 0);
                data.set(DATA_ACCESS, listed.get(slot).access().ordinal());
            }
        }
        if (connector == null) {
            return;
        }
        data.set(DATA_RESISTANCE, connector.resistance());
        // also shown when the player may not use the channel the pipe is on
        show(current, 0, connector.channel().flatMap(registry().channels()::find).map(ChannelMenu::paper).orElse(ItemStack.EMPTY));
        List<ItemStack> expected = connector.expected();
        for (int slot = 0; slot < WAITING_SLOTS; slot++) {
            show(waiting, slot, slot < expected.size() ? expected.get(slot) : ItemStack.EMPTY);
        }
    }

    // only what changed is sent to the client
    private static void show(Container container, int slot, ItemStack stack) {
        if (!ItemStack.matches(container.getItem(slot), stack)) {
            container.setItem(slot, stack);
        }
    }

    // a channel is shown as a sheet of paper with its name, below that who may use it and whose it is
    private static ItemStack paper(Channel channel) {
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.ITEM_NAME, Component.literal(channel.name()));
        paper.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("routedpipes.channel.access." + channel.access().name().toLowerCase(Locale.ROOT))
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.translatable("routedpipes.channel.owner", channel.ownerName())
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
        return paper;
    }

    // other players change channels too, and what a connector waits for changes all the time
    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    private void select(UUID channel) {
        selected = channel;
        List<UUID> ids = allowed().stream().map(Channel::id).toList();
        if (ids.contains(channel)) {
            data.set(DATA_PAGE, ids.indexOf(channel) / CHANNELS_PER_PAGE);
        }
        refresh();
    }

    @Override
    public void setText(int field, String text) {
        if (player.level().isClientSide) {
            return;
        }
        Channels all = registry().channels();
        if (field == TEXT_ADD) {
            Optional<Channel> made = all.create(text, player.getUUID(), player.getGameProfile().getName(), newAccess(), station);
            made.ifPresent(channel -> {
                registry().setDirty();
                select(channel.id());
            });
        } else if (field == TEXT_RENAME && selected != null && all.rename(selected, player.getUUID(), text)) {
            registry().setDirty();
            refresh();
        }
    }

    // the kind of access after this one, securing needs a station
    private ChannelAccess next(ChannelAccess access) {
        ChannelAccess next = access;
        do {
            next = access(next.ordinal() + 1);
        } while (next == ChannelAccess.SECURED && station == null);
        return next;
    }

    // clicking a listed channel selects it, nothing is ever picked up
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        int slot = slotId - FIRST_CHANNEL_SLOT;
        if (slotId < 0 || slotId >= slots.size()) {
            super.clicked(slotId, button, clickType, player);
        } else if (!player.level().isClientSide && slot >= 0 && slot < listed.size()) {
            selected = listed.get(slot).id();
            refresh();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button < BUTTON_PREVIOUS_PAGE || button > BUTTON_RESISTANCE_UP_10) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        Channels all = registry().channels();
        if (button == BUTTON_PREVIOUS_PAGE || button == BUTTON_NEXT_PAGE) {
            data.set(DATA_PAGE, data.get(DATA_PAGE) + (button == BUTTON_NEXT_PAGE ? 1 : -1));
        } else if (button == BUTTON_NEW_ACCESS) {
            data.set(DATA_NEW_ACCESS, next(newAccess()).ordinal());
        } else if (button >= BUTTON_RESISTANCE_DOWN) {
            if (connector != null) {
                int step = button >= BUTTON_RESISTANCE_DOWN_10 ? 10 : 1;
                boolean up = button == BUTTON_RESISTANCE_UP || button == BUTTON_RESISTANCE_UP_10;
                connector.setResistance(connector.resistance() + (up ? step : -step));
            }
        } else if (selected == null) {
            return true;
        } else if (button == BUTTON_USE) {
            // selected is always one of the channels this player may use
            if (connector != null) {
                connector.setChannel(selected);
            }
        } else if (button == BUTTON_DELETE) {
            if (all.delete(selected, player.getUUID())) {
                registry().setDirty();
            }
        } else if (button == BUTTON_ACCESS) {
            Optional<Channel> channel = all.find(selected);
            if (channel.isPresent() && all.setAccess(selected, player.getUUID(), next(channel.get().access()), station)) {
                registry().setDirty();
            }
        }
        refresh();
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
