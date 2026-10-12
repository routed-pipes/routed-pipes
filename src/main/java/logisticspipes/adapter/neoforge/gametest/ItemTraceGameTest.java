package logisticspipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.adapter.minecraft.ItemTracer;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.menu.UpgradeMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class ItemTraceGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, BlockPos pos) {
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static PipeBlockEntity transport(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LogisticsPipes.TRANSPORT_PIPE.get());
        return helper.getBlockEntity(pos);
    }

    private static void chest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
    }

    private static ItemTracer.Trace trace(GameTestHelper helper, PipeBlockEntity pipe) {
        return ItemTracer.of(pipe, Vec3.atCenterOf(pipe.getBlockPos()))
                .orElseThrow(() -> new IllegalStateException("there should be an item to trace in the pipe"));
    }

    private static String key(GameTestHelper helper, ItemTracer.Trace trace) {
        return ((TranslatableContents) ItemTracer.message(helper.getLevel(), trace).getContents()).getKey();
    }

    private static ItemInteractionResult useController(GameTestHelper helper, TestPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        ItemStack controller = new ItemStack(LogisticsPipes.PIPE_CONTROLLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, controller);
        return helper.getBlockState(relative).useItemOn(controller, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // a provider, two plain pipes, a corner, one more plain pipe and a request pipe
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void traceFollowsThePipesToTheDestination(GameTestHelper helper) {
        GameTestPower.supply(helper);
        List<BlockPos> way = List.of(new BlockPos(0, 1, 1), new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(2, 1, 0),
                new BlockPos(3, 1, 0));
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, way.get(0));
        chest(helper, new BlockPos(0, 2, 1), new ItemStack(Items.COBBLESTONE, 64));
        transport(helper, way.get(1));
        PipeBlockEntity corner = transport(helper, way.get(2));
        transport(helper, way.get(3));
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, way.get(4));
        chest(helper, new BlockPos(3, 2, 0));
        List<BlockPos> absolute = way.stream().map(helper::absolutePos).toList();

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 4))).started(),
                    "request should be possible");
            // the cobblestone is in the provider pipe now and has not picked its way yet
            ItemTracer.Trace trace = trace(helper, provider);
            helper.assertTrue(trace.item().is(Items.COBBLESTONE) && trace.item().getCount() == 4, "four cobblestone: " + trace.item());
            helper.assertTrue(trace.destination().equals(Optional.of(absolute.get(4))),
                    "it is headed for the request pipe: " + trace.destination());
            helper.assertTrue(trace.path().equals(absolute), "its way is " + absolute + " but the trace says " + trace.path());
            helper.assertTrue(key(helper, trace).equals("logisticspipes.trace.headed"), "the message tells where it is headed");
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(!corner.travellingItems().isEmpty(), "the cobblestone is not at the corner right now");
            // in a plain pipe the rest of the way is left
            ItemTracer.Trace trace = trace(helper, corner);
            helper.assertTrue(trace.destination().equals(Optional.of(absolute.get(4))),
                    "it is still headed for the request pipe: " + trace.destination());
            helper.assertTrue(trace.path().equals(absolute.subList(2, 5)),
                    "its way is " + absolute.subList(2, 5) + " but the trace says " + trace.path());
        });
    }

    @GameTest(template = EMPTY)
    public static void anItemNobodyTakesHasNoDestinationToShow(GameTestHelper helper) {
        PipeBlockEntity lonely = transport(helper, new BlockPos(0, 1, 1));
        lonely.accept(new ItemStack(Items.DIRT, 3), Direction.UP);

        ItemTracer.Trace trace = trace(helper, lonely);

        helper.assertTrue(trace.item().is(Items.DIRT) && trace.item().getCount() == 3, "three dirt: " + trace.item());
        helper.assertTrue(trace.destination().isEmpty(), "it is not headed anywhere: " + trace.destination());
        helper.assertTrue(trace.path().equals(List.of(helper.absolutePos(new BlockPos(0, 1, 1)))),
                "only the pipe it is in is on its way: " + trace.path());
        helper.assertTrue(key(helper, trace).equals("logisticspipes.trace.unclaimed"), "the message says so");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void traceIsOfTheItemNearestTheClick(GameTestHelper helper) {
        PipeBlockEntity pipe = transport(helper, new BlockPos(0, 1, 1));
        pipe.accept(new ItemStack(Items.DIRT), Direction.WEST);
        pipe.accept(new ItemStack(Items.SAND), Direction.EAST);
        Vec3 centre = Vec3.atCenterOf(pipe.getBlockPos());

        helper.assertTrue(ItemTracer.of(pipe, centre.add(-0.4, 0, 0)).orElseThrow().item().is(Items.DIRT), "the dirt is on the west side");
        helper.assertTrue(ItemTracer.of(pipe, centre.add(0.4, 0, 0)).orElseThrow().item().is(Items.SAND), "the sand is on the east side");
        helper.succeed();
    }

    // providers without an inventory, so what is put into one waits there
    @GameTest(template = EMPTY)
    public static void controllerTracesWhileItemsAreInThePipeAndOpensOtherwise(GameTestHelper helper) {
        BlockPos busyPos = new BlockPos(0, 1, 1);
        BlockPos idlePos = new BlockPos(2, 1, 1);
        BlockPos plainPos = new BlockPos(4, 1, 1);
        PipeBlockEntity busy = pipe(helper, PipeKind.PROVIDER, busyPos);
        pipe(helper, PipeKind.PROVIDER, idlePos);
        PipeBlockEntity plain = transport(helper, plainPos);
        busy.accept(new ItemStack(Items.DIRT), Direction.UP);
        TestPlayer player = TestPlayer.in(helper);

        helper.assertTrue(useController(helper, player, busyPos).consumesAction(), "the controller does something on a busy pipe");
        helper.assertTrue(player.containerMenu instanceof UpgradeMenu, "a busy routed pipe still opens its controller");
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(busy.travellingItems().size() == 1, "tracing leaves the item where it is");

        useController(helper, player, idlePos);
        helper.assertTrue(player.containerMenu instanceof UpgradeMenu, "on an empty pipe the controller opens as before");

        helper.assertTrue(useController(helper, player, plainPos) == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "the controller does nothing on an empty plain pipe");
        plain.accept(new ItemStack(Items.DIRT), Direction.UP);
        helper.assertTrue(useController(helper, player, plainPos).consumesAction(), "items in plain pipes can be traced too");
        helper.succeed();
    }

    private static List<PipeBlockEntity> row(GameTestHelper helper, int length) {
        List<PipeBlockEntity> pipes = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            pipes.add(transport(helper, new BlockPos(x, 1, 1)));
        }
        return pipes;
    }

    // a row of plain pipes, the first one is clicked after the item has moved on
    @GameTest(template = EMPTY)
    public static void traceFindsAnItemThatHasLeftTheClickedPipe(GameTestHelper helper) {
        List<PipeBlockEntity> pipes = row(helper, 5);
        Vec3 click = Vec3.atCenterOf(pipes.get(0).getBlockPos());

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(ItemTracer.of(pipes.get(0), click).isEmpty(), "nothing to trace in empty pipes");
            pipes.get(ItemTracer.SEARCH_DISTANCE).accept(new ItemStack(Items.DIRT, 3), Direction.UP);
            ItemTracer.Trace far = ItemTracer.of(pipes.get(0), click).orElseThrow(
                    () -> new IllegalStateException("the dirt three pipes on should be found"));
            helper.assertTrue(far.item().is(Items.DIRT) && far.item().getCount() == 3, "three dirt: " + far.item());
            helper.assertTrue(far.path().get(0).equals(pipes.get(ItemTracer.SEARCH_DISTANCE).getBlockPos()),
                    "its way starts at the pipe it is in: " + far.path());

            pipes.get(1).accept(new ItemStack(Items.SAND), Direction.UP);
            helper.assertTrue(ItemTracer.of(pipes.get(0), click).orElseThrow().item().is(Items.SAND), "the sand next door is nearer");
            helper.assertTrue(ItemTracer.of(pipes.get(4), Vec3.atCenterOf(pipes.get(4).getBlockPos())).orElseThrow().item()
                    .is(Items.DIRT), "from the other end the dirt is nearer");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void traceDoesNotLookFurtherThanAFewPipes(GameTestHelper helper) {
        List<PipeBlockEntity> pipes = row(helper, ItemTracer.SEARCH_DISTANCE + 2);
        // next to the row but not connected to it, there is a block of air between them
        PipeBlockEntity apart = transport(helper, new BlockPos(0, 1, 3));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            pipes.get(ItemTracer.SEARCH_DISTANCE + 1).accept(new ItemStack(Items.DIRT), Direction.UP);
            apart.accept(new ItemStack(Items.SAND), Direction.UP);
            helper.assertTrue(ItemTracer.of(pipes.get(0), Vec3.atCenterOf(pipes.get(0).getBlockPos())).isEmpty(),
                    "an item four pipes on or in a pipe that is not connected is not the one that was clicked");
            helper.assertTrue(useController(helper, player, new BlockPos(0, 1, 1)) == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "the controller does nothing on a plain pipe with nothing near");
            helper.assertTrue(useController(helper, player, new BlockPos(1, 1, 1)).consumesAction(),
                    "one pipe on the item is in reach of the click");
            helper.succeed();
        });
    }
}
