package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.menu.UpgradeMenu;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.application.LogisticsNetwork;
import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
public class NetworkOverviewGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;

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

    private static UpgradeMenu.PipeRow row(GameTestHelper helper, UpgradeMenu menu, int row) {
        return UpgradeMenu.pipeRowOf(menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT + row).getItem())
                .orElseThrow(() -> new IllegalStateException("row " + row + " of the network tab should list a pipe"));
    }

    private static void assertRow(GameTestHelper helper, UpgradeMenu menu, int row, PipeKind kind, int x, int sent, int received,
            int relayed, int orders) {
        ItemStack stack = menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT + row).getItem();
        helper.assertTrue(stack.is(LogisticsPipes.pipe(kind).asItem()), "row " + row + " should be a " + kind + " pipe but is " + stack);
        UpgradeMenu.PipeRow found = row(helper, menu, row);
        UpgradeMenu.PipeRow wanted = new UpgradeMenu.PipeRow(helper.absolutePos(new BlockPos(x, 1, 1)), sent, received, relayed, orders);
        helper.assertTrue(found.equals(wanted), "row " + row + " should be " + wanted + " but is " + found);
    }

    // request, basic, provider and two more basic pipes in a row, ten cobblestone are ordered from one end to the other
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void networkTabListsEveryPipeNearestFirstPageByPage(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.BASIC, 1);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 2);
        chest(helper, 2, new ItemStack(Items.COBBLESTONE, 64));
        pipe(helper, PipeKind.BASIC, 3);
        pipe(helper, PipeKind.BASIC, 4);
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 10)))
                .started(), "request should be possible"));

        helper.succeedWhen(() -> {
            Container container = helper.getBlockEntity(target);
            helper.assertTrue(container.countItem(Items.COBBLESTONE) == 10, "the cobblestone has not arrived yet");
            if (provider.outputOrders().isEmpty()) {
                provider.outputOrders().add(OutputOrder.extra(McItemType.batchOf(new ItemStack(Items.STICK, 2)),
                        helper.getLevel().getGameTime()));
            }
            UpgradeMenu menu = controller(helper, player, 0);
            helper.assertTrue(!menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT).isActive(), "no pipes listed on the first tab");
            helper.assertTrue(menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT).getItem().isEmpty(), "the network is not looked up before its tab opens");

            menu.clickMenuButton(player, UpgradeMenu.Tab.NETWORK.ordinal());
            helper.assertTrue(menu.tab() == UpgradeMenu.Tab.NETWORK && menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT).isActive(),
                    "pipes are listed on the network tab");
            helper.assertTrue(!menu.getSlot(UpgradeMenu.FIRST_TASK_SLOT).isActive(), "the orders of the tasks tab are gone");
            helper.assertTrue(menu.page() == 0 && menu.pages() == 2, "five pipes make two pages, got " + menu.pages());
            assertRow(helper, menu, 0, PipeKind.REQUEST, 0, 0, 10, 0, 0);
            assertRow(helper, menu, 1, PipeKind.BASIC, 1, 0, 0, 10, 0);
            assertRow(helper, menu, 2, PipeKind.PROVIDER, 2, 10, 0, 0, 1);
            assertRow(helper, menu, 3, PipeKind.BASIC, 3, 0, 0, 0, 0);
            long energy = 2L * PowerJunctionBlockEntity.CAPACITY - 10 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM;
            helper.assertTrue(menu.networkEnergy() == energy, "the network holds " + energy + " but the tab shows " + menu.networkEnergy());

            menu.clickMenuButton(player, UpgradeMenu.BUTTON_NEXT_PAGE);
            menu.clickMenuButton(player, UpgradeMenu.BUTTON_NEXT_PAGE);
            helper.assertTrue(menu.page() == 1, "paging stops at the last page, got " + menu.page());
            assertRow(helper, menu, 0, PipeKind.BASIC, 4, 0, 0, 0, 0);
            helper.assertTrue(menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT + 1).getItem().isEmpty(), "nothing after the last pipe");
            menu.clickMenuButton(player, UpgradeMenu.BUTTON_PREVIOUS_PAGE);
            menu.clickMenuButton(player, UpgradeMenu.BUTTON_PREVIOUS_PAGE);
            helper.assertTrue(menu.page() == 0, "paging stops at the first page");

            // the pages of the tasks tab are its own
            menu.clickMenuButton(player, UpgradeMenu.Tab.TASKS.ordinal());
            helper.assertTrue(menu.page() == 0 && menu.pages() == 1, "the request pipe has no orders to page through");
        });
    }

    // seen from the middle the pipe itself comes first, and a pipe that is not connected is not listed
    @GameTest(template = EMPTY)
    public static void networkTabStartsAtThePipeItWasOpenedOn(GameTestHelper helper) {
        pipe(helper, PipeKind.BASIC, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        pipe(helper, PipeKind.BASIC, 3);
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            UpgradeMenu menu = controller(helper, player, 1);
            menu.clickMenuButton(player, UpgradeMenu.Tab.NETWORK.ordinal());
            assertRow(helper, menu, 0, PipeKind.PROVIDER, 1, 0, 0, 0, 0);
            assertRow(helper, menu, 1, PipeKind.BASIC, 0, 0, 0, 0, 0);
            helper.assertTrue(menu.getSlot(UpgradeMenu.FIRST_PIPE_SLOT + 2).getItem().isEmpty(), "the pipe on its own is not listed");
            helper.assertTrue(menu.pages() == 1 && menu.networkEnergy() == 0, "one page and no energy without a junction");
            helper.succeed();
        });
    }
}
