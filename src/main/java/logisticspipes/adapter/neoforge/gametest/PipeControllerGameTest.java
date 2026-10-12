package logisticspipes.adapter.neoforge.gametest;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.menu.UpgradeMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.UpgradeKind;
import logisticspipes.domain.statistics.PipeStatistics.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class PipeControllerGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;
    // the second slot of the hotbar as the controller menu numbers it, the first one holds the controller
    private static final int HOTBAR_SLOT = PipeBlockEntity.UPGRADE_SLOTS + 28;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static BlockPos chest(GameTestHelper helper, int x, ItemStack... contents) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return pos;
    }

    // what a player with the controller in the hand gets when using it on the pipe at x
    private static UpgradeMenu controller(GameTestHelper helper, TestPlayer player, int x) {
        BlockPos relative = new BlockPos(x, 1, 1);
        BlockPos pos = helper.absolutePos(relative);
        ItemStack controller = new ItemStack(LogisticsPipes.PIPE_CONTROLLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, controller);
        helper.getBlockState(relative).useItemOn(controller, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return (UpgradeMenu) player.containerMenu;
    }

    private static void press(UpgradeMenu menu, TestPlayer player, int button) {
        menu.clickMenuButton(player, button);
    }

    private static void assertCounts(GameTestHelper helper, UpgradeMenu menu, String who, long sent, long received, long relayed) {
        String found = menu.session(Kind.SENT) + "/" + menu.session(Kind.RECEIVED) + "/" + menu.session(Kind.RELAYED);
        helper.assertTrue(found.equals(sent + "/" + received + "/" + relayed),
                who + " should show " + sent + "/" + received + "/" + relayed + " sent/received/relayed but shows " + found);
        helper.assertTrue(menu.lifetime(Kind.SENT) == sent && menu.lifetime(Kind.RECEIVED) == received
                && menu.lifetime(Kind.RELAYED) == relayed, who + " should show the same for its lifetime");
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void controllerShowsWhatAPipeSentReceivedAndRelayed(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.BASIC, 1);
        pipe(helper, PipeKind.PROVIDER, 2);
        chest(helper, 2, new ItemStack(Items.COBBLESTONE, 64));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 10)))
                .started(), "request should be possible"));

        helper.succeedWhen(() -> {
            Container container = helper.getBlockEntity(target);
            helper.assertTrue(container.countItem(Items.COBBLESTONE) == 10, "the cobblestone has not arrived yet");
            assertCounts(helper, controller(helper, player, 2), "the provider", 10, 0, 0);
            assertCounts(helper, controller(helper, player, 1), "the pipe in between", 0, 0, 10);
            UpgradeMenu atRequest = controller(helper, player, 0);
            assertCounts(helper, atRequest, "the request pipe", 0, 10, 0);
            helper.assertTrue(atRequest.routingTableSize() == 2, "two other pipes to route to, shows " + atRequest.routingTableSize());
        });
    }

    @GameTest(template = EMPTY)
    public static void lifetimeCountsSurviveSaveAndLoadAndTheSessionStartsOver(GameTestHelper helper) {
        PipeBlockEntity original = pipe(helper, PipeKind.BASIC, 0);
        original.statistics().count(Kind.SENT, 7);
        original.statistics().count(Kind.RELAYED, 70_000);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PipeBlockEntity loaded = pipe(helper, PipeKind.BASIC, 3);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.statistics().lifetime(Kind.SENT) == 7 && loaded.statistics().lifetime(Kind.RELAYED) == 70_000,
                "lifetime counts should be saved");
        helper.assertTrue(loaded.statistics().session(Kind.SENT) == 0 && loaded.statistics().session(Kind.RELAYED) == 0,
                "a loaded pipe starts a new session");
        // more than fits into one data slot of the menu
        UpgradeMenu menu = controller(helper, TestPlayer.in(helper), 3);
        helper.assertTrue(menu.lifetime(Kind.RELAYED) == 70_000, "the menu shows large counts, got " + menu.lifetime(Kind.RELAYED));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void upgradeSlotsAreOnlyThereOnTheirTab(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        TestPlayer player = TestPlayer.in(helper);
        ItemStack upgrade = new ItemStack(LogisticsPipes.UPGRADES.get(UpgradeKind.SPEED).get());
        player.getInventory().setItem(1, upgrade);
        UpgradeMenu menu = controller(helper, player, 0);

        helper.assertTrue(menu.tab() == UpgradeMenu.Tab.STATISTICS, "the controller opens on the statistics");
        helper.assertTrue(!menu.getSlot(0).isActive(), "no upgrade slots on the statistics tab");
        menu.quickMoveStack(player, HOTBAR_SLOT);
        helper.assertTrue(basic.upgrades().isEmpty(), "nothing goes into slots that are not on screen");

        press(menu, player, UpgradeMenu.Tab.UPGRADES.ordinal());
        helper.assertTrue(menu.getSlot(0).isActive(), "upgrade slots on the upgrades tab");
        menu.quickMoveStack(player, HOTBAR_SLOT);
        helper.assertTrue(basic.upgrades().getItem(0).is(LogisticsPipes.UPGRADES.get(UpgradeKind.SPEED).get()),
                "the upgrade goes in with a shift click");
        helper.succeed();
    }

    // a crafting pipe that never gets its planks keeps the order, three more are made up
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void tasksTabListsTheOpenOrdersPageByPage(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        chest(helper, 0);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 1);
        chest(helper, 1);
        crafting.config().setItem(0, new ItemStack(Items.OAK_LOG));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OAK_PLANKS, 4));
        pipe(helper, PipeKind.PROVIDER, 2);
        chest(helper, 2, new ItemStack(Items.OAK_LOG, 8));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 4))).started(), "the craft should start");
            for (int extra = 0; extra < 3; extra++) {
                crafting.outputOrders().add(OutputOrder.extra(McItemType.batchOf(new ItemStack(Items.STICK, 2 + extra)),
                        helper.getLevel().getGameTime()));
            }
            UpgradeMenu menu = controller(helper, player, 1);
            helper.assertTrue(!menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT).isActive(), "no orders on the first tab");
            press(menu, player, UpgradeMenu.Tab.TASKS.ordinal());
            helper.assertTrue(menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT).isActive(), "orders on the tasks tab");
            helper.assertTrue(menu.page() == 0 && menu.pages() == 2, "four orders make two pages, got " + menu.pages());

            ItemStack first = menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT).getItem();
            UpgradeMenu.Task task = UpgradeMenu.taskOf(first).orElseThrow();
            helper.assertTrue(first.is(Items.OAK_PLANKS) && task.number() == 1 && task.count() == 4, "the planks come first: " + task);
            helper.assertTrue(task.target().filter(helper.absolutePos(new BlockPos(0, 1, 1))::equals).isPresent(),
                    "they go to the request pipe: " + task.target());
            UpgradeMenu.Task extra = UpgradeMenu.taskOf(menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT + 1).getItem()).orElseThrow();
            helper.assertTrue(extra.target().isEmpty(), "something extra has no target");

            press(menu, player, UpgradeMenu.BUTTON_NEXT_PAGE);
            press(menu, player, UpgradeMenu.BUTTON_NEXT_PAGE);
            UpgradeMenu.Task last = UpgradeMenu.taskOf(menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT).getItem()).orElseThrow();
            helper.assertTrue(menu.page() == 1 && last.number() == 4 && last.count() == 4, "the last order is alone on page two: " + last);
            helper.assertTrue(menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT + 1).getItem().isEmpty(), "nothing after the last order");
            press(menu, player, UpgradeMenu.BUTTON_PREVIOUS_PAGE);
            press(menu, player, UpgradeMenu.BUTTON_PREVIOUS_PAGE);
            helper.assertTrue(menu.page() == 0, "paging stops at the first page");
            helper.succeed();
        });
    }
}
