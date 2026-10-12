package logisticspipes.adapter.minecraft.menu;

import java.util.List;
import java.util.UUID;

import logisticspipes.adapter.minecraft.SecurityStationBlockEntity;
import logisticspipes.adapter.minecraft.SecurityStations;
import logisticspipes.domain.listing.Page;
import logisticspipes.domain.security.Permission;
import logisticspipes.domain.security.SecuritySettings;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// the security station: one tab for the station and its cards, one for the players and what they may do
public class SecurityStationMenu extends AbstractContainerMenu implements TextMenu {

    // the button with the number of a tab switches to it
    public enum Tab {
        STATION, PLAYERS
    }

    public static final int BUTTON_AUTHORIZED = 2;
    public static final int BUTTON_AUTOMATED_REMOVAL = 3;
    public static final int BUTTON_ADD_CARD = 4;
    public static final int BUTTON_REMOVE_CARD = 5;
    public static final int BUTTON_PREVIOUS_PAGE = 6;
    public static final int BUTTON_NEXT_PAGE = 7;
    // plus the number of the permission, switches it for the selected player
    public static final int BUTTON_PERMISSION = 8;
    // opens the channels of the world
    public static final int BUTTON_CHANNELS = 100;
    public static final int TEXT_ADD_PLAYER = 0;
    public static final int PLAYERS_PER_PAGE = 9;
    public static final int CARD_SLOT = 0;
    public static final int FIRST_PLAYER_SLOT = 37;
    public static final int INVENTORY_TOP = 140;

    private static final int DATA_TAB = 0;
    private static final int DATA_AUTHORIZED = 1;
    private static final int DATA_AUTOMATED_REMOVAL = 2;
    private static final int DATA_PAGE = 3;
    private static final int DATA_PAGES = 4;
    private static final int DATA_SELECTED = 5;
    private static final int DATA_PERMISSIONS = 6;

    private final ContainerData data = new SimpleContainerData(7);
    // the two halves of the id of the station
    private final LongData id;
    private final Container players = new SimpleContainer(PLAYERS_PER_PAGE);
    private final SecurityStationBlockEntity station;
    private List<UUID> listed = List.of();
    private UUID selected;

    // client side
    public SecurityStationMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(1), null);
    }

    public SecurityStationMenu(MenuType<?> type, int id, Inventory inventory, SecurityStationBlockEntity station) {
        this(type, id, inventory, station.cards(), station);
    }

    private SecurityStationMenu(MenuType<?> type, int containerId, Inventory inventory, Container cards,
            SecurityStationBlockEntity station) {
        super(type, containerId);
        this.station = station;
        // cards only come out of here
        addSlot(new Slot(cards, 0, 56, 107) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean isActive() {
                return tab() == Tab.STATION;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(inventorySlot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(inventorySlot(inventory, column, 8 + column * 18, INVENTORY_TOP + 58));
        }
        for (int slot = 0; slot < PLAYERS_PER_PAGE; slot++) {
            addSlot(new MarkerSlot(players, slot, 8 + slot * 18, 42, () -> tab() == Tab.PLAYERS));
        }
        data.set(DATA_PAGES, 1);
        data.set(DATA_SELECTED, -1);
        if (station == null) {
            id = new LongData(2);
        } else {
            UUID stationId = station.id();
            id = new LongData(stationId::getMostSignificantBits, stationId::getLeastSignificantBits);
            refresh();
        }
        addDataSlots(data);
        addDataSlots(id);
    }

    // the players tab needs the room of the inventory for itself
    private Slot inventorySlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean isActive() {
                return tab() == Tab.STATION;
            }
        };
    }

    public Tab tab() {
        return Tab.values()[Math.floorMod(data.get(DATA_TAB), Tab.values().length)];
    }

    public UUID stationId() {
        return new UUID(id.value(0), id.value(1));
    }

    public boolean authorized() {
        return data.get(DATA_AUTHORIZED) != 0;
    }

    public boolean automatedRemoval() {
        return data.get(DATA_AUTOMATED_REMOVAL) != 0;
    }

    public int page() {
        return data.get(DATA_PAGE);
    }

    public int pages() {
        return data.get(DATA_PAGES);
    }

    // which of the player slots holds the selected player, -1 when none of them does
    public int selectedSlot() {
        return data.get(DATA_SELECTED);
    }

    // what the selected player may do
    public boolean allowed(Permission permission) {
        return (data.get(DATA_PERMISSIONS) & 1 << permission.ordinal()) != 0;
    }

    private SecurityStations.Station record() {
        return station.station();
    }

    private void refresh() {
        SecurityStations.Station record = record();
        SecuritySettings settings = record.settings();
        data.set(DATA_AUTHORIZED, settings.authorized() ? 1 : 0);
        data.set(DATA_AUTOMATED_REMOVAL, settings.automatedRemoval() ? 1 : 0);
        Page<UUID> page = Page.of(settings.players(), data.get(DATA_PAGE), PLAYERS_PER_PAGE);
        data.set(DATA_PAGE, page.number());
        data.set(DATA_PAGES, page.pages());
        listed = page.entries();
        for (int slot = 0; slot < PLAYERS_PER_PAGE; slot++) {
            players.setItem(slot, slot < listed.size() ? head(record.name(listed.get(slot))) : ItemStack.EMPTY);
        }
        data.set(DATA_SELECTED, selected == null ? -1 : listed.indexOf(selected));
        int permissions = 0;
        if (selected != null) {
            for (Permission permission : settings.permissions(selected)) {
                permissions |= 1 << permission.ordinal();
            }
        }
        data.set(DATA_PERMISSIONS, permissions);
    }

    // a listed player is shown as a head with their name
    private static ItemStack head(String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.ITEM_NAME, Component.literal(name));
        return head;
    }

    private void changed() {
        SecurityStations.of(station.getLevel()).setDirty();
        refresh();
    }

    // puts a player on the list and selects them
    public void addPlayer(UUID player, String name) {
        SecurityStations.Station record = record();
        record.add(player, name);
        selected = player;
        data.set(DATA_PAGE, record.settings().players().indexOf(player) / PLAYERS_PER_PAGE);
        changed();
    }

    // the name typed into the players tab, only someone who is online can be looked up
    @Override
    public void setText(int field, String text) {
        if (station == null || field != TEXT_ADD_PLAYER) {
            return;
        }
        Player found = station.getLevel().getServer().getPlayerList().getPlayerByName(text.strip());
        if (found != null) {
            addPlayer(found.getUUID(), found.getGameProfile().getName());
        }
    }

    // clicking a listed player selects them, nothing is ever picked up
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        int slot = slotId - FIRST_PLAYER_SLOT;
        if (slot < 0 || slot >= PLAYERS_PER_PAGE) {
            super.clicked(slotId, button, clickType, player);
        } else if (station != null && slot < listed.size()) {
            selected = listed.get(slot);
            refresh();
        }
    }

    // runs on the client too, so that the slots of a tab are there before the server has answered
    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button >= 0 && button < Tab.values().length) {
            data.set(DATA_TAB, button);
            return true;
        }
        if (button == BUTTON_CHANNELS) {
            if (station != null) {
                player.openMenu(station.channelMenuProvider());
            }
            return true;
        }
        if (button < BUTTON_AUTHORIZED || button >= BUTTON_PERMISSION + Permission.values().length) {
            return false;
        }
        if (station == null) {
            return true;
        }
        SecuritySettings settings = record().settings();
        if (button == BUTTON_AUTHORIZED) {
            settings.setAuthorized(!settings.authorized());
        } else if (button == BUTTON_AUTOMATED_REMOVAL) {
            settings.setAutomatedRemoval(!settings.automatedRemoval());
        } else if (button == BUTTON_ADD_CARD) {
            if (station.hasRoomForCard() && !station.makeCard()) {
                player.displayClientMessage(Component.translatable("logisticspipes.security.no_energy")
                        .withStyle(ChatFormatting.RED), false);
            }
        } else if (button == BUTTON_REMOVE_CARD) {
            station.cards().removeItem(0, 1);
        } else if (button == BUTTON_PREVIOUS_PAGE || button == BUTTON_NEXT_PAGE) {
            data.set(DATA_PAGE, data.get(DATA_PAGE) + (button == BUTTON_NEXT_PAGE ? 1 : -1));
        } else if (selected != null) {
            Permission permission = Permission.values()[button - BUTTON_PERMISSION];
            settings.set(selected, permission, !settings.permissions(selected).contains(permission));
        }
        changed();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index != CARD_SLOT || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, CARD_SLOT + 1, FIRST_PLAYER_SLOT, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return station == null || Container.stillValidBlockEntity(station, player);
    }
}
