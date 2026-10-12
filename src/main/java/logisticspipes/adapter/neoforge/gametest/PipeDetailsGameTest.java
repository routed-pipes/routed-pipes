package logisticspipes.adapter.neoforge.gametest;

import java.util.List;

import logisticspipes.adapter.minecraft.CraftingTableBlockEntity;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PipeDetails;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
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

// what the tooltip of a block info mod is told about a pipe
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class PipeDetailsGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    // the way it reaches the client: made on the server, saved to a tag and read back
    private static PipeDetails sent(GameTestHelper helper, PipeBlockEntity pipe) {
        return PipeDetails.load(PipeDetails.of(pipe).save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
    }

    private static List<Item> items(List<PipeDetails.Entry> entries) {
        return entries.stream().map(entry -> entry.stack().getItem()).toList();
    }

    @GameTest(template = EMPTY)
    public static void basicPipeTellsItsFilterCutShortAndItsDefaultRoute(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        Item[] filter = {Items.STONE, Items.COAL, Items.DIAMOND, Items.COBBLESTONE, Items.IRON_INGOT};
        for (int slot = 0; slot < filter.length; slot++) {
            basic.config().setItem(slot, new ItemStack(filter[slot]));
        }

        PipeDetails details = sent(helper, basic);
        helper.assertTrue(items(details.listed().shown()).equals(List.of(Items.COAL, Items.COBBLESTONE, Items.DIAMOND)),
                "the first three listed items by id, got " + details.listed());
        helper.assertTrue(details.listed().more() == 2, "two more are only counted");
        helper.assertTrue(!details.defaultRoute() && details.lines().size() == 1, "one line for the filter");

        basic.setDefaultRoute(true);
        details = sent(helper, basic);
        helper.assertTrue(details.defaultRoute() && details.lines().size() == 2, "the default route gets its own line");
        helper.assertTrue(details.modules().isEmpty() && details.orders().isEmpty(), "no modules and no orders in a basic pipe");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pipesWithoutSettingsHaveNothingToTell(GameTestHelper helper) {
        GameTestPower.supply(helper);
        helper.assertTrue(sent(helper, pipe(helper, PipeKind.BASIC, 0)).lines().isEmpty(), "a fresh basic pipe");
        helper.assertTrue(sent(helper, pipe(helper, PipeKind.REQUEST, 2)).lines().isEmpty(), "a request pipe");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void chassisTellsItsModulesAndWhatTheyTake(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos pos = new BlockPos(0, 1, 1);
        helper.setBlock(pos, LogisticsPipes.CHASSIS_PIPES.get(8).get());
        PipeBlockEntity chassis = helper.getBlockEntity(pos);
        ItemStack sink = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get());
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), true);
        chassis.modules().setItem(0, sink);
        chassis.modules().setItem(1, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.EXTRACTOR).get()));
        chassis.modules().setItem(3, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.PROVIDER).get()));
        chassis.modules().setItem(4, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.POLYMORPHIC_ITEM_SINK).get()));

        PipeDetails details = sent(helper, chassis);
        helper.assertTrue(items(details.modules().shown()).equals(List.of(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get(),
                LogisticsPipes.MODULES.get(ModuleKind.EXTRACTOR).get(), LogisticsPipes.MODULES.get(ModuleKind.PROVIDER).get())),
                "the first three modules in slot order, got " + details.modules());
        helper.assertTrue(details.modules().more() == 1, "the fourth module is only counted");
        helper.assertTrue(items(details.listed().shown()).equals(List.of(Items.DIAMOND)), "the item sink module lists diamonds");
        helper.assertTrue(details.defaultRoute(), "the item sink module is a default route");
        helper.assertTrue(details.lines().size() == 3, "modules, filter and default route");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void craftingPipeTellsItsOpenOrdersUntilTheyAreShipped(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.CHEST);
        pipe(helper, PipeKind.PROVIDER, 1);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        Container logs = helper.getBlockEntity(new BlockPos(1, 2, 1));
        logs.setItem(0, new ItemStack(Items.OAK_LOG, 16));
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 2);
        helper.setBlock(new BlockPos(2, 2, 1), LogisticsPipes.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(new BlockPos(2, 2, 1));
        table.pattern().setItem(0, new ItemStack(Items.OAK_LOG));
        crafting.importRecipe();

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(sent(helper, crafting).orders().isEmpty(), "nothing ordered yet");
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 6))).started(), "first request");
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 4))).started(), "second request");

            PipeDetails details = sent(helper, crafting);
            helper.assertTrue(details.orders().shown().size() == 1 && details.orders().shown().get(0).stack().is(Items.OAK_PLANKS)
                    && details.orders().shown().get(0).count() == 10, "both orders add up to ten planks, got " + details.orders());
            helper.assertTrue(details.lines().size() == 1, "one line for the orders");

            helper.succeedWhen(() -> helper.assertTrue(sent(helper, crafting).orders().isEmpty(), "orders stay listed after shipping"));
        });
    }
}
