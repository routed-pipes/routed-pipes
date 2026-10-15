package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class ItemConservationGameTest {

    private static final String EMPTY = "empty";

    // FINDING: ordered items dropped by breaking a pipe are not reported lost, so the supplier waits for them for a minute
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void supplierOrdersAgainWhenAPipeWithItsItemsIsBroken(GameTestHelper helper) {
        GameTestPower.supply(helper);
        helper.setBlock(new BlockPos(0, 1, 1), Content.pipe(PipeKind.SUPPLIER));
        PipeBlockEntity supplier = helper.getBlockEntity(new BlockPos(0, 1, 1));
        supplier.config().setItem(0, new ItemStack(Items.COAL, 16));
        BlockPos target = new BlockPos(0, 2, 1);
        helper.setBlock(target, Blocks.CHEST);
        for (int x = 1; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Content.TRANSPORT_PIPE.get());
        }
        helper.setBlock(new BlockPos(9, 1, 1), Content.pipe(PipeKind.PROVIDER));
        BlockPos source = new BlockPos(9, 2, 1);
        helper.setBlock(source, Blocks.CHEST);
        Container stock = helper.getBlockEntity(source);
        stock.setItem(0, new ItemStack(Items.COAL, 64));

        BlockPos middle = new BlockPos(5, 1, 1);
        boolean[] broken = {false};
        helper.onEachTick(() -> {
            if (!broken[0] && helper.getBlockEntity(middle) instanceof PipeBlockEntity pipe && !pipe.travellingItems().isEmpty()) {
                // the coal falls out, the pipe is put back right away
                broken[0] = true;
                helper.destroyBlock(middle);
                helper.setBlock(middle, Content.TRANSPORT_PIPE.get());
            }
        });

        // well before the order would time out on its own
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(broken[0], "the coal never passed the middle pipe");
            Container chest = helper.getBlockEntity(target);
            int count = chest.countItem(Items.COAL);
            helper.assertTrue(count == 16, "expected the supplier to order again and hold 16 coal but it holds " + count);
            helper.succeed();
        });
    }
}
