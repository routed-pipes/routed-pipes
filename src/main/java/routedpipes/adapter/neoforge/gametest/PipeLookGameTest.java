package routedpipes.adapter.neoforge.gametest;

import java.util.List;

import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.PipeArm;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PipeMounts;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.look.Mount;
import routedpipes.domain.routing.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// what the block state of a pipe tells clients to draw
@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class PipeLookGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos FIRST = new BlockPos(3, 1, 1);
    private static final BlockPos BETWEEN = new BlockPos(4, 1, 1);
    private static final BlockPos SECOND = new BlockPos(5, 1, 1);

    private static void assertArm(GameTestHelper helper, BlockPos pos, Direction side, PipeArm expected) {
        PipeArm arm = helper.getBlockState(pos).getValue(PipeBlock.ARMS.get(side));
        helper.assertTrue(arm == expected, "expected " + expected + " to the " + side + " of " + pos + " but found " + arm);
    }

    // two routed pipes with a plain pipe between them
    private static void twoRouters(GameTestHelper helper) {
        helper.setBlock(FIRST, RoutedPipes.pipe(PipeKind.BASIC));
        helper.setBlock(BETWEEN, RoutedPipes.TRANSPORT_PIPE.get());
        helper.setBlock(SECOND, RoutedPipes.pipe(PipeKind.BASIC));
    }

    @GameTest(template = EMPTY)
    public static void armTowardsAnotherRoutedPipeIsGreenAndEveryOtherArmRed(GameTestHelper helper) {
        twoRouters(helper);
        // an inventory, a power junction and a plain pipe that leads nowhere
        helper.setBlock(FIRST.above(), Blocks.CHEST);
        helper.setBlock(FIRST.west(), RoutedPipes.POWER_JUNCTION.get());
        helper.setBlock(FIRST.south(), RoutedPipes.TRANSPORT_PIPE.get());

        helper.runAfterDelay(5, () -> {
            assertArm(helper, FIRST, Direction.EAST, PipeArm.ROUTED);
            assertArm(helper, FIRST, Direction.UP, PipeArm.UNROUTED);
            assertArm(helper, FIRST, Direction.WEST, PipeArm.UNROUTED);
            assertArm(helper, FIRST, Direction.SOUTH, PipeArm.UNROUTED);
            assertArm(helper, FIRST, Direction.NORTH, PipeArm.NONE);
            assertArm(helper, FIRST, Direction.DOWN, PipeArm.NONE);
            assertArm(helper, SECOND, Direction.WEST, PipeArm.ROUTED);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void plainPipesHaveNoLights(GameTestHelper helper) {
        twoRouters(helper);

        helper.runAfterDelay(5, () -> {
            assertArm(helper, BETWEEN, Direction.EAST, PipeArm.UNROUTED);
            assertArm(helper, BETWEEN, Direction.WEST, PipeArm.UNROUTED);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void lightTurnsRedWhenTheOtherRoutedPipeIsGoneAndGreenWhenItIsBack(GameTestHelper helper) {
        twoRouters(helper);

        helper.runAfterDelay(5, () -> {
            assertArm(helper, FIRST, Direction.EAST, PipeArm.ROUTED);
            helper.setBlock(SECOND, Blocks.AIR);
        });
        helper.runAfterDelay(10, () -> {
            assertArm(helper, FIRST, Direction.EAST, PipeArm.UNROUTED);
            helper.setBlock(SECOND, RoutedPipes.pipe(PipeKind.BASIC));
        });
        helper.runAfterDelay(15, () -> {
            assertArm(helper, FIRST, Direction.EAST, PipeArm.ROUTED);
            helper.succeed();
        });
    }

    private static void assertPowered(GameTestHelper helper, BlockPos pos, boolean expected) {
        helper.assertTrue(helper.getBlockState(pos).getValue(PipeBlock.POWERED) == expected,
                "the frame of " + pos + " should be " + (expected ? "lit" : "dark"));
    }

    // every routed pipe of the network follows a junction anywhere in it, plain pipes never light up
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void frameIsLitWhileTheNetworkHasEnergyAndDarkWithout(GameTestHelper helper) {
        twoRouters(helper);
        BlockPos junction = SECOND.east();

        helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    assertPowered(helper, FIRST, false);
                    assertPowered(helper, SECOND, false);
                    GameTestPower.junction(helper, junction, 1000);
                })
                .thenWaitUntil(() -> {
                    assertPowered(helper, FIRST, true);
                    assertPowered(helper, SECOND, true);
                })
                .thenExecute(() -> {
                    assertPowered(helper, BETWEEN, false);
                    helper.setBlock(junction, Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    assertPowered(helper, FIRST, false);
                    assertPowered(helper, SECOND, false);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void anEmptyJunctionLightsNothing(GameTestHelper helper) {
        twoRouters(helper);
        GameTestPower.junction(helper, SECOND.east(), 0);

        helper.runAfterDelay(60, () -> {
            assertPowered(helper, FIRST, false);
            helper.succeed();
        });
    }

    private static List<Mount> mounts(GameTestHelper helper, BlockPos pos) {
        return PipeMounts.at(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos));
    }

    private static void assertMounts(GameTestHelper helper, BlockPos pos, Mount... expected) {
        List<Mount> mounts = mounts(helper, pos);
        helper.assertTrue(mounts.equals(List.of(expected)), "expected the mounts " + List.of(expected) + " at " + pos + " but found " + mounts);
    }

    @GameTest(template = EMPTY)
    public static void pipeOnASolidBlockStandsOnTwoFeetBesideItsArms(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(3, 2, 1);
        helper.setBlock(pipe.below(), Blocks.STONE);
        helper.setBlock(pipe, RoutedPipes.TRANSPORT_PIPE.get());
        assertMounts(helper, pipe, new Mount(Side.DOWN, Side.NORTH), new Mount(Side.DOWN, Side.SOUTH));

        helper.setBlock(pipe.south(), RoutedPipes.TRANSPORT_PIPE.get());
        assertMounts(helper, pipe, new Mount(Side.DOWN, Side.EAST), new Mount(Side.DOWN, Side.WEST));

        helper.setBlock(pipe.below(), Blocks.AIR);
        helper.assertTrue(mounts(helper, pipe).isEmpty(), "a pipe in the air has no mounts");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pipeHoldsOnToAWallButNotToBlocksWithoutAFullFace(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(3, 2, 1);
        helper.setBlock(pipe, RoutedPipes.TRANSPORT_PIPE.get());
        helper.setBlock(pipe.south(), Blocks.OAK_FENCE);
        helper.setBlock(pipe.east(), Blocks.CHEST);
        helper.assertTrue(mounts(helper, pipe).isEmpty(), "nothing to hold on to at a fence or a chest");

        helper.setBlock(pipe.south(), Blocks.STONE);
        assertMounts(helper, pipe, new Mount(Side.SOUTH, Side.EAST), new Mount(Side.SOUTH, Side.WEST));
        helper.succeed();
    }

    // a routed pipe is connected to the chest under it, so it has nothing to stand on
    @GameTest(template = EMPTY)
    public static void pipeHasNoMountsOnASideWithAnArm(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(3, 2, 1);
        helper.setBlock(pipe.below(), Blocks.CHEST);
        helper.setBlock(pipe.above(), Blocks.STONE);
        helper.setBlock(pipe, RoutedPipes.pipe(PipeKind.BASIC));

        helper.runAfterDelay(3, () -> {
            assertArm(helper, pipe, Direction.DOWN, PipeArm.UNROUTED);
            assertMounts(helper, pipe, new Mount(Side.UP, Side.NORTH), new Mount(Side.UP, Side.SOUTH));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void armGoesWithTheNeighbourAndTheOthersKeepTheirLight(GameTestHelper helper) {
        twoRouters(helper);
        helper.setBlock(FIRST.above(), Blocks.CHEST);

        helper.runAfterDelay(5, () -> {
            helper.setBlock(FIRST.above(), Blocks.AIR);
            assertArm(helper, FIRST, Direction.UP, PipeArm.NONE);
            assertArm(helper, FIRST, Direction.EAST, PipeArm.ROUTED);
            helper.succeed();
        });
    }

    // glasses and block info mods ask a pipe before its first tick, when it has no arms yet
    // it must not stay blind to the chest it connects to a moment later
    @GameTest(template = EMPTY)
    public static void aPipeAskedBeforeItsFirstTickStillFindsItsInventory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container chest = helper.getBlockEntity(pos.above());
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.setBlock(pos, RoutedPipes.pipe(PipeKind.PROVIDER));
        PipeBlockEntity provider = helper.getBlockEntity(pos);
        helper.assertTrue(provider.provided().isEmpty(), "no arm to the chest before the first tick, arms are " + provider.getBlockState());

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!provider.provided().isEmpty(), "the provider sees what is in its chest, arms are " + provider.getBlockState());
            helper.succeed();
        });
    }
}
