package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.Optional;
import java.util.Set;

import routedpipes.adapter.minecraft.PlacementPreview;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// what a player holding a pipe is shown before placing it
public class PlacementPreviewGameTest {

    private static final String EMPTY = "empty";
    // the pipe would go on top of a stone here
    private static final BlockPos BASE = new BlockPos(2, 1, 2);
    private static final BlockPos SPOT = BASE.above();

    // a chest above the spot, a plain pipe east of it and a junction north of it
    private static void surroundings(GameTestHelper helper) {
        helper.setBlock(BASE, Blocks.STONE);
        helper.setBlock(SPOT.above(), Blocks.CHEST);
        helper.setBlock(SPOT.east(), Content.TRANSPORT_PIPE.get());
        helper.setBlock(SPOT.north(), Content.POWER_JUNCTION.get());
    }

    private static BlockHitResult topOfBase(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BASE);
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    private static TestPlayer holding(GameTestHelper helper, InteractionHand hand, ItemLike item) {
        TestPlayer player = TestPlayer.in(helper);
        player.setItemInHand(hand, new ItemStack(item));
        return player;
    }

    private static void assertSides(GameTestHelper helper, TestPlayer player, Set<Direction> sides) {
        PlacementPreview.Preview preview = PlacementPreview.of(player, topOfBase(helper))
                .orElseThrow(() -> new IllegalStateException("a pipe can be placed on the stone"));
        helper.assertTrue(preview.pos().equals(helper.absolutePos(SPOT)), "the pipe would go on top of the stone: " + preview.pos());
        helper.assertTrue(preview.sides().equals(sides), "it would connect on " + sides + " but the preview says " + preview.sides());
    }

    @GameTest(template = EMPTY)
    public static void routedPipeWouldConnectToInventoryPipeAndJunction(GameTestHelper helper) {
        surroundings(helper);

        assertSides(helper, holding(helper, InteractionHand.MAIN_HAND, Content.pipe(PipeKind.BASIC)),
                Set.of(Direction.UP, Direction.EAST, Direction.NORTH));
        assertSides(helper, holding(helper, InteractionHand.OFF_HAND, Content.pipe(PipeKind.BASIC)),
                Set.of(Direction.UP, Direction.EAST, Direction.NORTH));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void plainPipeWouldOnlyConnectToThePipe(GameTestHelper helper) {
        surroundings(helper);

        assertSides(helper, holding(helper, InteractionHand.MAIN_HAND, Content.TRANSPORT_PIPE.get()), Set.of(Direction.EAST));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void noPreviewWithoutAPipeInTheHandOrWithoutRoom(GameTestHelper helper) {
        surroundings(helper);

        helper.assertTrue(PlacementPreview.of(holding(helper, InteractionHand.MAIN_HAND, Items.STICK), topOfBase(helper)).isEmpty(),
                "a stick is not a pipe");
        helper.assertTrue(PlacementPreview.of(holding(helper, InteractionHand.MAIN_HAND, Blocks.STONE), topOfBase(helper)).isEmpty(),
                "neither is a stone");
        helper.setBlock(SPOT, Blocks.STONE);
        helper.assertTrue(PlacementPreview.of(holding(helper, InteractionHand.MAIN_HAND, Content.pipe(PipeKind.BASIC)),
                topOfBase(helper)).isEmpty(), "there is no room for a pipe where a stone is");
        helper.succeed();
    }

    // the preview has to show what placing the pipe then does
    @GameTest(template = EMPTY)
    public static void placedPipeLooksLikeItsPreview(GameTestHelper helper) {
        surroundings(helper);
        TestPlayer player = holding(helper, InteractionHand.MAIN_HAND, Content.pipe(PipeKind.BASIC));
        Optional<PlacementPreview.Preview> preview = PlacementPreview.of(player, topOfBase(helper));
        ItemStack stack = player.getMainHandItem();

        helper.assertTrue(((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                topOfBase(helper))).consumesAction(), "the pipe should be placed");

        helper.assertTrue(preview.isPresent() && helper.getBlockState(SPOT).equals(preview.get().state()),
                "the pipe was placed as " + helper.getBlockState(SPOT) + " but the preview was " + preview);
        helper.succeed();
    }
}
