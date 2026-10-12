package logisticspipes.adapter.neoforge.gametest;

import java.util.List;

import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.SettingsCopierItem;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.ProviderMode;
import logisticspipes.domain.logistics.ProviderSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class SettingsCopierGameTest {

    private static final String EMPTY = "empty";

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static PipeBlockEntity chassis(GameTestHelper helper, int slots, int x, ItemStack... modules) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.CHASSIS_PIPES.get(slots).get());
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        for (int slot = 0; slot < modules.length; slot++) {
            pipe.modules().setItem(slot, modules[slot]);
        }
        return pipe;
    }

    private static ItemStack module(ModuleKind kind) {
        return new ItemStack(LogisticsPipes.MODULES.get(kind).get());
    }

    private static TestPlayer playerWithCopier(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LogisticsPipes.SETTINGS_COPIER.get()));
        return player;
    }

    // a right click with the copier on the pipe at x, in the order the game does it:
    // sneaking skips the block, otherwise the block is asked first and has to let the item through
    private static void use(GameTestHelper helper, TestPlayer player, int x, boolean sneaking) {
        BlockPos pos = new BlockPos(x, 1, 1);
        ItemStack copier = player.getMainHandItem();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
        player.setShiftKeyDown(sneaking);
        if (!sneaking) {
            ItemInteractionResult result = helper.getBlockState(pos).useItemOn(copier, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "the pipe must leave the click to the copier");
        }
        copier.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, copier, hit));
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "using the copier never opens the pipe");
    }

    private static List<String> storedKeys(ItemStack copier) {
        return SettingsCopierItem.stored(copier).stream()
                .map(line -> ((TranslatableContents) line.getContents()).getKey()).toList();
    }

    @GameTest(template = EMPTY)
    public static void copiesProviderSettingsToAnotherProvider(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity source = pipe(helper, PipeKind.PROVIDER, 0);
        source.config().setItem(0, new ItemStack(Items.IRON_INGOT));
        source.config().setItem(4, new ItemStack(Items.COAL, 7));
        source.setMode(ProviderMode.LEAVE_ONE_PER_STACK.ordinal());
        source.setExclude(true);
        PipeBlockEntity target = pipe(helper, PipeKind.PROVIDER, 2);
        target.config().setItem(8, new ItemStack(Items.DIAMOND));
        TestPlayer player = playerWithCopier(helper);
        ItemStack copier = player.getMainHandItem();

        helper.assertTrue(SettingsCopierItem.stored(copier).isEmpty(), "a fresh copier holds nothing");
        use(helper, player, 2, false);
        helper.assertTrue(target.config().getItem(8).is(Items.DIAMOND), "pasting from an empty copier changes nothing");

        use(helper, player, 0, true);
        helper.assertTrue(SettingsCopierItem.hasSettings(copier), "sneak use copies");
        helper.assertTrue(storedKeys(copier).equals(List.of("logisticspipes.settings_copier.stored", "logisticspipes.settings_copier.markers",
                "logisticspipes.gui.provider_mode." + ProviderMode.LEAVE_ONE_PER_STACK.ordinal(), "logisticspipes.gui.exclude")),
                "the tooltip says what is stored, got " + storedKeys(copier));
        helper.assertTrue(source.config().getItem(0).is(Items.IRON_INGOT) && source.mode() == ProviderMode.LEAVE_ONE_PER_STACK.ordinal(),
                "copying leaves the pipe as it was");

        use(helper, player, 2, false);
        helper.assertTrue(target.config().getItem(0).is(Items.IRON_INGOT), "first marker");
        helper.assertTrue(target.config().getItem(4).is(Items.COAL) && target.config().getItem(4).getCount() == 7, "marker with its count");
        helper.assertTrue(target.config().getItem(8).isEmpty(), "markers the source does not have are cleared");
        helper.assertTrue(target.mode() == ProviderMode.LEAVE_ONE_PER_STACK.ordinal() && target.exclude(), "mode and exclude flag");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void copiesSatelliteNumberAndRecipeOfACraftingPipe(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity source = pipe(helper, PipeKind.CRAFTING, 0);
        source.config().setItem(PipeBlockEntity.SATELLITE_ROW, new ItemStack(Items.RAW_IRON, 2));
        source.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        source.setSatelliteId(7);
        PipeBlockEntity target = pipe(helper, PipeKind.CRAFTING, 2);
        TestPlayer player = playerWithCopier(helper);

        use(helper, player, 0, true);
        use(helper, player, 2, false);

        helper.assertTrue(target.satelliteId() == 7, "satellite number");
        helper.assertTrue(target.recipe().isPresent() && target.recipe().equals(source.recipe()), "recipe");
        helper.assertTrue(target.satelliteInputs().equals(source.satelliteInputs()), "satellite row");
        helper.assertTrue(storedKeys(player.getMainHandItem()).contains("logisticspipes.settings_copier.satellite"), "tooltip names the satellite");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void settingsOnlyFitPipesOfTheSameKind(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        basic.config().setItem(0, new ItemStack(Items.DIAMOND));
        basic.setDefaultRoute(true);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 2);
        int supplyMode = supplier.mode();
        helper.setBlock(new BlockPos(4, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        TestPlayer player = playerWithCopier(helper);

        use(helper, player, 0, true);
        use(helper, player, 2, false);
        helper.assertTrue(supplier.config().isEmpty() && supplier.mode() == supplyMode && supplier.supplyTargets().isEmpty(),
                "a supplier does not take the settings of a basic pipe");

        ItemStack copier = player.getMainHandItem();
        BlockPos plain = helper.absolutePos(new BlockPos(4, 1, 1));
        player.setShiftKeyDown(true);
        copier.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, copier,
                new BlockHitResult(Vec3.atCenterOf(plain), Direction.UP, plain, false)));
        helper.assertTrue(storedKeys(copier).contains("logisticspipes.status.default_route"), "a plain pipe has no settings to copy over the old ones");
        helper.succeed();
    }

    // the pasted filter has to reach the network, or the diamonds would not find the pipe
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pastedFilterIsKnownToTheNetwork(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity template = pipe(helper, PipeKind.BASIC, 1);
        template.config().setItem(0, new ItemStack(Items.DIAMOND));
        PipeBlockEntity target = pipe(helper, PipeKind.BASIC, 2);
        BlockPos chest = new BlockPos(2, 2, 1);
        helper.setBlock(chest, Blocks.CHEST);
        TestPlayer player = playerWithCopier(helper);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(target.sinkRules().isEmpty(), "the target takes nothing at first");
            use(helper, player, 1, true);
            use(helper, player, 2, false);
            helper.assertTrue(target.sinkRules().equals(List.of(new ItemSink(template.supplyTargets().keySet(), false))),
                    "the pipe has to forget the sink rules it had worked out");
            start.accept(new ItemStack(Items.DIAMOND, 5), Direction.DOWN);
        });

        helper.succeedWhen(() -> {
            Container container = helper.getBlockEntity(chest);
            helper.assertTrue(container.countItem(Items.DIAMOND) == 5, "the diamonds arrive at the pipe the filter was pasted onto");
        });
    }

    @GameTest(template = EMPTY)
    public static void chassisSettingsGoOntoModulesOfTheSameKindInTheSameSlot(GameTestHelper helper) {
        GameTestPower.supply(helper);
        ItemStack sink = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD)), true);
        ItemStack provider = module(ModuleKind.PROVIDER);
        ModuleItem.configureProvider(provider, List.of(new ItemStack(Items.COAL)), ProviderMode.LEAVE_FIRST, true);
        ItemStack supplier = module(ModuleKind.PASSIVE_SUPPLIER);
        ModuleItem.configureSink(supplier, List.of(new ItemStack(Items.TORCH, 32)), false);
        PipeBlockEntity source = chassis(helper, 4, 0, sink, provider, supplier, module(ModuleKind.EXTRACTOR));

        ItemStack otherSink = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(otherSink, List.of(new ItemStack(Items.DIRT)), false);
        ItemStack terminus = module(ModuleKind.TERMINUS);
        ModuleItem.configureSink(terminus, List.of(new ItemStack(Items.GRAVEL)), false);
        // a smaller chassis: same kind in slot 0, another kind in slot 1, slot 2 left empty
        PipeBlockEntity target = chassis(helper, 3, 2, otherSink, terminus);
        TestPlayer player = playerWithCopier(helper);

        use(helper, player, 0, true);
        helper.assertTrue(storedKeys(player.getMainHandItem()).equals(List.of("logisticspipes.settings_copier.stored",
                "logisticspipes.status.modules")), "the tooltip names the modules, got " + storedKeys(player.getMainHandItem()));
        Component modules = SettingsCopierItem.stored(player.getMainHandItem()).get(1);
        helper.assertTrue(((Component) ((TranslatableContents) modules.getContents()).getArgs()[0]).getSiblings().size() == 7,
                "three module names and the count of the rest, with commas between");
        use(helper, player, 2, false);

        ItemSink pasted = ModuleItem.sinkOf(target.modules().getItem(0));
        helper.assertTrue(pasted.equals(ModuleItem.sinkOf(sink)) && pasted.defaultRoute() && pasted.filter().size() == 2,
                "the item sink in slot 0 takes over filter and default route");
        helper.assertTrue(target.modules().getItem(1).getItem() == terminus.getItem()
                && ModuleItem.filterOf(target.modules().getItem(1)).get(0).is(Items.GRAVEL), "the terminus in slot 1 is another kind and stays as it is");
        helper.assertTrue(target.modules().getItem(2).isEmpty() && target.modules().getItem(3).isEmpty(), "no module is copied, only settings");
        helper.assertTrue(source.modules().getItem(0).getItem() == sink.getItem(), "the source keeps its modules");
        helper.assertTrue(target.sinkRules().contains(ModuleItem.sinkOf(sink)), "the chassis works with the pasted settings right away");

        // now the provider settings onto a provider in the same slot
        target.modules().setItem(1, module(ModuleKind.PROVIDER));
        use(helper, player, 2, false);
        ProviderSettings settings = ModuleItem.providerOf(target.modules().getItem(1));
        helper.assertTrue(settings.equals(ModuleItem.providerOf(provider)) && settings.mode() == ProviderMode.LEAVE_FIRST && settings.exclude(),
                "provider mode, list and exclude flag come along");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pipeStillOpensForOtherItems(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 0);
        TestPlayer player = TestPlayer.in(helper);
        BlockPos pos = new BlockPos(0, 1, 1);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
        ItemStack stick = new ItemStack(Items.STICK);

        ItemInteractionResult result = helper.getBlockState(pos).useItemOn(stick, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "anything else falls through to opening the pipe");
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        helper.assertTrue(player.containerMenu instanceof PipeConfigMenu, "settings open as before");
        helper.succeed();
    }
}
