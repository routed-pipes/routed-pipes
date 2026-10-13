package routedpipes.adapter.neoforge.gametest;

import java.util.Arrays;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.StatisticsTableBlockEntity;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.minecraft.menu.StatisticsTableMenu;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.statistics.AmountHistory;
import routedpipes.domain.statistics.AmountTracking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class StatisticsTableGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;
    private static final BlockPos PIPE = new BlockPos(0, 1, 1);
    private static final BlockPos CHEST = new BlockPos(0, 2, 1);
    // next to the pipe
    private static final BlockPos TABLE = new BlockPos(0, 1, 0);

    private static ItemType type(Item item) {
        return McItemType.batchOf(new ItemStack(item)).type();
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, ItemStack... contents) {
        helper.setBlock(PIPE, RoutedPipes.pipe(kind));
        helper.setBlock(CHEST, Blocks.CHEST);
        Container container = helper.getBlockEntity(CHEST);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return helper.getBlockEntity(PIPE);
    }

    private static StatisticsTableBlockEntity table(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RoutedPipes.STATISTICS_TABLE.get());
        return helper.getBlockEntity(pos);
    }

    // lets a minute pass for the table at once
    private static void passAMinute(GameTestHelper helper, StatisticsTableBlockEntity table) {
        BlockState state = table.getBlockState();
        BlockEntityTicker<StatisticsTableBlockEntity> ticker = state.getTicker(helper.getLevel(),
                RoutedPipes.STATISTICS_TABLE_BLOCK_ENTITY.get());
        for (int tick = 0; tick < AmountTracking.INTERVAL; tick++) {
            ticker.tick(helper.getLevel(), table.getBlockPos(), state, table);
        }
    }

    private static long[] samples(StatisticsTableBlockEntity table, Item item) {
        return table.tracking().history(type(item)).orElseThrow().oldestFirst();
    }

    private static StatisticsTableMenu open(GameTestHelper helper, TestPlayer player) {
        BlockPos pos = helper.absolutePos(TABLE);
        helper.getBlockState(TABLE).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return (StatisticsTableMenu) player.containerMenu;
    }

    @GameTest(template = EMPTY)
    public static void tableWritesDownOnceAMinuteWhatItsNetworkHas(GameTestHelper helper) {
        pipe(helper, PipeKind.PROVIDER, new ItemStack(Items.COBBLESTONE, 64));
        StatisticsTableBlockEntity table = table(helper, TABLE);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(table.track(type(Items.COBBLESTONE)), "tracking should start");
            helper.assertTrue(Arrays.equals(samples(table, Items.COBBLESTONE), new long[] {64}),
                    "the amount at the start is the first sample, the chest of the pipe next to the table counts");
            Container chest = helper.getBlockEntity(CHEST);
            chest.removeItem(0, 14);
        });
        // the chest is looked into again after a moment
        helper.runAfterDelay(SETTLE + 3, () -> {
            passAMinute(helper, table);
            helper.assertTrue(Arrays.equals(samples(table, Items.COBBLESTONE), new long[] {64, 50}),
                    "a minute later there is a second sample: " + Arrays.toString(samples(table, Items.COBBLESTONE)));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void tableWithoutAPipeWritesNothingDown(GameTestHelper helper) {
        StatisticsTableBlockEntity table = table(helper, TABLE);

        helper.assertTrue(!table.track(type(Items.COBBLESTONE)) && table.tracking().items().isEmpty(), "nothing to track without a pipe");
        table.tracking().track(type(Items.COBBLESTONE), 7);
        passAMinute(helper, table);
        helper.assertTrue(Arrays.equals(samples(table, Items.COBBLESTONE), new long[] {7}), "and no samples either");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void historySurvivesSaveAndLoad(GameTestHelper helper) {
        StatisticsTableBlockEntity original = table(helper, TABLE);
        original.tracking().restore(type(Items.COBBLESTONE), AmountHistory.of(new long[] {1, 5_000_000_000L, 3}));
        original.tracking().restore(type(Items.DIAMOND), AmountHistory.of(new long[] {9}));
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        StatisticsTableBlockEntity loaded = table(helper, TABLE.east(3));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.tracking().items().equals(original.tracking().items()), "the tracked items in their order");
        helper.assertTrue(Arrays.equals(samples(loaded, Items.COBBLESTONE), new long[] {1, 5_000_000_000L, 3}), "with their samples");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemsAreAddedFromAListOfWhatTheNetworkHas(GameTestHelper helper) {
        pipe(helper, PipeKind.PROVIDER, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIAMOND, 3));
        StatisticsTableBlockEntity table = table(helper, TABLE);
        TestPlayer player = TestPlayer.in(helper);
        int list = StatisticsTableMenu.FIRST_LIST_SLOT;

        helper.runAfterDelay(SETTLE, () -> {
            StatisticsTableMenu menu = open(helper, player);
            helper.assertTrue(menu.hasPipe() && menu.view() == StatisticsTableMenu.View.AMOUNTS, "the table opens on the amounts");
            helper.assertTrue(menu.getSlot(0).isActive() && !menu.getSlot(list).isActive(), "with the tracked items and no list");

            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_ADD);
            helper.assertTrue(menu.view() == StatisticsTableMenu.View.ADD && menu.getSlot(list).isActive(), "the add button opens the list");
            helper.assertTrue(menu.getSlot(list).getItem().is(Items.COBBLESTONE) && RequestMenu.listedCount(menu.getSlot(list).getItem()) == 64
                    && menu.getSlot(list + 1).getItem().is(Items.DIAMOND), "which has what the network has, got "
                    + menu.getSlot(list).getItem() + " and " + menu.getSlot(list + 1).getItem() + ", the pipe has "
                    + ((PipeBlockEntity) helper.getBlockEntity(PIPE)).amounts());
            menu.setText(StatisticsTableMenu.TEXT_SEARCH, "diam");
            helper.assertTrue(menu.getSlot(list).getItem().is(Items.DIAMOND) && menu.getSlot(list + 1).getItem().isEmpty(),
                    "and can be searched");
            menu.setText(StatisticsTableMenu.TEXT_SEARCH, "");

            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_CONFIRM);
            helper.assertTrue(table.tracking().items().isEmpty(), "nothing is added before an item was picked");
            menu.clicked(list, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.pickedSlot() == 0, "a click picks an item");
            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_CONFIRM);
            helper.assertTrue(table.tracking().items().equals(java.util.List.of(type(Items.COBBLESTONE))), "the picked item is tracked");
            helper.assertTrue(menu.view() == StatisticsTableMenu.View.AMOUNTS && menu.selectedSlot() == 0
                    && menu.getSlot(0).getItem().is(Items.COBBLESTONE), "and selected on the amounts tab");
            helper.assertTrue(Arrays.equals(menu.samples(), new long[] {64}), "with its history: " + Arrays.toString(menu.samples()));

            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_ADD);
            menu.clicked(list, 0, ClickType.PICKUP, player);
            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_CONFIRM);
            helper.assertTrue(table.tracking().items().size() == 1 && menu.view() == StatisticsTableMenu.View.ADD,
                    "an item is not tracked twice");

            menu.clickMenuButton(player, StatisticsTableMenu.View.AMOUNTS.ordinal());
            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_REMOVE);
            helper.assertTrue(table.tracking().items().isEmpty() && menu.selectedSlot() == -1 && menu.samples().length == 0,
                    "the remove button stops tracking the selected item");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void trackedItemsArePagedAndBothPageButtonsWork(GameTestHelper helper) {
        pipe(helper, PipeKind.PROVIDER);
        StatisticsTableBlockEntity table = table(helper, TABLE);
        TestPlayer player = TestPlayer.in(helper);
        Item[] items = {Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.CLAY, Items.COAL, Items.FLINT, Items.STICK,
                Items.BONE, Items.DIAMOND, Items.EMERALD};

        helper.runAfterDelay(SETTLE, () -> {
            for (Item item : items) {
                table.track(type(item));
            }
            StatisticsTableMenu menu = open(helper, player);
            helper.assertTrue(menu.itemsPage() == 0 && menu.itemsPages() == 2, "eleven items make two pages");
            helper.assertTrue(menu.getSlot(8).getItem().is(Items.BONE), "nine of them on the first");

            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_NEXT_ITEMS);
            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_NEXT_ITEMS);
            helper.assertTrue(menu.itemsPage() == 1 && menu.getSlot(0).getItem().is(Items.DIAMOND)
                    && menu.getSlot(1).getItem().is(Items.EMERALD) && menu.getSlot(2).getItem().isEmpty(), "the next button goes forward");
            menu.clicked(1, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.selectedSlot() == 1 && menu.getSlot(StatisticsTableMenu.SELECTED_SLOT).getItem().is(Items.EMERALD),
                    "a click selects an item");

            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_PREVIOUS_ITEMS);
            helper.assertTrue(menu.itemsPage() == 0 && menu.selectedSlot() == -1, "the previous button goes back");
            helper.assertTrue(menu.getSlot(StatisticsTableMenu.SELECTED_SLOT).getItem().is(Items.EMERALD), "the selection stays");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void tasksTabListsWhatTheCraftersStillOweOnceAskedFor(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING);
        table(helper, TABLE);
        TestPlayer player = TestPlayer.in(helper);
        long now = helper.getLevel().getGameTime();
        crafting.outputOrders().add(OutputOrder.extra(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 8)), now));
        crafting.outputOrders().add(OutputOrder.extra(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 4)), now));
        crafting.outputOrders().add(OutputOrder.extra(McItemType.batchOf(new ItemStack(Items.STICK, 2)), now));
        int list = StatisticsTableMenu.FIRST_LIST_SLOT;

        helper.runAfterDelay(SETTLE, () -> {
            StatisticsTableMenu menu = open(helper, player);
            menu.clickMenuButton(player, StatisticsTableMenu.View.TASKS.ordinal());
            helper.assertTrue(menu.view() == StatisticsTableMenu.View.TASKS && menu.getSlot(list).getItem().isEmpty(),
                    "the tasks tab is empty until the tasks are asked for");

            menu.clickMenuButton(player, StatisticsTableMenu.BUTTON_REFRESH);
            helper.assertTrue(menu.getSlot(list).getItem().is(Items.OAK_PLANKS) && RequestMenu.listedCount(menu.getSlot(list).getItem()) == 12,
                    "orders for the same item add up");
            helper.assertTrue(menu.getSlot(list + 1).getItem().is(Items.STICK) && menu.getSlot(list + 2).getItem().isEmpty(),
                    "one entry per item");
            menu.clicked(list, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.pickedSlot() == -1, "a task cannot be picked");
            helper.succeed();
        });
    }
}
