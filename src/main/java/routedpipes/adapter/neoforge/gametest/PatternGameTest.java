package routedpipes.adapter.neoforge.gametest;

import java.util.List;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.ChassisMenu;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.SupplyMode;
import routedpipes.domain.logistics.UpgradeKind;
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

// a supplier at x 0 with its chest on top, a provider with a full chest at x 1
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class PatternGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos TARGET = new BlockPos(0, 2, 1);

    private static ItemStack upgrade(UpgradeKind kind) {
        return new ItemStack(LogisticsPipes.UPGRADES.get(kind).get());
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static Container chest(GameTestHelper helper, int x, ItemStack... contents) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return container;
    }

    private static void provider(GameTestHelper helper) {
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, new ItemStack(Items.COAL, 64), new ItemStack(Items.DIAMOND, 64));
    }

    // coal goes into the sixth slot
    private static PipeBlockEntity coalSupplier(GameTestHelper helper, boolean limited) {
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 0);
        helper.assertTrue(supplier.installUpgrade(upgrade(UpgradeKind.PATTERN)), "a supplier pipe takes the pattern upgrade");
        supplier.setMode(SupplyMode.PARTIAL.ordinal());
        supplier.config().setItem(0, new ItemStack(Items.COAL, 4));
        supplier.slotSettings().setPatternSlot(0, 5);
        supplier.slotSettings().setLimited(limited);
        return supplier;
    }

    private static void assertSlot(GameTestHelper helper, Container container, int slot, Item item, int count) {
        ItemStack stack = container.getItem(slot);
        helper.assertTrue(stack.is(item) && stack.getCount() == count,
                "expected " + count + " " + item + " in slot " + slot + " but found " + stack);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void patternKeepsEveryItemInItsSlot(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = coalSupplier(helper, true);
        supplier.config().setItem(1, new ItemStack(Items.DIAMOND, 2));
        supplier.slotSettings().setPatternSlot(1, 7);
        Container target = chest(helper, 0);
        provider(helper);

        helper.runAfterDelay(150, () -> {
            assertSlot(helper, target, 5, Items.COAL, 4);
            assertSlot(helper, target, 7, Items.DIAMOND, 2);
            helper.assertTrue(target.countItem(Items.COAL) == 4 && target.countItem(Items.DIAMOND) == 2, "nothing in other slots");
            helper.succeed();
        });
    }

    // without the upgrade the same pipe just fills the chest from the first slot on
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void withoutThePatternUpgradeTheSlotNumbersDoNothing(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = coalSupplier(helper, true);
        supplier.removeUpgrades();
        Container target = chest(helper, 0);
        provider(helper);

        helper.runAfterDelay(150, () -> {
            assertSlot(helper, target, 0, Items.COAL, 4);
            helper.succeed();
        });
    }

    // a slot holding something else is left alone
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void patternOrdersNothingForASlotThatHoldsSomethingElse(GameTestHelper helper) {
        GameTestPower.supply(helper);
        coalSupplier(helper, true);
        Container target = chest(helper, 0);
        target.setItem(5, new ItemStack(Items.DIRT));
        provider(helper);

        helper.runAfterDelay(150, () -> {
            helper.assertTrue(target.countItem(Items.COAL) == 0, "no coal was ordered");
            helper.succeed();
        });
    }

    // six more coal than the slot is meant to hold arrive: limited sends them away again, here to the basic pipe at x 2
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void limitedPatternSendsBackWhatDoesNotFitItsSlot(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = coalSupplier(helper, true);
        Container target = chest(helper, 0);
        provider(helper);
        pipe(helper, PipeKind.BASIC, 2).setDefaultRoute(true);
        Container overflow = chest(helper, 2);

        helper.runAfterDelay(30, () -> supplier.request(McItemType.batchOf(new ItemStack(Items.COAL, 6))));
        helper.runAfterDelay(250, () -> {
            assertSlot(helper, target, 5, Items.COAL, 4);
            helper.assertTrue(target.countItem(Items.COAL) == 4, "nothing went into another slot");
            helper.assertTrue(overflow.countItem(Items.COAL) >= 2, "the rest went back into the network");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void unlimitedPatternKeepsWhatDoesNotFitItsSlot(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = coalSupplier(helper, false);
        Container target = chest(helper, 0);
        provider(helper);
        pipe(helper, PipeKind.BASIC, 2).setDefaultRoute(true);
        Container overflow = chest(helper, 2);

        helper.runAfterDelay(30, () -> supplier.request(McItemType.batchOf(new ItemStack(Items.COAL, 6))));
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(target.countItem(Items.COAL) >= 6, "everything stayed, found " + target.countItem(Items.COAL));
            helper.assertTrue(overflow.countItem(Items.COAL) == 0, "nothing went back into the network");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void settingsScreenSetsTheSlotOfEveryMarkerOnlyWithTheUpgrade(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 0);
        TestPlayer player = TestPlayer.in(helper);
        int thirdMarkerUp = PipeConfigMenu.BUTTON_PATTERN_SLOT + 2 * 2 + 1;

        player.openMenu(supplier.menuProvider());
        helper.assertTrue(!player.containerMenu.clickMenuButton(player, thirdMarkerUp), "no slot numbers without the upgrade");
        helper.assertTrue(!player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_LIMITED), "and no limited switch");

        supplier.installUpgrade(upgrade(UpgradeKind.PATTERN));
        player.openMenu(supplier.menuProvider());
        PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
        helper.assertTrue(menu.patterned() && menu.limited(), "with the upgrade the markers stand for slots, limited at first");
        for (int click = 0; click < 3; click++) {
            menu.clickMenuButton(player, thirdMarkerUp);
        }
        helper.assertTrue(supplier.slotSettings().patternSlot(2) == 3 && menu.patternSlot(2) == 3, "three up is the fourth slot");
        for (int click = 0; click < 5; click++) {
            menu.clickMenuButton(player, thirdMarkerUp - 1);
        }
        helper.assertTrue(supplier.slotSettings().patternSlot(2) == 0, "it does not go below the first slot");
        menu.clickMenuButton(player, thirdMarkerUp);
        menu.clickMenuButton(player, PipeConfigMenu.BUTTON_LIMITED);
        helper.assertTrue(!supplier.slotSettings().limited() && !supplier.patternSupplies().get(0).limited(), "the switch turns limited off");

        // the mode that fills all the room is not offered with a pattern
        supplier.setMode(SupplyMode.BULK100.ordinal());
        menu.clickMenuButton(player, PipeConfigMenu.BUTTON_MODE);
        helper.assertTrue(supplier.mode() == SupplyMode.PARTIAL.ordinal(), "after bulk100 comes partial again");
        helper.assertTrue(supplier.supplies().isEmpty(), "the plain supplier is off while there is a pattern");

        PipeBlockEntity loaded = pipe(helper, PipeKind.SUPPLIER, 3);
        loaded.loadWithComponents(supplier.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.slotSettings().patternSlot(2) == 1 && !loaded.slotSettings().limited(), "slot numbers and switch are saved");
        helper.succeed();
    }

    // the module is set up through the chassis screen, where its pattern upgrade sits in its own upgrade slots
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void activeSupplierModuleWithPatternUpgradeFillsItsSlot(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos chassisPos = new BlockPos(0, 1, 1);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        ItemStack module = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ACTIVE_SUPPLIER).get());
        ModuleItem.configureSupplier(module, List.of(new ItemStack(Items.COAL, 4)), SupplyMode.PARTIAL);
        chassis.modules().setItem(0, module);
        Container target = chest(helper, 0);
        provider(helper);
        TestPlayer player = TestPlayer.in(helper);
        int firstMarkerUp = PipeConfigMenu.BUTTON_PATTERN_SLOT + 1;

        player.openMenu(chassis.menuProvider());
        player.containerMenu.clickMenuButton(player, ChassisMenu.BUTTON_SETTINGS);
        helper.assertTrue(!player.containerMenu.clickMenuButton(player, firstMarkerUp), "no slot numbers without the upgrade");
        helper.assertTrue(chassis.supplies().size() == 1 && chassis.patternSupplies().isEmpty(), "a plain supplier without it");

        chassis.moduleUpgrades().setItem(0, upgrade(UpgradeKind.PATTERN));
        player.openMenu(chassis.menuProvider());
        player.containerMenu.clickMenuButton(player, ChassisMenu.BUTTON_SETTINGS);
        for (int click = 0; click < 5; click++) {
            helper.assertTrue(player.containerMenu.clickMenuButton(player, firstMarkerUp), "the upgrade of the module unlocks the numbers");
        }
        helper.assertTrue(chassis.supplies().isEmpty() && chassis.patternSupplies().get(0).entries().get(0).slot() == 5,
                "the module keeps its coal in the sixth slot");

        helper.runAfterDelay(150, () -> {
            assertSlot(helper, target, 5, Items.COAL, 4);
            helper.assertTrue(target.countItem(Items.COAL) == 4, "nothing in other slots");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void patternUpgradeFitsSuppliersOnly(GameTestHelper helper) {
        helper.assertTrue(UpgradeKind.PATTERN.fitsPipe(PipeKind.SUPPLIER) && !UpgradeKind.PATTERN.fitsPipe(PipeKind.BASIC)
                && !UpgradeKind.PATTERN.fitsPipe(PipeKind.CHASSIS), "of the pipes only the supplier");
        helper.assertTrue(UpgradeKind.PATTERN.fitsModule(ModuleKind.ACTIVE_SUPPLIER) && !UpgradeKind.PATTERN.fitsModule(ModuleKind.PASSIVE_SUPPLIER),
                "of the modules only the active supplier");
        helper.succeed();
    }
}
