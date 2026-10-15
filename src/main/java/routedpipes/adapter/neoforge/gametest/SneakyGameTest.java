package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.List;

import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.UpgradeItem;
import routedpipes.adapter.minecraft.menu.UpgradeMenu;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// the pipes here touch the side of a furnace, where only fuel goes in: slot 0 is behind the top, slot 1 behind the sides
// diamonds do not smelt, so the furnace never lights and burns the coal
@GameTestHolder(Content.MODID)
@PrefixGameTestTemplate(false)
public class SneakyGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final BlockPos FURNACE = new BlockPos(2, 1, 1);
    private static final int INPUT = 0;
    private static final int FUEL = 1;

    private static ItemStack upgrade(UpgradeKind kind) {
        return new ItemStack(Content.UPGRADES.get(kind).get());
    }

    private static ItemStack sneaky(Direction face) {
        ItemStack upgrade = upgrade(UpgradeKind.SNEAKY);
        UpgradeItem.setSide(upgrade, face);
        return upgrade;
    }

    private static PipeBlockEntity basicBesideFurnace(GameTestHelper helper) {
        helper.setBlock(PIPE, Content.pipe(PipeKind.BASIC));
        helper.setBlock(FURNACE, Blocks.FURNACE);
        PipeBlockEntity pipe = helper.getBlockEntity(PIPE);
        pipe.setDefaultRoute(true);
        return pipe;
    }

    private static void assertSlot(GameTestHelper helper, int slot, Item item, int count) {
        Container furnace = helper.getBlockEntity(FURNACE);
        ItemStack stack = furnace.getItem(slot);
        helper.assertTrue(count == 0 ? stack.isEmpty() : stack.is(item) && stack.getCount() == count,
                "expected " + count + " " + item + " in slot " + slot + " but found " + stack);
    }

    // a diamond tries the bottom, which takes nothing, and goes in at the top, coal fits neither and goes in at the side
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void combinationTriesTheFacesOfItsRowInOrder(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity pipe = basicBesideFurnace(helper);
        helper.assertTrue(pipe.installUpgrade(upgrade(UpgradeKind.SNEAKY_COMBINATION)), "every routed pipe takes the combination");
        helper.assertTrue(pipe.installUpgrade(sneaky(Direction.DOWN)) && pipe.installUpgrade(sneaky(Direction.UP))
                && pipe.installUpgrade(sneaky(Direction.NORTH)), "sneaky upgrades go in");
        helper.assertTrue(pipe.sneakyUpgrades().getItem(2).getItem() instanceof UpgradeItem && pipe.upgrades().getItem(1).isEmpty(),
                "with the combination they go into its row");
        Container furnace = helper.getBlockEntity(FURNACE);
        // a full input slot sends the next thing on to the following face
        furnace.setItem(INPUT, new ItemStack(Items.DIAMOND, 60));

        helper.runAfterDelay(15, () -> pipe.accept(new ItemStack(Items.DIAMOND, 4), Direction.DOWN));
        helper.runAfterDelay(40, () -> pipe.accept(new ItemStack(Items.COAL, 8), Direction.DOWN));
        helper.runAfterDelay(80, () -> {
            assertSlot(helper, INPUT, Items.DIAMOND, 64);
            assertSlot(helper, FUEL, Items.COAL, 8);
            helper.succeed();
        });
    }

    // the row alone does nothing: the pipe is back at the side of the furnace, and a diamond is no fuel
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void withoutTheCombinationUpgradeTheRowDoesNothing(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity pipe = basicBesideFurnace(helper);
        pipe.sneakyUpgrades().setItem(0, sneaky(Direction.UP));

        helper.runAfterDelay(15, () -> pipe.accept(new ItemStack(Items.DIAMOND, 4), Direction.DOWN));
        helper.runAfterDelay(60, () -> {
            assertSlot(helper, INPUT, Items.DIAMOND, 0);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void controllerShowsTheSneakyRowOnlyWithTheCombinationUpgrade(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity pipe = basicBesideFurnace(helper);
        TestPlayer player = TestPlayer.in(helper);
        int row = UpgradeMenu.FIRST_SNEAKY_SLOT;

        ItemStack controller = new ItemStack(Content.PIPE_CONTROLLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, controller);
        BlockPos absolute = helper.absolutePos(PIPE);
        helper.getBlockState(PIPE).useItemOn(controller, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        UpgradeMenu menu = (UpgradeMenu) player.containerMenu;
        menu.clickMenuButton(player, UpgradeMenu.Tab.UPGRADES.ordinal());
        helper.assertTrue(!menu.getSlot(row).isActive() && !menu.getSlot(row).mayPlace(sneaky(Direction.UP)), "no row without the upgrade");

        pipe.installUpgrade(upgrade(UpgradeKind.SNEAKY_COMBINATION));
        helper.assertTrue(menu.getSlot(row).isActive() && menu.getSlot(row + 8).isActive(), "nine more slots with it");
        helper.assertTrue(menu.getSlot(row).mayPlace(sneaky(Direction.UP)), "they take sneaky upgrades");
        helper.assertTrue(!menu.getSlot(row).mayPlace(upgrade(UpgradeKind.SPEED)), "and nothing else");
        helper.assertTrue(menu.getSlot(row).getMaxStackSize() == 1, "one per slot");

        // shift clicking a sneaky upgrade in the inventory puts it into the row
        player.getInventory().setItem(9, sneaky(Direction.UP));
        menu.quickMoveStack(player, PipeBlockEntity.UPGRADE_SLOTS);
        helper.assertTrue(pipe.sneakyUpgrades().getItem(0).getItem() instanceof UpgradeItem, "shift click fills the row");

        // taking the combination out and closing hands the row back
        pipe.upgrades().setItem(0, ItemStack.EMPTY);
        menu.removed(player);
        helper.assertTrue(pipe.sneakyUpgrades().isEmpty(), "the row is emptied");
        helper.assertTrue(player.getInventory().countItem(Content.UPGRADES.get(UpgradeKind.SNEAKY).get()) == 1,
                "the player gets the upgrade back");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sneakyRowSurvivesSaveAndLoadAndComesOutWithTheUpgrades(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity pipe = basicBesideFurnace(helper);
        pipe.installUpgrade(upgrade(UpgradeKind.SNEAKY_COMBINATION));
        pipe.installUpgrade(sneaky(Direction.NORTH));
        CompoundTag saved = pipe.saveWithoutMetadata(helper.getLevel().registryAccess());

        BlockPos other = new BlockPos(1, 1, 4);
        helper.setBlock(other, Content.pipe(PipeKind.BASIC));
        PipeBlockEntity loaded = helper.getBlockEntity(other);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(UpgradeItem.sideOf(loaded.sneakyUpgrades().getItem(0)) == Direction.NORTH, "the row is saved with its faces");
        helper.assertTrue(loaded.removeUpgrades().size() == 2 && loaded.sneakyUpgrades().isEmpty(), "taking the upgrades out empties the row");
        helper.succeed();
    }

    private static PipeBlockEntity chassisBesideFurnace(GameTestHelper helper, ItemStack... modules) {
        helper.setBlock(PIPE, Content.CHASSIS_PIPES.get(2).get());
        helper.setBlock(FURNACE, Blocks.FURNACE);
        PipeBlockEntity chassis = helper.getBlockEntity(PIPE);
        for (int slot = 0; slot < modules.length; slot++) {
            chassis.modules().setItem(slot, modules[slot]);
        }
        return chassis;
    }

    private static ItemStack sink(Item... items) {
        ItemStack module = new ItemStack(Content.MODULES.get(ModuleKind.ITEM_SINK).get());
        ModuleItem.configureSink(module, List.of(items).stream().map(ItemStack::new).toList(), false);
        return module;
    }

    // one chassis fills both slots of the furnace: the diamond module reaches in at the top, the coal module has no upgrade
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void sneakyUpgradeOfAModuleDecidesTheFaceForWhatThatModuleTakes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity chassis = chassisBesideFurnace(helper, sink(Items.DIAMOND), sink(Items.COAL));
        chassis.moduleUpgrades().setItem(1, sneaky(Direction.UP));

        helper.runAfterDelay(15, () -> chassis.accept(new ItemStack(Items.DIAMOND, 4), Direction.DOWN));
        helper.runAfterDelay(40, () -> chassis.accept(new ItemStack(Items.COAL, 8), Direction.DOWN));
        helper.runAfterDelay(80, () -> {
            assertSlot(helper, INPUT, Items.DIAMOND, 4);
            assertSlot(helper, FUEL, Items.COAL, 8);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void sneakyUpgradeFitsTheSinkModulesOnly(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity chassis = chassisBesideFurnace(helper, sink(Items.DIAMOND),
                new ItemStack(Content.MODULES.get(ModuleKind.EXTRACTOR).get()));
        chassis.installUpgrade(upgrade(UpgradeKind.MODULE_UPGRADE));
        TestPlayer player = TestPlayer.in(helper);

        player.openMenu(chassis.menuProvider());
        int first = PipeBlockEntity.MODULE_SLOTS;
        helper.assertTrue(player.containerMenu.getSlot(first).mayPlace(sneaky(Direction.UP)), "an item sink takes a sneaky upgrade");
        helper.assertTrue(!player.containerMenu.getSlot(first + PipeBlockEntity.UPGRADES_PER_MODULE).mayPlace(sneaky(Direction.UP)),
                "an extractor does not");
        helper.assertTrue(!player.containerMenu.getSlot(first).mayPlace(upgrade(UpgradeKind.SNEAKY_COMBINATION)),
                "the combination is for pipes");
        for (ModuleKind kind : List.of(ModuleKind.POLYMORPHIC_ITEM_SINK, ModuleKind.MOD_ITEM_SINK, ModuleKind.TAG_ITEM_SINK,
                ModuleKind.CREATIVE_TAB_ITEM_SINK)) {
            helper.assertTrue(UpgradeKind.SNEAKY.fitsModule(kind), kind + " takes a sneaky upgrade");
        }
        helper.assertTrue(!UpgradeKind.SNEAKY.fitsModule(ModuleKind.ENCHANTMENT_SINK) && !UpgradeKind.SNEAKY.fitsModule(ModuleKind.TERMINUS),
                "the other sinks do not");
        helper.succeed();
    }
}
