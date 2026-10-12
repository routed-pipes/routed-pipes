package logisticspipes.adapter.minecraft.menu;

import java.util.Optional;
import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.SecurityCardItem;
import logisticspipes.adapter.minecraft.UpgradeItem;
import logisticspipes.application.NetworkOverview;
import logisticspipes.application.RouterStatus;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.listing.Page;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.UpgradeKind;
import logisticspipes.domain.statistics.PipeStatistics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// what the pipe controller shows of a routed pipe, one tab at a time: its statistics, its upgrade slots,
// the slot for its security card, its open orders and the other routed pipes of its network
public class UpgradeMenu extends AbstractContainerMenu {

    // the button with the number of a tab switches to it
    public enum Tab {
        STATISTICS, UPGRADES, SECURITY, TASKS, NETWORK
    }

    // an open order as the tasks tab lists it, numbered from 1, without a target it goes to whoever takes it
    public record Task(int number, int count, Optional<BlockPos> target) {
    }

    // a routed pipe as the network tab lists it: where it is, what it moved since it was loaded and how many orders it has open
    public record PipeRow(BlockPos pos, int sent, int received, int relayed, int orders) {
    }

    public static final int BUTTON_PREVIOUS_PAGE = 16;
    public static final int BUTTON_NEXT_PAGE = 17;
    public static final int TASK_ROWS = 3;
    public static final int TASK_ROW_HEIGHT = 20;
    public static final int TASKS_TOP = 40;
    public static final int INVENTORY_TOP = 134;
    private static final int INVENTORY_SLOTS = 36;
    public static final int FIRST_TASK_SLOT = PipeBlockEntity.UPGRADE_SLOTS + INVENTORY_SLOTS;
    public static final int SECURITY_SLOT = FIRST_TASK_SLOT + TASK_ROWS;
    public static final int PIPE_ROWS = 4;
    public static final int PIPE_ROW_HEIGHT = 20;
    public static final int FIRST_PIPE_SLOT = SECURITY_SLOT + 1;
    // listed pipes carry their place and counts in here, the stack is the pipe as an item
    private static final String PIPE_TAG = "LogisticsPipesPipe";
    // the row of sneaky upgrades that is there with a combination upgrade
    public static final int FIRST_SNEAKY_SLOT = FIRST_PIPE_SLOT + PIPE_ROWS;
    public static final int SNEAKY_ROW_TOP = 88;
    // listed orders carry their number, amount and target in here, the stack count is always 1
    private static final String TASK_TAG = "LogisticsPipesTask";
    private static final int REFRESH_INTERVAL = 20;

    private static final int DATA_KIND = 0;
    private static final int DATA_TAB = 1;
    private static final int DATA_PAGE = 2;
    private static final int DATA_PAGES = 3;
    private static final int DATA_MAY_UPGRADE = 4;
    private static final int DATA_AUTHORIZED = 5;
    private static final int KINDS = PipeStatistics.Kind.values().length;
    private static final int NUMBER_TABLE_SIZE = KINDS * 2;
    private static final int NUMBER_ENERGY = NUMBER_TABLE_SIZE + 1;
    private static final int NETWORK_PAGE = 0;
    private static final int NETWORK_PAGES = 1;

    private final ContainerData data = new SimpleContainerData(6);
    private final LongData statistics;
    private final Container tasks = new SimpleContainer(TASK_ROWS);
    private final Container pipes = new SimpleContainer(PIPE_ROWS);
    private final ContainerData network = new SimpleContainerData(2);
    private int networkEnergy;
    private final PipeBlockEntity pipe;
    private final Predicate<Player> usable;
    private int tableSize;
    private int ticks;

    // client side
    public UpgradeMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(PipeBlockEntity.UPGRADE_SLOTS), new SimpleContainer(1),
                new SimpleContainer(PipeBlockEntity.UPGRADE_SLOTS), null, false, player -> true);
    }

    // mayUpgrade says whether the security station of the pipe lets this player at the upgrades
    public UpgradeMenu(MenuType<?> type, int id, Inventory inventory, PipeBlockEntity pipe, boolean mayUpgrade,
            Predicate<Player> usable) {
        this(type, id, inventory, pipe.upgrades(), pipe.security().card(), pipe.sneakyUpgrades(), pipe, mayUpgrade, usable);
    }

    private UpgradeMenu(MenuType<?> type, int id, Inventory inventory, Container upgrades, Container card, Container sneakyRow,
            PipeBlockEntity pipe, boolean mayUpgrade, Predicate<Player> usable) {
        super(type, id);
        this.pipe = pipe;
        this.usable = usable;
        for (int slot = 0; slot < PipeBlockEntity.UPGRADE_SLOTS; slot++) {
            addSlot(new Slot(upgrades, slot, 8 + slot * 18, 42) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return mayUpgrade() && stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind().fitsPipe(kind());
                }

                @Override
                public boolean mayPickup(Player player) {
                    return mayUpgrade();
                }

                @Override
                public int getMaxStackSize() {
                    return PipeBlockEntity.UPGRADE_STACK;
                }

                @Override
                public boolean isActive() {
                    return tab() == Tab.UPGRADES;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_TOP + 58));
        }
        for (int row = 0; row < TASK_ROWS; row++) {
            addSlot(new MarkerSlot(tasks, row, 8, TASKS_TOP + row * TASK_ROW_HEIGHT, () -> tab() == Tab.TASKS));
        }
        // a card goes in when its station vouches for it and never comes out again
        addSlot(new Slot(card, 0, 8, 42) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return pipe == null ? stack.getItem() instanceof SecurityCardItem : pipe.security().accepts(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return tab() == Tab.SECURITY;
            }
        });
        for (int row = 0; row < PIPE_ROWS; row++) {
            addSlot(new MarkerSlot(pipes, row, 8, TASKS_TOP + row * PIPE_ROW_HEIGHT, () -> tab() == Tab.NETWORK));
        }
        for (int slot = 0; slot < PipeBlockEntity.UPGRADE_SLOTS; slot++) {
            addSlot(new Slot(sneakyRow, slot, 8 + slot * 18, SNEAKY_ROW_TOP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return mayUpgrade() && sneakyRow() && stack.getItem() instanceof UpgradeItem upgrade
                            && upgrade.kind() == UpgradeKind.SNEAKY;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return mayUpgrade();
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return tab() == Tab.UPGRADES && sneakyRow();
                }
            });
        }
        data.set(DATA_PAGES, 1);
        network.set(NETWORK_PAGES, 1);
        data.set(DATA_MAY_UPGRADE, mayUpgrade ? 1 : 0);
        if (pipe == null) {
            statistics = new LongData(NUMBER_ENERGY + 1);
        } else {
            // the client needs the kind of pipe to know which upgrades fit
            data.set(DATA_KIND, pipe.kind().ordinal());
            PipeStatistics counts = pipe.statistics();
            statistics = new LongData(
                    () -> counts.session(PipeStatistics.Kind.SENT),
                    () -> counts.session(PipeStatistics.Kind.RECEIVED),
                    () -> counts.session(PipeStatistics.Kind.RELAYED),
                    () -> counts.lifetime(PipeStatistics.Kind.SENT),
                    () -> counts.lifetime(PipeStatistics.Kind.RECEIVED),
                    () -> counts.lifetime(PipeStatistics.Kind.RELAYED),
                    () -> tableSize,
                    () -> networkEnergy);
            refresh();
        }
        addDataSlots(data);
        addDataSlots(statistics);
        addDataSlots(network);
    }

    private PipeKind kind() {
        return PipeKind.values()[Math.floorMod(data.get(DATA_KIND), PipeKind.values().length)];
    }

    public Tab tab() {
        return Tab.values()[Math.floorMod(data.get(DATA_TAB), Tab.values().length)];
    }

    // of the list on the tab that is open
    public int page() {
        return tab() == Tab.NETWORK ? network.get(NETWORK_PAGE) : data.get(DATA_PAGE);
    }

    public boolean mayUpgrade() {
        return data.get(DATA_MAY_UPGRADE) != 0;
    }

    // whether a combination upgrade is in the pipe, both sides see that from the upgrade slots
    public boolean sneakyRow() {
        for (int slot = 0; slot < PipeBlockEntity.UPGRADE_SLOTS; slot++) {
            if (slots.get(slot).getItem().getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeKind.SNEAKY_COMBINATION) {
                return true;
            }
        }
        return false;
    }

    // without the combination upgrade its row is gone, what was left in it goes back to the player
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (pipe != null && !sneakyRow()) {
            pipe.removeSneakyRow().forEach(player.getInventory()::placeItemBackInInventory);
        }
    }

    // whether the station of the card in the pipe still vouches for it
    public boolean cardAuthorized() {
        return data.get(DATA_AUTHORIZED) != 0;
    }

    public int pages() {
        return tab() == Tab.NETWORK ? network.get(NETWORK_PAGES) : data.get(DATA_PAGES);
    }

    // what the junctions of the network hold, as of the last look at the network tab
    public long networkEnergy() {
        return statistics.value(NUMBER_ENERGY);
    }

    // empty for a stack that is not a listed pipe
    public static Optional<PipeRow> pipeRowOf(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(PIPE_TAG)) {
            return Optional.empty();
        }
        CompoundTag row = tag.getCompound(PIPE_TAG);
        return Optional.of(new PipeRow(BlockPos.of(row.getLong("Pos")), row.getInt("Sent"), row.getInt("Received"),
                row.getInt("Relayed"), row.getInt("Orders")));
    }

    public long session(PipeStatistics.Kind kind) {
        return statistics.value(kind.ordinal());
    }

    public long lifetime(PipeStatistics.Kind kind) {
        return statistics.value(KINDS + kind.ordinal());
    }

    // how many other routed pipes this one knows a way to
    public long routingTableSize() {
        return statistics.value(NUMBER_TABLE_SIZE);
    }

    // empty for a stack that is not a listed order
    public static Optional<Task> taskOf(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TASK_TAG)) {
            return Optional.empty();
        }
        CompoundTag task = tag.getCompound(TASK_TAG);
        Optional<BlockPos> target = task.contains("Target") ? Optional.of(BlockPos.of(task.getLong("Target"))) : Optional.empty();
        return Optional.of(new Task(task.getInt("Number"), task.getInt("Count"), target));
    }

    private void refresh() {
        tableSize = pipe.networkStatus().map(RouterStatus::routers).orElse(0);
        data.set(DATA_AUTHORIZED, pipe.security().authorized() ? 1 : 0);
        Page<OutputOrder> page = Page.of(pipe.outputOrders().all(), data.get(DATA_PAGE), TASK_ROWS);
        data.set(DATA_PAGE, page.number());
        data.set(DATA_PAGES, page.pages());
        for (int row = 0; row < TASK_ROWS; row++) {
            tasks.setItem(row, row < page.entries().size()
                    ? taskStack(page.entries().get(row), page.number() * TASK_ROWS + row + 1) : ItemStack.EMPTY);
        }
        if (tab() == Tab.NETWORK) {
            refreshNetwork();
        }
    }

    // only looked up while its tab is open, a large network has a lot of pipes
    private void refreshNetwork() {
        NetworkOverview overview = pipe.networkOverview();
        networkEnergy = overview.energy();
        Page<NetworkOverview.Entry> page = Page.of(overview.pipes(), network.get(NETWORK_PAGE), PIPE_ROWS);
        network.set(NETWORK_PAGE, page.number());
        network.set(NETWORK_PAGES, page.pages());
        for (int row = 0; row < PIPE_ROWS; row++) {
            pipes.setItem(row, row < page.entries().size() ? pipeStack(page.entries().get(row)) : ItemStack.EMPTY);
        }
    }

    private ItemStack pipeStack(NetworkOverview.Entry entry) {
        Optional<BlockPos> pos = pipe.positionOf(entry.router());
        if (pos.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(pipe.getLevel().getBlockState(pos.get()).getBlock());
        CompoundTag row = new CompoundTag();
        row.putLong("Pos", pos.get().asLong());
        row.putInt("Sent", entry.sent());
        row.putInt("Received", entry.received());
        row.putInt("Relayed", entry.relayed());
        row.putInt("Orders", entry.openOrders());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(PIPE_TAG, row));
        return stack;
    }

    private ItemStack taskStack(OutputOrder order, int number) {
        ItemStack stack = McItemType.stackOf(new ItemBatch(order.batch().type(), 1));
        CompoundTag task = new CompoundTag();
        task.putInt("Number", number);
        task.putInt("Count", order.batch().count());
        if (!order.isExtra()) {
            pipe.positionOf(order.destination()).ifPresent(target -> task.putLong("Target", target.asLong()));
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TASK_TAG, task));
        return stack;
    }

    @Override
    public void broadcastChanges() {
        if (pipe != null && ++ticks % REFRESH_INTERVAL == 0) {
            refresh();
        }
        super.broadcastChanges();
    }

    // runs on the client too, so that the slots of a tab are there before the server has answered
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < Tab.values().length) {
            data.set(DATA_TAB, id);
        } else if (tab() == Tab.NETWORK && (id == BUTTON_PREVIOUS_PAGE || id == BUTTON_NEXT_PAGE)) {
            // only the server knows how many pages there are, a page the client made up would stick
            if (pipe != null) {
                network.set(NETWORK_PAGE, network.get(NETWORK_PAGE) + (id == BUTTON_NEXT_PAGE ? 1 : -1));
            }
        } else if (id == BUTTON_PREVIOUS_PAGE || id == BUTTON_NEXT_PAGE) {
            data.set(DATA_PAGE, data.get(DATA_PAGE) + (id == BUTTON_NEXT_PAGE ? 1 : -1));
        } else {
            return false;
        }
        if (pipe != null) {
            refresh();
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= FIRST_TASK_SLOT && index < FIRST_SNEAKY_SLOT) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < PipeBlockEntity.UPGRADE_SLOTS || index >= FIRST_SNEAKY_SLOT) {
            moved = moveItemStackTo(stack, PipeBlockEntity.UPGRADE_SLOTS, FIRST_TASK_SLOT, true);
        } else if (tab() == Tab.SECURITY) {
            moved = moveItemStackTo(stack, SECURITY_SLOT, SECURITY_SLOT + 1, false);
        } else {
            // upgrades only go in while their slots are on screen
            // sneaky upgrades go into the row of the combination upgrade first, its slots refuse everything else
            moved = tab() == Tab.UPGRADES && (moveItemStackTo(stack, FIRST_SNEAKY_SLOT, slots.size(), false)
                    || moveItemStackTo(stack, 0, PipeBlockEntity.UPGRADE_SLOTS, false));
        }
        if (!moved) {
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
        return usable.test(player);
    }
}
