package routedpipes.adapter.neoforge.gametest;

import java.util.List;

import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.domain.logistics.ModuleKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class BulkStorageGameTest {

    private static final String EMPTY = "empty";

    private static ItemStack module(ModuleKind kind) {
        return new ItemStack(RoutedPipes.MODULES.get(kind).get());
    }

    private static ItemStack defaultRoute() {
        ItemStack sink = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(sink, List.of(), true);
        return sink;
    }

    private static PipeBlockEntity chassis(GameTestHelper helper, int x, ItemStack module) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, RoutedPipes.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.modules().setItem(0, module);
        return pipe;
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

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        int count = ((Container) helper.getBlockEntity(pos)).countItem(item);
        helper.assertTrue(count == expected, "expected " + expected + " " + item + " at " + pos + " but found " + count);
    }

    // an extractor, then two chests behind default routes, the far one already holds some coal
    // with the module at the end coal goes to the far chest and the rest to the near one, without it all goes to the near one
    private static void storageArea(GameTestHelper helper, boolean withModule) {
        GameTestPower.supply(helper);
        chassis(helper, 0, module(ModuleKind.EXTRACTOR));
        BlockPos source = chest(helper, 0, new ItemStack(Items.COAL, 20), new ItemStack(Items.DIRT, 9));
        chassis(helper, 1, defaultRoute());
        BlockPos near = chest(helper, 1);
        chassis(helper, 2, defaultRoute());
        BlockPos far = chest(helper, 2, new ItemStack(Items.COAL, 1));
        if (withModule) {
            chassis(helper, 3, module(ModuleKind.BULK_STORAGE_SINK));
        }

        helper.succeedWhen(() -> {
            assertCount(helper, source, Items.COAL, 0);
            assertCount(helper, source, Items.DIRT, 0);
            assertCount(helper, near, Items.COAL, withModule ? 0 : 20);
            assertCount(helper, far, Items.COAL, withModule ? 21 : 1);
            assertCount(helper, near, Items.DIRT, 9);
            assertCount(helper, far, Items.DIRT, 0);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void withTheModuleItemsGoWhereTheyAreAlreadyStored(GameTestHelper helper) {
        storageArea(helper, true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void withoutTheModuleTheNearestDefaultRouteTakesEverything(GameTestHelper helper) {
        storageArea(helper, false);
    }

    // coal put in by hand is noticed, the next coal follows it
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void whatIsPutIntoAStoreLaterIsFoundToo(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity extractor = chassis(helper, 0, module(ModuleKind.EXTRACTOR));
        BlockPos source = chest(helper, 0);
        chassis(helper, 1, defaultRoute());
        BlockPos near = chest(helper, 1);
        chassis(helper, 2, defaultRoute());
        BlockPos far = chest(helper, 2);
        chassis(helper, 3, module(ModuleKind.BULK_STORAGE_SINK));

        helper.runAfterDelay(40, () -> ((Container) helper.getBlockEntity(far)).setItem(0, new ItemStack(Items.COAL, 1)));
        helper.runAfterDelay(60, () -> ((Container) helper.getBlockEntity(source)).setItem(0, new ItemStack(Items.COAL, 10)));
        helper.succeedWhen(() -> {
            helper.assertTrue(extractor.travellingItems().isEmpty(), "the coal is still on its way");
            assertCount(helper, source, Items.COAL, 0);
            assertCount(helper, near, Items.COAL, 0);
            assertCount(helper, far, Items.COAL, 11);
        });
    }
}
