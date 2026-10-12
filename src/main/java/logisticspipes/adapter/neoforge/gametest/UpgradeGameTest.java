package logisticspipes.adapter.neoforge.gametest;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.UpgradeItem;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.PipeKind;
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

    private static ItemStack upgrade(UpgradeItem.Kind kind, int count) {
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
        ItemStack sneaky = upgrade(UpgradeItem.Kind.SNEAKY, 1);
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
        ItemStack sneaky = upgrade(UpgradeItem.Kind.SNEAKY, 1);
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
        ItemStack held = upgrade(UpgradeItem.Kind.SPEED, 6);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        for (int click = 0; click < 6; click++) {
            helper.getBlockState(relative).useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, relative));
        }
        helper.assertTrue(held.getCount() == 6 - PipeBlockEntity.UPGRADE_SLOTS, "only as many as there are slots get used up");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.getBlockState(relative).useWithoutItem(helper.getLevel(), player, hit(helper, relative));
        helper.assertTrue(basic.removeUpgrades().isEmpty(), "sneaking takes everything out");
        helper.assertItemEntityCountIs(LogisticsPipes.UPGRADES.get(UpgradeItem.Kind.SPEED).get(), relative, 2,
                PipeBlockEntity.UPGRADE_SLOTS);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void plainPipesTakeNoUpgrades(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos relative = new BlockPos(0, 1, 1);
        helper.setBlock(relative, LogisticsPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity transport = helper.getBlockEntity(relative);
        ItemStack held = upgrade(UpgradeItem.Kind.SPEED, 1);

        helper.assertTrue(!transport.installUpgrade(held) && held.getCount() == 1, "nothing goes into a transport pipe");
        helper.succeed();
    }

    // a basic pipe, six transport pipes, a basic pipe with a chest: returns the tick the diamond arrived, or -1
    private static int[] longRun(GameTestHelper helper, int speedUpgrades) {
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        ItemStack upgrades = upgrade(UpgradeItem.Kind.SPEED, speedUpgrades);
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
        ItemStack sneaky = upgrade(UpgradeItem.Kind.SNEAKY, 1);
        UpgradeItem.setSide(sneaky, Direction.DOWN);
        original.installUpgrade(sneaky);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PipeBlockEntity loaded = pipe(helper, PipeKind.PROVIDER, 5);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        java.util.List<ItemStack> upgrades = loaded.removeUpgrades();
        helper.assertTrue(upgrades.size() == 1 && UpgradeItem.sideOf(upgrades.get(0)) == Direction.DOWN, "sneaky upgrade with its side");
        helper.succeed();
    }
}
