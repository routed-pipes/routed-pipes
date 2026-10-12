package logisticspipes.adapter.neoforge.gametest;

import java.util.List;

import logisticspipes.adapter.minecraft.LogisticsPipeBlock;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.minecraft.TubeBlock;
import logisticspipes.adapter.minecraft.TubeBlockEntity;
import logisticspipes.adapter.minecraft.TubePartBlockEntity;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.tube.TubeKind;
import logisticspipes.domain.tube.TubeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class TubeGameTest {

    // twelve blocks long and eight wide, tubes need room
    private static final String LONG = "empty_long";

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, 1, z);
    }

    // a basic pipe that takes everything
    private static void router(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LogisticsPipes.pipe(PipeKind.BASIC));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.setDefaultRoute(true);
    }

    private static BlockPos chestAbove(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.above(), Blocks.CHEST);
        return pos.above();
    }

    private static void tube(GameTestHelper helper, TubeKind kind, BlockPos main, Side facing) {
        TubeBlock tube = LogisticsPipes.TUBES.get(kind).get();
        helper.assertTrue(tube.build(helper.getLevel(), helper.absolutePos(main), new TubeShape(kind, facing), pos -> true),
                "the tube fits");
    }

    private static void insert(GameTestHelper helper, BlockPos pos, Direction side, ItemStack stack) {
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.accept(stack, side);
    }

    private static void assertCount(GameTestHelper helper, BlockPos chest, Item item, int expected) {
        Container container = helper.getBlockEntity(chest);
        int count = container.countItem(item);
        helper.assertTrue(count == expected, "expected " + expected + " but chest holds " + count);
    }

    private static void assertTubeGone(GameTestHelper helper, List<BlockPos> blocks) {
        for (BlockPos pos : blocks) {
            helper.assertBlockPresent(Blocks.AIR, pos);
        }
    }

    private static Item speedupItem() {
        return LogisticsPipes.TUBES.get(TubeKind.SPEEDUP).get().asItem();
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversThroughTubes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, at(0, 1));
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        tube(helper, TubeKind.LINE, at(5, 1), Side.EAST);
        tube(helper, TubeKind.SPEEDUP, at(9, 1), Side.WEST);
        router(helper, at(10, 1));
        BlockPos chest = chestAbove(helper, at(10, 1));

        insert(helper, at(0, 1), Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> assertCount(helper, chest, Items.DIAMOND, 3));
    }

    // nine blocks of tube count less than four pipes
    @GameTest(template = LONG, timeoutTicks = 200)
    public static void aLongWayThroughTubesIsShorterThanAFewPipes(GameTestHelper helper) {
        GameTestPower.junction(helper, at(0, 2), PowerJunctionBlockEntity.CAPACITY);
        router(helper, at(0, 3));
        tube(helper, TubeKind.SPEEDUP, at(1, 3), Side.EAST);
        tube(helper, TubeKind.SPEEDUP, at(8, 3), Side.WEST);
        router(helper, at(9, 3));
        BlockPos far = chestAbove(helper, at(9, 3));
        for (int z = 4; z <= 6; z++) {
            helper.setBlock(at(0, z), LogisticsPipes.TRANSPORT_PIPE.get());
        }
        router(helper, at(0, 7));
        BlockPos near = chestAbove(helper, at(0, 7));

        insert(helper, at(0, 3), Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.runAfterDelay(150, () -> {
            assertCount(helper, far, Items.DIAMOND, 3);
            helper.assertContainerEmpty(near);
            helper.succeed();
        });
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void anItemFallsOutOfAnOpenTubeEnd(GameTestHelper helper) {
        helper.setBlock(at(0, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        tube(helper, TubeKind.LINE, at(5, 1), Side.EAST);

        helper.runAfterDelay(2, () -> insert(helper, at(0, 1), Direction.WEST, new ItemStack(Items.DIAMOND, 3)));

        helper.succeedWhen(() -> {
            helper.assertItemEntityCountIs(Items.DIAMOND, at(6, 1), 1.2, 3);
            TubeBlockEntity speedup = helper.getBlockEntity(at(1, 1));
            helper.assertTrue(speedup.travellingItems().isEmpty(), "nothing is left in the tube");
        });
    }

    // a line tube does not let items out into a pipe, only the speedup tube does
    @GameTest(template = LONG, timeoutTicks = 200)
    public static void aLineTubeDoesNotFeedAPipe(GameTestHelper helper) {
        helper.setBlock(at(0, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        tube(helper, TubeKind.LINE, at(5, 1), Side.EAST);
        helper.setBlock(at(6, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        helper.setBlock(at(7, 1), Blocks.CHEST);

        helper.runAfterDelay(2, () -> insert(helper, at(0, 1), Direction.WEST, new ItemStack(Items.DIAMOND, 3)));

        helper.runAfterDelay(60, () -> {
            PipeBlockEntity pipe = helper.getBlockEntity(at(6, 1));
            helper.assertTrue(pipe.travellingItems().isEmpty(), "the pipe got nothing");
            helper.assertItemEntityCountIs(Items.DIAMOND, at(5, 1), 2, 3);
            helper.succeed();
        });
    }

    @GameTest(template = LONG)
    public static void pipesConnectToTheMainBlockOfASpeedupTubeOnly(GameTestHelper helper) {
        tube(helper, TubeKind.SPEEDUP, at(2, 1), Side.EAST);
        helper.setBlock(at(1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        helper.setBlock(at(6, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        helper.setBlock(at(2, 2), LogisticsPipes.TRANSPORT_PIPE.get());
        tube(helper, TubeKind.LINE, at(2, 4), Side.EAST);
        helper.setBlock(at(1, 4), LogisticsPipes.TRANSPORT_PIPE.get());

        helper.runAfterDelay(3, () -> {
            helper.assertBlockProperty(at(1, 1), LogisticsPipeBlock.CONNECTED.get(Direction.EAST), true);
            helper.assertBlockProperty(at(6, 1), LogisticsPipeBlock.CONNECTED.get(Direction.WEST), false);
            helper.assertBlockProperty(at(2, 2), LogisticsPipeBlock.CONNECTED.get(Direction.NORTH), false);
            helper.assertBlockProperty(at(1, 4), LogisticsPipeBlock.CONNECTED.get(Direction.EAST), false);
            helper.succeed();
        });
    }

    @GameTest(template = LONG)
    public static void breakingAPartTakesTheWholeTubeAndDropsOneItem(GameTestHelper helper) {
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        helper.assertBlockPresent(LogisticsPipes.TUBE_PART.get(), at(4, 1));

        helper.getLevel().destroyBlock(helper.absolutePos(at(3, 1)), true);

        assertTubeGone(helper, List.of(at(1, 1), at(2, 1), at(3, 1), at(4, 1)));
        helper.assertItemEntityCountIs(speedupItem(), at(2, 1), 3, 1);
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void breakingTheMainBlockTakesThePartsAndDropsOneItem(GameTestHelper helper) {
        tube(helper, TubeKind.SPEEDUP, at(4, 1), Side.WEST);
        helper.assertBlockPresent(LogisticsPipes.TUBE_PART.get(), at(1, 1));

        helper.getLevel().destroyBlock(helper.absolutePos(at(4, 1)), true);

        assertTubeGone(helper, List.of(at(1, 1), at(2, 1), at(3, 1), at(4, 1)));
        helper.assertItemEntityCountIs(speedupItem(), at(2, 1), 3, 1);
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void aBrokenTubeDropsWhatWasInside(GameTestHelper helper) {
        helper.setBlock(at(0, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        tube(helper, TubeKind.SPEEDUP, at(5, 1), Side.EAST);

        helper.runAfterDelay(2, () -> insert(helper, at(0, 1), Direction.WEST, new ItemStack(Items.DIAMOND, 3)));

        helper.succeedWhen(() -> {
            TubeBlockEntity second = helper.getBlockEntity(at(5, 1));
            helper.assertTrue(!second.travellingItems().isEmpty(), "the item is in the second tube");
            helper.getLevel().destroyBlock(helper.absolutePos(at(7, 1)), true);
            helper.assertItemEntityCountIs(Items.DIAMOND, at(5, 1), 1.5, 3);
        });
    }

    @GameTest(template = LONG)
    public static void aPlayerPutsATubeDownAwayFromThemselves(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
        TestPlayer player = TestPlayer.in(helper);
        Vec3 feet = helper.absoluteVec(new Vec3(0.5, 1, 1.5));
        player.setPos(feet.x, feet.y, feet.z);
        ItemStack stack = new ItemStack(speedupItem(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(2.5, 1, 1.5)), Direction.UP,
                helper.absolutePos(new BlockPos(2, 0, 1)), false);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);

        // something in the way of the last block
        helper.setBlock(at(5, 1), Blocks.STONE);
        InteractionResult blocked = stack.getItem().useOn(context);
        helper.assertTrue(blocked == InteractionResult.FAIL, "does not fit, got " + blocked);
        assertTubeGone(helper, List.of(at(2, 1), at(3, 1), at(4, 1)));
        helper.assertTrue(stack.getCount() == 2, "nothing was used up");

        helper.setBlock(at(5, 1), Blocks.AIR);
        InteractionResult placed = stack.getItem().useOn(context);
        helper.assertTrue(placed.consumesAction(), "fits now, got " + placed);
        helper.assertBlockPresent(LogisticsPipes.TUBES.get(TubeKind.SPEEDUP).get(), at(2, 1));
        helper.assertBlockProperty(at(2, 1), TubeBlock.FACING, Direction.EAST);
        for (int x = 3; x <= 5; x++) {
            helper.assertBlockPresent(LogisticsPipes.TUBE_PART.get(), at(x, 1));
        }
        helper.assertTrue(stack.getCount() == 1, "one tube was used up");
        helper.succeed();
    }

    @GameTest(template = LONG, timeoutTicks = 300)
    public static void aNewTubeLinksTheRoutersAtItsEnds(GameTestHelper helper) {
        GameTestPower.supply(helper);
        router(helper, at(0, 1));
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        router(helper, at(9, 1));
        BlockPos chest = chestAbove(helper, at(9, 1));

        // the routers have long looked around when the second tube closes the gap
        helper.runAfterDelay(20, () -> {
            tube(helper, TubeKind.SPEEDUP, at(8, 1), Side.WEST);
            helper.runAfterDelay(5, () -> insert(helper, at(0, 1), Direction.DOWN, new ItemStack(Items.DIAMOND, 3)));
        });

        helper.succeedWhen(() -> assertCount(helper, chest, Items.DIAMOND, 3));
    }

    // router, speedup tube east, curve to the south, speedup tube south, router
    private static void aroundTheCorner(GameTestHelper helper) {
        GameTestPower.junction(helper, at(0, 1), PowerJunctionBlockEntity.CAPACITY);
        router(helper, at(0, 0));
        tube(helper, TubeKind.SPEEDUP, at(1, 0), Side.EAST);
        tube(helper, TubeKind.CURVE, at(5, 0), Side.WEST);
        tube(helper, TubeKind.SPEEDUP, at(7, 6), Side.NORTH);
        router(helper, at(7, 7));
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversAroundACurve(GameTestHelper helper) {
        aroundTheCorner(helper);
        BlockPos chest = chestAbove(helper, at(7, 7));

        insert(helper, at(0, 0), Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> assertCount(helper, chest, Items.DIAMOND, 3));
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversAroundACurveTheOtherWay(GameTestHelper helper) {
        aroundTheCorner(helper);
        BlockPos chest = chestAbove(helper, at(0, 0));

        insert(helper, at(7, 7), Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> assertCount(helper, chest, Items.DIAMOND, 3));
    }

    @GameTest(template = LONG)
    public static void aCurveFillsSevenBlocksAndGoesAsOne(GameTestHelper helper) {
        tube(helper, TubeKind.CURVE, at(5, 0), Side.WEST);
        List<BlockPos> parts = List.of(at(5, 1), at(6, 0), at(6, 1), at(6, 2), at(7, 1), at(7, 2));
        for (BlockPos part : parts) {
            helper.assertBlockPresent(LogisticsPipes.TUBE_PART.get(), part);
        }

        helper.getLevel().destroyBlock(helper.absolutePos(at(7, 2)), true);

        helper.assertBlockPresent(Blocks.AIR, at(5, 0));
        assertTubeGone(helper, parts);
        helper.assertItemEntityCountIs(LogisticsPipes.TUBES.get(TubeKind.CURVE).get().asItem(), at(5, 0), 3, 1);
        helper.succeed();
    }

    // one curve inside the other, the blocks between them belong to both
    @GameTest(template = LONG)
    public static void curvesShareBlocksAndAreBrokenOneByOne(GameTestHelper helper) {
        TubeBlock curve = LogisticsPipes.TUBES.get(TubeKind.CURVE).get();
        TubeShape north = new TubeShape(TubeKind.CURVE, Side.NORTH);
        tube(helper, TubeKind.CURVE, at(4, 1), Side.NORTH);
        tube(helper, TubeKind.CURVE, at(5, 2), Side.NORTH);
        TubePartBlockEntity shared = helper.getBlockEntity(at(4, 2));
        helper.assertTrue(shared.mains().size() == 2, "the outer block of the inner curve holds both");
        TubePartBlockEntity alsoShared = helper.getBlockEntity(at(3, 3));
        helper.assertTrue(alsoShared.mains().size() == 2, "and so does the other one");
        // a curve that would run through the main block of another does not fit, and leaves nothing behind
        helper.assertTrue(!curve.build(helper.getLevel(), helper.absolutePos(at(6, 1)), north, pos -> true), "no crossing");
        helper.assertBlockPresent(Blocks.AIR, at(6, 1));
        helper.assertBlockPresent(Blocks.AIR, at(6, 2));

        helper.getLevel().destroyBlock(helper.absolutePos(at(5, 2)), true);

        helper.assertBlockPresent(curve, at(4, 1));
        helper.assertBlockPresent(LogisticsPipes.TUBE_PART.get(), at(4, 2));
        helper.assertTrue(shared.mains().equals(List.of(helper.absolutePos(at(4, 1)))), "the shared block stays with the curve that is left");
        helper.assertBlockPresent(Blocks.AIR, at(5, 3));
        helper.assertItemEntityCountIs(curve.asItem(), at(5, 2), 3, 1);

        // breaking a shared block takes both curves
        tube(helper, TubeKind.CURVE, at(5, 2), Side.NORTH);
        helper.getLevel().destroyBlock(helper.absolutePos(at(4, 2)), true);
        helper.assertBlockPresent(Blocks.AIR, at(4, 1));
        helper.assertBlockPresent(Blocks.AIR, at(5, 2));
        helper.assertBlockPresent(Blocks.AIR, at(3, 3));
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void aPlayerBendsACurveToTheSideTheyLookAt(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 4), Blocks.STONE);
        TestPlayer player = TestPlayer.in(helper);
        Vec3 feet = helper.absoluteVec(new Vec3(0.5, 1, 4.5));
        player.setPos(feet.x, feet.y, feet.z);
        // east and a bit north
        player.setYRot(-100);
        ItemStack stack = new ItemStack(LogisticsPipes.TUBES.get(TubeKind.CURVE).get().asItem());
        BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(2.5, 1, 4.5)), Direction.UP,
                helper.absolutePos(new BlockPos(2, 0, 4)), false);

        InteractionResult placed = stack.getItem().useOn(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));

        helper.assertTrue(placed.consumesAction(), "fits, got " + placed);
        // the main block is the far end
        helper.assertBlockPresent(LogisticsPipes.TUBES.get(TubeKind.CURVE).get(), at(4, 2));
        helper.assertBlockProperty(at(4, 2), TubeBlock.FACING, Direction.NORTH);
        helper.assertBlockPresent(LogisticsPipes.TUBE_PART.get(), at(2, 4));
        helper.assertTrue(stack.isEmpty(), "the tube was used up");
        helper.succeed();
    }
}
