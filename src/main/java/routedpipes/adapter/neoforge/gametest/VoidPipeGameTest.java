package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.Set;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.VoidSink;
import routedpipes.domain.statistics.PipeStatistics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// a basic pipe that takes nothing at x 0 where items come in, a void pipe further along the row
@GameTestHolder(Content.MODID)
@PrefixGameTestTemplate(false)
public class VoidPipeGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos MIDDLE = new BlockPos(3, 1, 1);

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static void transport(GameTestHelper helper, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Content.TRANSPORT_PIPE.get());
        }
    }

    private static int destroyed(PipeBlockEntity voidPipe) {
        return voidPipe.statistics().session(PipeStatistics.Kind.RECEIVED);
    }

    // how many of the item are still on their way through the row
    private static int inThePipes(GameTestHelper helper, Item item) {
        int count = 0;
        for (int x = 0; x < 8; x++) {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 1))) instanceof PipeBlockEntity pipe) {
                count += pipe.travellingItems().stream().filter(travelling -> McItemType.stackOf(travelling.batch()).is(item))
                        .mapToInt(travelling -> travelling.batch().count()).sum();
            }
        }
        return count;
    }

    @GameTest(template = EMPTY, timeoutTicks = 190)
    public static void anItemNobodyHasRoomForIsDestroyedAndNothingDrops(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity entry = pipe(helper, PipeKind.BASIC, 0);
        transport(helper, 1, 2);
        PipeBlockEntity voidPipe = pipe(helper, PipeKind.VOID, 3);
        helper.onEachTick(() -> helper.assertItemEntityNotPresent(Items.DIAMOND, MIDDLE, 8));

        entry.accept(new ItemStack(Items.DIAMOND, 3), Direction.DOWN);

        // well before an item without a destination would be given up on and pop out of its pipe
        helper.succeedWhen(() -> {
            helper.assertTrue(destroyed(voidPipe) == 3, "the void pipe took " + destroyed(voidPipe) + " of 3");
            helper.assertTrue(inThePipes(helper, Items.DIAMOND) == 0, "still in the pipes: " + inThePipes(helper, Items.DIAMOND));
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aDefaultRouteWithRoomGetsTheItemAndTheVoidPipeOnlyPassesItOn(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity entry = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity voidPipe = pipe(helper, PipeKind.VOID, 1);
        transport(helper, 2, 3);
        pipe(helper, PipeKind.BASIC, 4).setDefaultRoute(true);
        BlockPos chest = new BlockPos(4, 2, 1);
        helper.setBlock(chest, Blocks.CHEST);

        entry.accept(new ItemStack(Items.DIAMOND, 3), Direction.DOWN);

        helper.succeedWhen(() -> {
            Container container = helper.getBlockEntity(chest);
            helper.assertTrue(container.countItem(Items.DIAMOND) == 3, "the chest holds " + container.countItem(Items.DIAMOND) + " of 3");
            helper.assertTrue(destroyed(voidPipe) == 0, "the nearer void pipe destroyed " + destroyed(voidPipe));
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aVoidPipeWithAListDestroysOnlyWhatIsListed(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity entry = pipe(helper, PipeKind.BASIC, 0);
        transport(helper, 1, 2);
        PipeBlockEntity voidPipe = pipe(helper, PipeKind.VOID, 3);
        voidPipe.config().setItem(0, new ItemStack(Items.COBBLESTONE));

        entry.accept(new ItemStack(Items.DIAMOND, 3), Direction.DOWN);
        entry.accept(new ItemStack(Items.COBBLESTONE, 5), Direction.DOWN);

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(destroyed(voidPipe) == 5, "only the cobblestone is gone, the void pipe took " + destroyed(voidPipe));
            helper.assertTrue(inThePipes(helper, Items.DIAMOND) == 3, "the diamonds wait in the pipes, there are "
                    + inThePipes(helper, Items.DIAMOND));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void theListSurvivesSaveAndLoad(GameTestHelper helper) {
        PipeBlockEntity voidPipe = pipe(helper, PipeKind.VOID, 1);
        voidPipe.config().setItem(0, new ItemStack(Items.COBBLESTONE));

        PipeBlockEntity loaded = pipe(helper, PipeKind.VOID, 3);
        loaded.loadWithComponents(voidPipe.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());

        helper.assertTrue(loaded.sinkRules().equals(voidPipe.sinkRules()) && loaded.sinkRules().size() == 1
                && loaded.sinkRules().get(0).equals(new VoidSink(Set.of(McItemType.batchOf(new ItemStack(Items.COBBLESTONE)).type()))),
                "the loaded pipe destroys cobblestone only, got " + loaded.sinkRules());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itsScreenIsTheOneOfABasicPipeWithoutTheDefaultRoute(GameTestHelper helper) {
        PipeBlockEntity voidPipe = pipe(helper, PipeKind.VOID, 1);
        TestPlayer player = TestPlayer.in(helper);

        player.openMenu(voidPipe.menuProvider());

        helper.assertTrue(player.containerMenu instanceof PipeConfigMenu menu && menu.kind() == PipeKind.VOID,
                "the settings of a void pipe open");
        player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_DEFAULT_ROUTE);
        helper.assertTrue(!voidPipe.defaultRoute(), "there is no default route to switch on");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itIsCraftedFromABasicPipeAnInkSacAndRedstone(GameTestHelper helper) {
        ItemStack crafted = RecipeGameTest.crafted(helper, 3, 1, Content.pipe(PipeKind.BASIC).asItem(), Items.INK_SAC, Items.REDSTONE);

        helper.assertTrue(crafted.is(Content.pipe(PipeKind.VOID).asItem()) && crafted.getCount() == 1, "got " + crafted);
        helper.succeed();
    }
}
