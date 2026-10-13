package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// an opaque pipe keeps what travels through it from the clients
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class OpaqueUpgradeGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos PIPE = new BlockPos(3, 1, 1);

    private static ItemStack opaque() {
        return new ItemStack(LogisticsPipes.UPGRADES.get(UpgradeKind.OPAQUE).get());
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LogisticsPipes.pipe(PipeKind.BASIC));
        return helper.getBlockEntity(pos);
    }

    private static CompoundTag forClients(GameTestHelper helper, PipeBlockEntity pipe) {
        return pipe.getUpdateTag(helper.getLevel().registryAccess());
    }

    @GameTest(template = EMPTY)
    public static void opaqueUpgradeGoesIntoRoutedPipesOnly(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PIPE);
        helper.setBlock(PIPE.east(), LogisticsPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity plain = helper.getBlockEntity(PIPE.east());

        helper.assertTrue(!basic.opaque(), "a fresh pipe is not opaque");
        helper.assertTrue(basic.installUpgrade(opaque()), "the upgrade should fit a routed pipe");
        helper.assertTrue(basic.opaque(), "the pipe is opaque with the upgrade");
        helper.assertTrue(!plain.installUpgrade(opaque()) && !plain.opaque(), "a plain pipe takes no upgrades");
        basic.removeUpgrades();
        helper.assertTrue(!basic.opaque(), "and see-through again without it");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void clientsAreToldTheItemsOfAPipeUnlessItIsOpaque(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PIPE);
        basic.accept(new ItemStack(Items.DIAMOND, 3), Direction.WEST);

        CompoundTag open = forClients(helper, basic);
        helper.assertTrue(open.getList("Items", Tag.TAG_COMPOUND).size() == 1 && !open.getBoolean("Opaque"),
                "clients get the travelling item of a plain routed pipe");

        basic.installUpgrade(opaque());
        CompoundTag hidden = forClients(helper, basic);
        helper.assertTrue(!hidden.contains("Items") && hidden.getBoolean("Opaque"), "clients get no items of an opaque pipe");
        helper.assertTrue(basic.travellingItems().size() == 1, "the item is still in the pipe");
        helper.succeed();
    }

    // what a client does with the update: the items it showed before the upgrade went in are gone
    @GameTest(template = EMPTY)
    public static void anUpdateFromAnOpaquePipeEmptiesWhatWasShown(GameTestHelper helper) {
        PipeBlockEntity opaque = pipe(helper, PIPE);
        opaque.installUpgrade(opaque());
        PipeBlockEntity viewed = pipe(helper, PIPE.east().east());
        viewed.accept(new ItemStack(Items.DIAMOND, 3), Direction.WEST);

        viewed.loadWithComponents(forClients(helper, opaque), helper.getLevel().registryAccess());

        helper.assertTrue(viewed.travellingItems().isEmpty(), "nothing left to draw");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void itemsStillTravelThroughAnOpaquePipe(GameTestHelper helper) {
        GameTestPower.junction(helper, PIPE.south(), PowerJunctionBlockEntity.CAPACITY);
        PipeBlockEntity first = pipe(helper, PIPE);
        PipeBlockEntity middle = pipe(helper, PIPE.east());
        PipeBlockEntity last = pipe(helper, PIPE.east().east());
        middle.installUpgrade(opaque());
        last.setDefaultRoute(true);
        BlockPos chest = PIPE.east().east().above();
        helper.setBlock(chest, Blocks.CHEST);

        helper.runAfterDelay(15, () -> first.accept(new ItemStack(Items.DIAMOND, 3), Direction.WEST));
        helper.succeedWhen(() -> {
            Container contents = helper.getBlockEntity(chest);
            helper.assertTrue(contents.countItem(Items.DIAMOND) == 3, "the diamonds have not arrived");
        });
    }
}
