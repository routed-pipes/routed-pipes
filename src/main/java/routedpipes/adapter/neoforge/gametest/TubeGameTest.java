package routedpipes.adapter.neoforge.gametest;

import java.util.List;

import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.PipeArm;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.TubeBlock;
import routedpipes.adapter.minecraft.TubeBlockEntity;
import routedpipes.adapter.minecraft.TubePartBlockEntity;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Side;
import routedpipes.domain.tube.TubeKind;
import routedpipes.domain.tube.TubeShape;
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

@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class TubeGameTest {

    // sixteen blocks long and eight wide, tubes need room
    private static final String LONG = "empty_long";

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, 1, z);
    }

    // a basic pipe that takes everything
    private static void router(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RoutedPipes.pipe(PipeKind.BASIC));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.setDefaultRoute(true);
    }

    private static BlockPos chestAbove(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.above(), Blocks.CHEST);
        return pos.above();
    }

    private static void tube(GameTestHelper helper, TubeKind kind, BlockPos main, Side facing) {
        TubeBlock tube = RoutedPipes.TUBES.get(kind).get();
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
        return RoutedPipes.TUBES.get(TubeKind.SPEEDUP).get().asItem();
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
            helper.setBlock(at(0, z), RoutedPipes.TRANSPORT_PIPE.get());
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
        helper.setBlock(at(0, 1), RoutedPipes.TRANSPORT_PIPE.get());
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
        helper.setBlock(at(0, 1), RoutedPipes.TRANSPORT_PIPE.get());
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        tube(helper, TubeKind.LINE, at(5, 1), Side.EAST);
        helper.setBlock(at(6, 1), RoutedPipes.TRANSPORT_PIPE.get());
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
        helper.setBlock(at(1, 1), RoutedPipes.TRANSPORT_PIPE.get());
        helper.setBlock(at(6, 1), RoutedPipes.TRANSPORT_PIPE.get());
        helper.setBlock(at(2, 2), RoutedPipes.TRANSPORT_PIPE.get());
        tube(helper, TubeKind.LINE, at(2, 4), Side.EAST);
        helper.setBlock(at(1, 4), RoutedPipes.TRANSPORT_PIPE.get());

        helper.runAfterDelay(3, () -> {
            helper.assertBlockProperty(at(1, 1), PipeBlock.ARMS.get(Direction.EAST), PipeArm.UNROUTED);
            helper.assertBlockProperty(at(6, 1), PipeBlock.ARMS.get(Direction.WEST), PipeArm.NONE);
            helper.assertBlockProperty(at(2, 2), PipeBlock.ARMS.get(Direction.NORTH), PipeArm.NONE);
            helper.assertBlockProperty(at(1, 4), PipeBlock.ARMS.get(Direction.EAST), PipeArm.NONE);
            helper.succeed();
        });
    }

    @GameTest(template = LONG)
    public static void breakingAPartTakesTheWholeTubeAndDropsOneItem(GameTestHelper helper) {
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), at(4, 1));

        helper.getLevel().destroyBlock(helper.absolutePos(at(3, 1)), true);

        assertTubeGone(helper, List.of(at(1, 1), at(2, 1), at(3, 1), at(4, 1)));
        helper.assertItemEntityCountIs(speedupItem(), at(2, 1), 3, 1);
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void breakingTheMainBlockTakesThePartsAndDropsOneItem(GameTestHelper helper) {
        tube(helper, TubeKind.SPEEDUP, at(4, 1), Side.WEST);
        helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), at(1, 1));

        helper.getLevel().destroyBlock(helper.absolutePos(at(4, 1)), true);

        assertTubeGone(helper, List.of(at(1, 1), at(2, 1), at(3, 1), at(4, 1)));
        helper.assertItemEntityCountIs(speedupItem(), at(2, 1), 3, 1);
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void aBrokenTubeDropsWhatWasInside(GameTestHelper helper) {
        helper.setBlock(at(0, 1), RoutedPipes.TRANSPORT_PIPE.get());
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
        helper.assertBlockPresent(RoutedPipes.TUBES.get(TubeKind.SPEEDUP).get(), at(2, 1));
        helper.assertBlockProperty(at(2, 1), TubeBlock.FACING, Direction.EAST);
        for (int x = 3; x <= 5; x++) {
            helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), at(x, 1));
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
            helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), part);
        }

        helper.getLevel().destroyBlock(helper.absolutePos(at(7, 2)), true);

        helper.assertBlockPresent(Blocks.AIR, at(5, 0));
        assertTubeGone(helper, parts);
        helper.assertItemEntityCountIs(RoutedPipes.TUBES.get(TubeKind.CURVE).get().asItem(), at(5, 0), 3, 1);
        helper.succeed();
    }

    // one curve inside the other, the blocks between them belong to both
    @GameTest(template = LONG)
    public static void curvesShareBlocksAndAreBrokenOneByOne(GameTestHelper helper) {
        TubeBlock curve = RoutedPipes.TUBES.get(TubeKind.CURVE).get();
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
        helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), at(4, 2));
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
        ItemStack stack = new ItemStack(RoutedPipes.TUBES.get(TubeKind.CURVE).get().asItem());
        BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(2.5, 1, 4.5)), Direction.UP,
                helper.absolutePos(new BlockPos(2, 0, 4)), false);

        InteractionResult placed = stack.getItem().useOn(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));

        helper.assertTrue(placed.consumesAction(), "fits, got " + placed);
        // the main block is the far end
        helper.assertBlockPresent(RoutedPipes.TUBES.get(TubeKind.CURVE).get(), at(4, 2));
        helper.assertBlockProperty(at(4, 2), TubeBlock.FACING, Direction.NORTH);
        helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), at(2, 4));
        helper.assertTrue(stack.isEmpty(), "the tube was used up");
        helper.succeed();
    }

    private static void tube(GameTestHelper helper, TubeKind kind, BlockPos main, Side facing, boolean mirrored) {
        TubeBlock tube = RoutedPipes.TUBES.get(kind).get();
        helper.assertTrue(tube.build(helper.getLevel(), helper.absolutePos(main), new TubeShape(kind, facing, mirrored), pos -> true),
                "the tube fits");
    }

    // router, speedup tube east, the tube in question from x 5 to 8, speedup tube, router at the given place
    private static void throughATube(GameTestHelper helper, TubeKind kind, boolean mirrored, BlockPos end) {
        GameTestPower.junction(helper, at(0, 2), PowerJunctionBlockEntity.CAPACITY);
        router(helper, at(0, 1));
        tube(helper, TubeKind.SPEEDUP, at(1, 1), Side.EAST);
        tube(helper, kind, at(5, 1), Side.EAST, mirrored);
        tube(helper, TubeKind.SPEEDUP, end.west(), Side.WEST);
        router(helper, end);
    }

    private static void deliver(GameTestHelper helper, BlockPos from, BlockPos to) {
        BlockPos chest = chestAbove(helper, to);

        insert(helper, from, Direction.DOWN, new ItemStack(Items.DIAMOND, 3));

        helper.succeedWhen(() -> assertCount(helper, chest, Items.DIAMOND, 3));
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversUpAGainTube(GameTestHelper helper) {
        throughATube(helper, TubeKind.GAIN, false, new BlockPos(13, 2, 1));
        deliver(helper, at(0, 1), new BlockPos(13, 2, 1));
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversDownAGainTube(GameTestHelper helper) {
        throughATube(helper, TubeKind.GAIN, false, new BlockPos(13, 2, 1));
        deliver(helper, new BlockPos(13, 2, 1), at(0, 1));
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversThroughAnSCurve(GameTestHelper helper) {
        throughATube(helper, TubeKind.S_CURVE, false, at(13, 2));
        deliver(helper, at(0, 1), at(13, 2));
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversBackThroughAnSCurve(GameTestHelper helper) {
        throughATube(helper, TubeKind.S_CURVE, false, at(13, 2));
        deliver(helper, at(13, 2), at(0, 1));
    }

    @GameTest(template = LONG, timeoutTicks = 200)
    public static void deliversThroughAMirroredSCurve(GameTestHelper helper) {
        throughATube(helper, TubeKind.S_CURVE, true, at(13, 0));
        deliver(helper, at(0, 1), at(13, 0));
    }

    @GameTest(template = LONG)
    public static void aGainTubeFillsSixBlocksOnTwoLevelsAndGoesAsOne(GameTestHelper helper) {
        tube(helper, TubeKind.GAIN, at(1, 1), Side.EAST);
        List<BlockPos> parts = List.of(at(2, 1), at(3, 1), new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1));
        for (BlockPos part : parts) {
            helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), part);
        }

        helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(4, 2, 1)), true);

        helper.assertBlockPresent(Blocks.AIR, at(1, 1));
        assertTubeGone(helper, parts);
        helper.assertItemEntityCountIs(RoutedPipes.TUBES.get(TubeKind.GAIN).get().asItem(), at(1, 1), 3, 1);
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void gainTubesStackAndSCurvesLieSideBySide(GameTestHelper helper) {
        tube(helper, TubeKind.GAIN, at(1, 1), Side.EAST);
        tube(helper, TubeKind.GAIN, new BlockPos(1, 2, 1), Side.EAST);
        TubePartBlockEntity betweenGains = helper.getBlockEntity(new BlockPos(2, 2, 1));
        helper.assertTrue(betweenGains.mains().size() == 2, "the upper tube starts where the lower one ends up");

        tube(helper, TubeKind.S_CURVE, at(1, 4), Side.EAST);
        tube(helper, TubeKind.S_CURVE, at(1, 5), Side.EAST);
        TubePartBlockEntity betweenCurves = helper.getBlockEntity(at(2, 5));
        helper.assertTrue(betweenCurves.mains().size() == 2, "the second curve starts where the first one ends up");

        // an s-curve the other way round would cross the first one
        TubeBlock curve = RoutedPipes.TUBES.get(TubeKind.S_CURVE).get();
        helper.assertTrue(!curve.build(helper.getLevel(), helper.absolutePos(at(1, 6)), new TubeShape(TubeKind.S_CURVE, Side.EAST, true),
                pos -> true), "no crossing");
        helper.assertBlockPresent(Blocks.AIR, at(1, 6));
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void aPlayerMovesAnSCurveOverToTheSideTheyLookAt(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 4), Blocks.STONE);
        TestPlayer player = TestPlayer.in(helper);
        Vec3 feet = helper.absoluteVec(new Vec3(0.5, 1, 4.5));
        player.setPos(feet.x, feet.y, feet.z);
        // east and a bit north
        player.setYRot(-100);
        ItemStack stack = new ItemStack(RoutedPipes.TUBES.get(TubeKind.S_CURVE).get().asItem());
        BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(2.5, 1, 4.5)), Direction.UP,
                helper.absolutePos(new BlockPos(2, 0, 4)), false);

        InteractionResult placed = stack.getItem().useOn(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));

        helper.assertTrue(placed.consumesAction(), "fits, got " + placed);
        helper.assertBlockPresent(RoutedPipes.TUBES.get(TubeKind.S_CURVE).get(), at(2, 4));
        helper.assertBlockProperty(at(2, 4), TubeBlock.FACING, Direction.EAST);
        helper.assertBlockProperty(at(2, 4), TubeBlock.MIRRORED, true);
        helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), at(5, 3));
        helper.succeed();
    }

    @GameTest(template = LONG)
    public static void aPlayerPutsAGainTubeDownClimbingAwayFromThem(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 0, 4), Blocks.STONE);
        TestPlayer player = TestPlayer.in(helper);
        Vec3 feet = helper.absoluteVec(new Vec3(0.5, 1, 4.5));
        player.setPos(feet.x, feet.y, feet.z);
        player.setYRot(-90);
        ItemStack stack = new ItemStack(RoutedPipes.TUBES.get(TubeKind.GAIN).get().asItem());
        BlockHitResult hit = new BlockHitResult(helper.absoluteVec(new Vec3(2.5, 1, 4.5)), Direction.UP,
                helper.absolutePos(new BlockPos(2, 0, 4)), false);

        InteractionResult placed = stack.getItem().useOn(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));

        helper.assertTrue(placed.consumesAction(), "fits, got " + placed);
        helper.assertBlockPresent(RoutedPipes.TUBES.get(TubeKind.GAIN).get(), at(2, 4));
        helper.assertBlockProperty(at(2, 4), TubeBlock.FACING, Direction.EAST);
        helper.assertBlockPresent(RoutedPipes.TUBE_PART.get(), new BlockPos(5, 2, 4));
        helper.succeed();
    }
}
