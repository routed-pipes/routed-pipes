package logisticspipes.adapter.neoforge.gametest;

import logisticspipes.domain.logistics.UpgradeKind;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.UpgradeItem;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.adapter.minecraft.menu.UpgradeMenu;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.adapter.minecraft.LogisticsPipeBlock;
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

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class UpgradeGameTest {

    private static final String EMPTY = "empty";

    private static ItemStack upgrade(UpgradeKind kind, int count) {
        return new ItemStack(LogisticsPipes.UPGRADES.get(kind).get(), count);
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        // in these tests a basic pipe takes everything unless the test says otherwise
        pipe.setDefaultRoute(kind == PipeKind.BASIC);
        return pipe;
    }

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        Container container = helper.getBlockEntity(pos);
        int count = container.countItem(item);
        helper.assertTrue(count == expected, "expected " + expected + " " + item + " but found " + count);
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    // the pipe touches the side of the furnace, where only fuel goes in and nothing comes out
    private static PipeBlockEntity providerBesideFurnace(GameTestHelper helper) {
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 1);
        BlockPos furnace = new BlockPos(2, 1, 1);
        helper.setBlock(furnace, Blocks.FURNACE);
        Container contents = helper.getBlockEntity(furnace);
        contents.setItem(2, new ItemStack(Items.IRON_INGOT, 5));
        return provider;
    }

    @GameTest(template = EMPTY)
    public static void withoutSneakyUpgradeThePipeUsesTheFaceItTouches(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity provider = providerBesideFurnace(helper);

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(provider.contents().isEmpty(), "the furnace side gives nothing");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void sneakyUpgradeReachesThroughAnotherFace(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity provider = providerBesideFurnace(helper);
        ItemStack sneaky = upgrade(UpgradeKind.SNEAKY, 1);
        UpgradeItem.setSide(sneaky, Direction.DOWN);
        helper.assertTrue(provider.installUpgrade(sneaky), "upgrade should fit");

        helper.runAfterDelay(3, () -> {
            Integer ingots = provider.contents().get(McItemType.batchOf(new ItemStack(Items.IRON_INGOT)).type());
            helper.assertTrue(ingots != null && ingots == 5, "the furnace bottom gives the smelted ingots, got " + ingots);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void sneakyUpgradeTurnsWhenUsedInTheHand(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = TestPlayer.in(helper);
        ItemStack sneaky = upgrade(UpgradeKind.SNEAKY, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, sneaky);
        helper.assertTrue(UpgradeItem.sideOf(sneaky) == Direction.UP, "a fresh upgrade points up");

        sneaky.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(UpgradeItem.sideOf(sneaky) == Direction.NORTH, "one use turns it to the next face");
        for (int i = 0; i < 5; i++) {
            sneaky.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(UpgradeItem.sideOf(sneaky) == Direction.UP, "six uses go all the way round");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void upgradesGoInByClickingAndComeOutBySneaking(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        BlockPos relative = new BlockPos(0, 1, 1);
        TestPlayer player = TestPlayer.in(helper);
        ItemStack held = upgrade(UpgradeKind.SPEED, 6);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        for (int click = 0; click < 6; click++) {
            helper.getBlockState(relative).useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, relative));
        }
        helper.assertTrue(held.isEmpty(), "all six go in and stack up");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.getBlockState(relative).useWithoutItem(helper.getLevel(), player, hit(helper, relative));
        helper.assertTrue(basic.removeUpgrades().isEmpty(), "sneaking takes everything out");
        int dropped = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM, relative, 2).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(dropped == 6, "expected six upgrades on the ground but found " + dropped);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void plainPipesTakeNoUpgrades(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos relative = new BlockPos(0, 1, 1);
        helper.setBlock(relative, LogisticsPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity transport = helper.getBlockEntity(relative);
        ItemStack held = upgrade(UpgradeKind.SPEED, 1);

        helper.assertTrue(!transport.installUpgrade(held) && held.getCount() == 1, "nothing goes into a transport pipe");
        helper.succeed();
    }

    // a basic pipe, six transport pipes, a basic pipe with a chest: returns the tick the diamond arrived, or -1
    private static int[] longRun(GameTestHelper helper, int speedUpgrades) {
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        ItemStack upgrades = upgrade(UpgradeKind.SPEED, speedUpgrades);
        for (int i = 0; i < speedUpgrades; i++) {
            start.installUpgrade(upgrades);
        }
        for (int x = 1; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        }
        pipe(helper, PipeKind.BASIC, 7);
        BlockPos chest = new BlockPos(7, 2, 1);
        helper.setBlock(chest, Blocks.CHEST);
        int[] arrival = {-1};
        helper.runAfterDelay(15, () -> start.accept(new ItemStack(Items.DIAMOND, 1), Direction.DOWN));
        for (int tick = 16; tick < 60; tick++) {
            int after = tick - 15;
            helper.runAfterDelay(tick, () -> {
                Container container = helper.getBlockEntity(chest);
                if (arrival[0] < 0 && container.countItem(Items.DIAMOND) == 1) {
                    arrival[0] = after;
                }
            });
        }
        return arrival;
    }

    // a sorted item does half a block per tick, so eight pipes take about sixteen ticks
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void withoutSpeedUpgradeTheRunTakesItsTime(GameTestHelper helper) {
        GameTestPower.supply(helper);
        int[] arrival = longRun(helper, 0);

        helper.runAfterDelay(70, () -> {
            helper.assertTrue(arrival[0] >= 14 && arrival[0] <= 20, "expected about 16 ticks, took " + arrival[0]);
            helper.succeed();
        });
    }

    // four upgrades double the speed to the limit of a whole pipe per tick, so the run takes about eight ticks
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void speedUpgradesCarryItemsFasterThroughTheWholeRun(GameTestHelper helper) {
        GameTestPower.supply(helper);
        int[] arrival = longRun(helper, 4);

        helper.runAfterDelay(70, () -> {
            helper.assertTrue(arrival[0] >= 7 && arrival[0] <= 11, "expected about 8 ticks, took " + arrival[0]);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void installedUpgradesSurviveSaveAndLoad(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity original = providerBesideFurnace(helper);
        ItemStack sneaky = upgrade(UpgradeKind.SNEAKY, 1);
        UpgradeItem.setSide(sneaky, Direction.DOWN);
        original.installUpgrade(sneaky);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PipeBlockEntity loaded = pipe(helper, PipeKind.PROVIDER, 5);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        java.util.List<ItemStack> upgrades = loaded.removeUpgrades();
        helper.assertTrue(upgrades.size() == 1 && UpgradeItem.sideOf(upgrades.get(0)) == Direction.DOWN, "sneaky upgrade with its side");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void upgradesGoOnlyWhereTheyFit(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        BlockPos chassisPos = new BlockPos(2, 1, 1);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(2).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);

        helper.assertTrue(!basic.installUpgrade(upgrade(UpgradeKind.MODULE_UPGRADE, 1)), "a basic pipe has no modules to upgrade");
        helper.assertTrue(!basic.installUpgrade(upgrade(UpgradeKind.ACTION_SPEED, 1)), "action speed is for modules");
        helper.assertTrue(chassis.installUpgrade(upgrade(UpgradeKind.MODULE_UPGRADE, 1)), "a chassis takes the module upgrade");
        helper.succeed();
    }

    // the controller opens the upgrade slots, the chassis screen shows module upgrade slots once the chassis has the upgrade for it
    @GameTest(template = EMPTY)
    public static void pipeControllerOpensTheUpgradesAndModulesGetTheirOwnSlots(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos relative = new BlockPos(0, 1, 1);
        helper.setBlock(relative, LogisticsPipes.CHASSIS_PIPES.get(2).get());
        PipeBlockEntity chassis = helper.getBlockEntity(relative);
        chassis.modules().setItem(0, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.EXTRACTOR).get()));
        chassis.modules().setItem(1, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get()));
        TestPlayer player = TestPlayer.in(helper);
        ItemStack controller = new ItemStack(LogisticsPipes.PIPE_CONTROLLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, controller);

        helper.getBlockState(relative).useItemOn(controller, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, relative));
        helper.assertTrue(player.containerMenu instanceof UpgradeMenu, "the controller should open the upgrades");
        helper.assertTrue(!player.containerMenu.getSlot(0).mayPlace(upgrade(UpgradeKind.ACTION_SPEED, 1)), "not a pipe upgrade");
        helper.assertTrue(player.containerMenu.getSlot(0).mayPlace(upgrade(UpgradeKind.MODULE_UPGRADE, 1)), "fits a chassis");

        player.openMenu(chassis.menuProvider());
        int firstUpgradeSlot = PipeBlockEntity.MODULE_SLOTS;
        helper.assertTrue(!player.containerMenu.getSlot(firstUpgradeSlot).isActive(), "no module upgrade slots without the upgrade");
        chassis.installUpgrade(upgrade(UpgradeKind.MODULE_UPGRADE, 1));
        player.openMenu(chassis.menuProvider());
        helper.assertTrue(player.containerMenu.getSlot(firstUpgradeSlot).isActive(), "module upgrade slots with the upgrade");
        helper.assertTrue(player.containerMenu.getSlot(firstUpgradeSlot).mayPlace(upgrade(UpgradeKind.ACTION_SPEED, 1)),
                "an extractor takes action speed");
        helper.assertTrue(!player.containerMenu.getSlot(firstUpgradeSlot + PipeBlockEntity.UPGRADES_PER_MODULE)
                .mayPlace(upgrade(UpgradeKind.ACTION_SPEED, 1)), "an item sink does not");
        helper.succeed();
    }

    // a plain extractor moves 16 items in its first round, one with a stack upgrade moves the whole 64
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void stackExtractionUpgradeMovesAWholeStackAtOnce(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos chassisPos = new BlockPos(0, 1, 1);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        chassis.modules().setItem(0, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.EXTRACTOR).get()));
        chassis.moduleUpgrades().setItem(0, upgrade(UpgradeKind.ITEM_STACK_EXTRACTION, 1));
        BlockPos source = new BlockPos(0, 2, 1);
        helper.setBlock(source, Blocks.CHEST);
        helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(source).setItem(0, new ItemStack(Items.COAL, 64));
        pipe(helper, PipeKind.BASIC, 1);
        BlockPos target = new BlockPos(1, 2, 1);
        helper.setBlock(target, Blocks.CHEST);

        helper.runAfterDelay(39, () -> {
            assertCount(helper, target, Items.COAL, 64);
            helper.succeed();
        });
    }

    // an idle crafting pipe with a cleanup upgrade sends everything in its machine off, here to a basic pipe with a chest
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void cleanupUpgradeClearsTheMachineOfAnIdleCrafter(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        crafting.config().setItem(0, new ItemStack(Items.OAK_LOG));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OAK_PLANKS, 4));
        crafting.config().setItem(PipeBlockEntity.CLEANUP_FIRST, new ItemStack(Items.OAK_LOG));
        helper.assertTrue(crafting.installUpgrade(upgrade(UpgradeKind.CRAFTING_CLEANUP, 1)), "a crafting pipe takes the cleanup upgrade");
        BlockPos machine = new BlockPos(0, 2, 1);
        helper.setBlock(machine, Blocks.CHEST);
        Container contents = helper.getBlockEntity(machine);
        contents.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        contents.setItem(1, new ItemStack(Items.DIRT, 5));
        pipe(helper, PipeKind.BASIC, 1);
        BlockPos target = new BlockPos(1, 2, 1);
        helper.setBlock(target, Blocks.CHEST);

        helper.runAfterDelay(80, () -> {
            assertCount(helper, target, Items.DIRT, 5);
            assertCount(helper, machine, Items.OAK_LOG, 3);
            helper.succeed();
        });
    }

    // the slots for the byproduct and the cleanup list only work with their upgrades
    @GameTest(template = EMPTY)
    public static void craftingScreenShowsUpgradeSlotsOnlyWithTheirUpgrades(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        TestPlayer player = TestPlayer.in(helper);

        player.openMenu(crafting.menuProvider());
        int byproduct = PipeBlockEntity.BYPRODUCT_SLOT;
        int cleanup = PipeBlockEntity.CLEANUP_FIRST;
        helper.assertTrue(!player.containerMenu.getSlot(byproduct).isActive() && !player.containerMenu.getSlot(cleanup).isActive(),
                "no extra slots without upgrades");
        crafting.installUpgrade(upgrade(UpgradeKind.CRAFTING_BYPRODUCT, 1));
        ItemStack cleanups = upgrade(UpgradeKind.CRAFTING_CLEANUP, 2);
        crafting.installUpgrade(cleanups);
        crafting.installUpgrade(cleanups);
        player.openMenu(crafting.menuProvider());
        helper.assertTrue(player.containerMenu.getSlot(byproduct).isActive(), "byproduct slot");
        helper.assertTrue(player.containerMenu.getSlot(cleanup + 5).isActive() && !player.containerMenu.getSlot(cleanup + 6).isActive(),
                "six cleanup slots for two upgrades");

        crafting.config().setItem(0, new ItemStack(Items.RAW_IRON));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT));
        crafting.config().setItem(byproduct, new ItemStack(Items.IRON_NUGGET));
        helper.assertTrue(crafting.crafts().get(0).byproduct().isPresent(), "the byproduct is part of the recipe");
        player.containerMenu.clickMenuButton(player, logisticspipes.adapter.minecraft.menu.PipeConfigMenu.BUTTON_IMPORT_CLEANUP);
        helper.assertTrue(crafting.config().getItem(cleanup).is(Items.RAW_IRON) && crafting.config().getItem(cleanup + 1).is(Items.IRON_INGOT),
                "import lists the recipe");
        helper.assertTrue(crafting.cleanup().orElseThrow().removes(McItemType.batchOf(new ItemStack(Items.RAW_IRON)).type()),
                "after the import the listed items are what goes");
        helper.succeed();
    }

    // two routed pipes side by side stop seeing each other when the side between them is cut off
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void disconnectionUpgradeCutsASideOffAndReconnectsIt(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity left = pipe(helper, PipeKind.BASIC, 0);
        pipe(helper, PipeKind.BASIC, 1);
        BlockPos leftPos = new BlockPos(0, 1, 1);
        BlockPos rightPos = new BlockPos(1, 1, 1);
        ItemStack held = upgrade(UpgradeKind.DISCONNECTION, 1);
        Direction towardsRight = Direction.EAST;

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(LogisticsPipeBlock.connected(helper.getBlockState(leftPos), towardsRight), "connected at first");
            left.toggleDisconnected(held, towardsRight);
            helper.assertTrue(held.isEmpty(), "the upgrade goes into the pipe");
            helper.assertTrue(!LogisticsPipeBlock.connected(helper.getBlockState(leftPos), towardsRight)
                    && !LogisticsPipeBlock.connected(helper.getBlockState(rightPos), towardsRight.getOpposite()),
                    "both pipes let go of each other");
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(left.networkStatus().orElseThrow().routers() == 0, "the pipe sees no other router any more");
            left.removeUpgrades();
            helper.assertTrue(LogisticsPipeBlock.connected(helper.getBlockState(leftPos), towardsRight),
                    "taking the upgrade out reconnects");
            helper.succeed();
        });
    }

    // with the upgrade the satellite of an ingredient slot is set on its own, and only that slot goes there
    @GameTest(template = EMPTY)
    public static void advancedSatelliteUpgradeGivesEverySlotItsOwnSatellite(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        crafting.config().setItem(0, new ItemStack(Items.RAW_IRON));
        crafting.config().setItem(7, new ItemStack(Items.COAL));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT));
        crafting.setSatelliteId(5);
        TestPlayer player = TestPlayer.in(helper);

        helper.assertTrue(crafting.crafts().get(0).bySatellite().keySet().equals(java.util.Set.of(5)), "the bottom row goes to satellite 5");
        player.openMenu(crafting.menuProvider());
        int slotZeroUp = logisticspipes.adapter.minecraft.menu.PipeConfigMenu.BUTTON_SLOT_SATELLITE + 1;
        helper.assertTrue(!player.containerMenu.clickMenuButton(player, slotZeroUp), "no slot satellites without the upgrade");

        crafting.installUpgrade(upgrade(UpgradeKind.ADVANCED_SATELLITE, 1));
        player.openMenu(crafting.menuProvider());
        player.containerMenu.clickMenuButton(player, slotZeroUp);
        player.containerMenu.clickMenuButton(player, slotZeroUp);
        java.util.Map<Integer, java.util.List<logisticspipes.domain.item.ItemBatch>> bySatellite = crafting.crafts().get(0).bySatellite();
        helper.assertTrue(bySatellite.keySet().equals(java.util.Set.of(2)), "only the first slot has a satellite now, number 2: " + bySatellite);
        helper.assertTrue(bySatellite.get(2).equals(java.util.List.of(McItemType.batchOf(new ItemStack(Items.RAW_IRON)))), "the iron goes there");

        PipeBlockEntity loaded = pipe(helper, PipeKind.CRAFTING, 2);
        loaded.loadWithComponents(crafting.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.crafts().get(0).bySatellite().keySet().equals(java.util.Set.of(2)), "slot satellites are saved");
        helper.succeed();
    }
}
