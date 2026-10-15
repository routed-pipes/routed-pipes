package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class PipeTransportGameTest {

    private static final String EMPTY = "empty";

    // a basic pipe that takes everything
    private static void router(GameTestHelper helper, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(PipeKind.BASIC));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.setDefaultRoute(true);
    }

    private static void transport(GameTestHelper helper, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Content.TRANSPORT_PIPE.get());
        }
    }

    private static BlockPos chestAbove(GameTestHelper helper, int x) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        return pos;
    }

    private static void insert(GameTestHelper helper, int x, Direction side, ItemStack stack) {
        PipeBlockEntity pipe = helper.getBlockEntity(new BlockPos(x, 1, 1));
        pipe.accept(stack, side);
    }

    private static void assertCount(GameTestHelper helper, BlockPos chest, Item item, int expected) {
        Container container = helper.getBlockEntity(chest);
        int count = container.countItem(item);
        helper.assertTrue(count == expected, "expected " + expected + " but chest holds " + count);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void deliversThroughTransportPipes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 0);
        transport(helper, 1, 3);
        router(helper, 4);
        BlockPos chest = chestAbove(helper, 4);

        insert(helper, 0, Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> assertCount(helper, chest, Items.DIAMOND, 3));
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void prefersTheNearestChest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 1);
        BlockPos near = chestAbove(helper, 1);
        router(helper, 2);
        transport(helper, 3, 5);
        router(helper, 6);
        BlockPos far = chestAbove(helper, 6);

        insert(helper, 2, Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.runAfterDelay(150, () -> {
            assertCount(helper, near, Items.DIAMOND, 3);
            helper.assertContainerEmpty(far);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void skipsFullChest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 1);
        BlockPos near = chestAbove(helper, 1);
        Container full = helper.getBlockEntity(near);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        router(helper, 2);
        transport(helper, 3, 5);
        router(helper, 6);
        BlockPos far = chestAbove(helper, 6);

        insert(helper, 2, Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> assertCount(helper, far, Items.DIAMOND, 3));
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void dropsItemWhenNothingTakesIt(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 0);
        transport(helper, 1, 2);
        router(helper, 3);

        insert(helper, 0, Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.DIAMOND, new BlockPos(0, 1, 1), 2));
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hopperFeedsThePipe(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 0);
        BlockPos hopper = new BlockPos(0, 2, 1);
        helper.setBlock(hopper, Blocks.HOPPER);
        transport(helper, 1, 2);
        router(helper, 3);
        BlockPos chest = chestAbove(helper, 3);

        Container container = helper.getBlockEntity(hopper);
        container.setItem(0, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> assertCount(helper, chest, Items.DIAMOND, 3));
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void reroutesAfterThePipeRunIsBroken(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 0);
        transport(helper, 1, 3);
        router(helper, 4);
        BlockPos chest = chestAbove(helper, 4);

        helper.runAfterDelay(5, () -> {
            helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR);
            helper.runAfterDelay(5, () -> insert(helper, 0, Direction.DOWN, new ItemStack(Items.DIAMOND, 3)));
        });

        helper.runAfterDelay(300, () -> {
            helper.assertContainerEmpty(chest);
            helper.assertItemEntityPresent(Items.DIAMOND, new BlockPos(0, 1, 1), 2);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void filteredPipeGetsItsItemsBeforeANearerDefaultRoute(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 1);
        BlockPos near = chestAbove(helper, 1);
        router(helper, 2);
        transport(helper, 3, 5);
        router(helper, 6);
        BlockPos far = chestAbove(helper, 6);
        PipeBlockEntity filtered = helper.getBlockEntity(new BlockPos(6, 1, 1));
        filtered.config().setItem(0, new ItemStack(Items.DIAMOND));
        filtered.setDefaultRoute(false);

        // wait until the far pipe is known, before that the near chest is the only choice
        helper.runAfterDelay(5, () -> {
            insert(helper, 2, Direction.DOWN, new ItemStack(Items.DIAMOND, 3));
            insert(helper, 2, Direction.DOWN, new ItemStack(Items.STONE, 5));
        });

        helper.runAfterDelay(150, () -> {
            assertCount(helper, far, Items.DIAMOND, 3);
            assertCount(helper, far, Items.STONE, 0);
            assertCount(helper, near, Items.STONE, 5);
            assertCount(helper, near, Items.DIAMOND, 0);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aFreshBasicPipeTakesNothingUntilItIsToldTo(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, 0);
        PipeBlockEntity start = helper.getBlockEntity(new BlockPos(0, 1, 1));
        start.setDefaultRoute(false);
        helper.setBlock(new BlockPos(1, 1, 1), Content.pipe(PipeKind.BASIC));
        BlockPos chest = chestAbove(helper, 1);
        PipeBlockEntity fresh = helper.getBlockEntity(new BlockPos(1, 1, 1));

        helper.runAfterDelay(15, () -> insert(helper, 0, Direction.DOWN, new ItemStack(Items.DIAMOND, 3)));
        helper.runAfterDelay(60, () -> {
            helper.assertContainerEmpty(chest);
            fresh.setDefaultRoute(true);
        });
        helper.runAfterDelay(120, () -> {
            assertCount(helper, chest, Items.DIAMOND, 3);
            helper.succeed();
        });
    }

    // without a junction on the network even plain sorting stands still, the item waits and is then given up on
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void nothingIsSortedWithoutPower(GameTestHelper helper) {
        router(helper, 4);
        router(helper, 5);
        BlockPos chest = chestAbove(helper, 5);

        helper.runAfterDelay(15, () -> insert(helper, 4, Direction.DOWN, new ItemStack(Items.DIAMOND, 3)));
        helper.runAfterDelay(100, () -> helper.assertContainerEmpty(chest));
        helper.runAfterDelay(300, () -> {
            helper.assertContainerEmpty(chest);
            helper.assertItemEntityPresent(Items.DIAMOND, new BlockPos(4, 1, 1), 2);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void sortingAStackCostsALittlePower(GameTestHelper helper) {
        router(helper, 4);
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(4, 1, 2), 100);
        router(helper, 5);
        BlockPos chest = chestAbove(helper, 5);

        helper.runAfterDelay(15, () -> insert(helper, 4, Direction.DOWN, new ItemStack(Items.DIAMOND, 3)));

        helper.succeedWhen(() -> {
            assertCount(helper, chest, Items.DIAMOND, 3);
            helper.assertTrue(junction.stored() == 98, "one sorted stack costs 2 FE, junction holds " + junction.stored());
        });
    }
}
