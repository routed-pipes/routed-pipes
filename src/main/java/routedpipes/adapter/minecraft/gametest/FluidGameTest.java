package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.Platform;
import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.FluidContainerItem;
import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.McFluidType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.FluidConfigMenu;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.adapter.minecraft.menu.SatelliteNames;
import routedpipes.adapter.minecraft.menu.FluidRequestMenu;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidSupply;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class FluidGameTest {

    private static final String EMPTY = "empty";
    // long enough for every pipe to find its neighbours
    private static final int SETTLE = 5;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x, int y) {
        BlockPos pos = new BlockPos(x, y, 1);
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static void transport(GameTestHelper helper, int x, int y) {
        helper.setBlock(new BlockPos(x, y, 1), Content.TRANSPORT_PIPE.get());
    }

    private static TestTank tank(GameTestHelper helper, int x, int y, Fluid fluid, int amount) {
        return TestTank.place(helper, new BlockPos(x, y, 1), fluid, amount);
    }

    private static TestTank tank(GameTestHelper helper, int x, int y) {
        return tank(helper, x, y, Fluids.EMPTY, 0);
    }

    private static void lists(PipeBlockEntity pipe, int slot, Fluid fluid) {
        pipe.config().setItem(slot, FluidContainerItem.marker(new McFluidType(fluid)));
    }

    // what a pump on top of the pipe would do, returns how much the pipe took
    private static int pumpInto(GameTestHelper helper, PipeBlockEntity pipe, Fluid fluid, int amount) {
        return Platform.get().fluids().tanks(helper.getLevel(), pipe.getBlockPos(), Direction.UP)
                .map(tanks -> tanks.fill(new FluidBatch(new McFluidType(fluid), amount), false)).orElse(0);
    }

    private static void assertHolds(GameTestHelper helper, TestTank tank, Fluid fluid, int amount) {
        helper.assertTrue(tank.amount() == amount && (amount == 0 || tank.fluid() == fluid),
                "expected " + amount + " mB but the tank holds " + tank.amount() + " mB of " + tank.fluid());
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pumpedInFluidTravelsToThePipeThatListsIt(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        transport(helper, 1, 1);
        lists(pipe(helper, PipeKind.FLUID_BASIC, 2, 1), 0, Fluids.WATER);
        TestTank target = tank(helper, 2, 2);

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(pumpInto(helper, insertion, Fluids.WATER, 3000) == 3000,
                "the insertion pipe should take what is pumped in"));

        helper.succeedWhen(() -> assertHolds(helper, target, Fluids.WATER, 3000));
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void fluidNobodyListsStaysInTheInsertionPipe(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        lists(pipe(helper, PipeKind.FLUID_BASIC, 1, 1), 0, Fluids.LAVA);
        TestTank target = tank(helper, 1, 2);

        helper.runAfterDelay(SETTLE, () -> pumpInto(helper, insertion, Fluids.WATER, 3000));

        helper.runAfterDelay(60, () -> {
            assertHolds(helper, target, Fluids.WATER, 0);
            helper.assertTrue(insertion.fluidPipe().pipeTanks().side(Side.UP).amount() == 3000, "the fluid should still be in the pipe");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aFullTankSendsTheRestToTheNextPipeThatListsTheFluid(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        PipeBlockEntity nearPipe = pipe(helper, PipeKind.FLUID_BASIC, 1, 1);
        lists(nearPipe, 0, Fluids.WATER);
        tank(helper, 1, 2, Fluids.WATER, TestTank.CAPACITY);
        lists(pipe(helper, PipeKind.FLUID_BASIC, 2, 1), 0, Fluids.WATER);
        TestTank far = tank(helper, 2, 2);

        // the near pipe still has room in its own tank towards the full one
        helper.runAfterDelay(SETTLE, () -> pumpInto(helper, insertion, Fluids.WATER, 5000));
        helper.runAfterDelay(SETTLE + 20, () -> pumpInto(helper, insertion, Fluids.WATER, 5000));

        helper.succeedWhen(() -> {
            helper.assertTrue(nearPipe.fluidPipe().pipeTanks().side(Side.UP).amount() == 5000, "the near pipe should be full");
            assertHolds(helper, far, Fluids.WATER, 5000);
        });
    }

    @GameTest(template = EMPTY)
    public static void otherFluidPipesTakeNothingThatIsPumpedIn(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.FLUID_BASIC, 0, 1);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(pumpInto(helper, basic, Fluids.WATER, 1000) == 0, "a basic fluid pipe is no tank");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void fluidPipesConnectToTanksButNotToInventories(GameTestHelper helper) {
        pipe(helper, PipeKind.FLUID_BASIC, 0, 1);
        tank(helper, 0, 2);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);

        helper.runAfterDelay(SETTLE, () -> {
            BlockState state = helper.getBlockState(new BlockPos(0, 1, 1));
            helper.assertTrue(PipeBlock.connected(state, Direction.UP), "connected to the tank");
            helper.assertTrue(!PipeBlock.connected(state, Direction.EAST), "not connected to the chest");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void packedFluidIsSpilledWhenItsPipeBreaks(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        for (int x = 1; x <= 4; x++) {
            transport(helper, x, 1);
        }
        lists(pipe(helper, PipeKind.FLUID_BASIC, 5, 1), 0, Fluids.WATER);
        TestTank target = tank(helper, 5, 2);

        helper.runAfterDelay(SETTLE, () -> pumpInto(helper, insertion, Fluids.WATER, 5000));
        helper.runAfterDelay(SETTLE + 5, () -> {
            for (int x = 1; x <= 4; x++) {
                helper.destroyBlock(new BlockPos(x, 1, 1));
            }
        });

        helper.runAfterDelay(60, () -> {
            assertHolds(helper, target, Fluids.WATER, 0);
            helper.assertTrue(insertion.fluidPipe().pipeTanks().isEmpty(), "the fluid should have left the insertion pipe");
            helper.assertEntityNotPresent(EntityType.ITEM);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void theFilterTakesTheFluidOfABucketAndNothingElse(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.FLUID_BASIC, 0, 1);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(basic.menuProvider());
            player.containerMenu.setCarried(new ItemStack(Items.LAVA_BUCKET));
            player.containerMenu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(FluidContainerItem.fluidIn(basic.config().getItem(0))
                    .filter(batch -> batch.type().equals(new McFluidType(Fluids.LAVA))).isPresent(), "the slot should show lava");
            helper.assertTrue(player.containerMenu.getCarried().is(Items.LAVA_BUCKET), "the bucket stays in the hand");

            player.containerMenu.setCarried(new ItemStack(Items.COBBLESTONE));
            player.containerMenu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(basic.config().getItem(0).isEmpty(), "an item without fluid clears the slot");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aPipeThatIsBeingSetUpTakesNothing(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        PipeBlockEntity basic = pipe(helper, PipeKind.FLUID_BASIC, 1, 1);
        lists(basic, 0, Fluids.WATER);
        TestTank target = tank(helper, 1, 2);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(basic.menuProvider());
            pumpInto(helper, insertion, Fluids.WATER, 5000);
        });
        helper.runAfterDelay(40, () -> {
            assertHolds(helper, target, Fluids.WATER, 0);
            player.containerMenu.removed(player);
        });

        helper.succeedWhen(() -> assertHolds(helper, target, Fluids.WATER, 5000));
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void extractorPullsFluidOutOfItsTankAndSendsItOn(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.FLUID_EXTRACTOR, 0, 1);
        TestTank source = tank(helper, 0, 2, Fluids.WATER, 2000);
        lists(pipe(helper, PipeKind.FLUID_BASIC, 1, 1), 0, Fluids.WATER);
        TestTank target = tank(helper, 1, 2);

        helper.succeedWhen(() -> {
            assertHolds(helper, source, Fluids.WATER, 0);
            assertHolds(helper, target, Fluids.WATER, 2000);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void extractorPullsNothingWithoutPower(GameTestHelper helper) {
        pipe(helper, PipeKind.FLUID_EXTRACTOR, 0, 1);
        TestTank source = tank(helper, 0, 2, Fluids.WATER, 2000);
        lists(pipe(helper, PipeKind.FLUID_BASIC, 1, 1), 0, Fluids.WATER);
        tank(helper, 1, 2);

        helper.runAfterDelay(100, () -> {
            assertHolds(helper, source, Fluids.WATER, 2000);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void terminusOnlyGetsWhatNoBasicPipeTakes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        lists(pipe(helper, PipeKind.FLUID_TERMINUS, 1, 1), 4, Fluids.WATER);
        TestTank terminus = tank(helper, 1, 2);
        lists(pipe(helper, PipeKind.FLUID_BASIC, 2, 1), 0, Fluids.WATER);
        TestTank basic = tank(helper, 2, 2, Fluids.WATER, TestTank.CAPACITY - 5000);

        helper.runAfterDelay(SETTLE, () -> pumpInto(helper, insertion, Fluids.WATER, 5000));
        // by now the basic pipe is full, its own tank towards the full one included
        helper.runAfterDelay(SETTLE + 20, () -> pumpInto(helper, insertion, Fluids.WATER, 5000));
        helper.runAfterDelay(SETTLE + 40, () -> pumpInto(helper, insertion, Fluids.WATER, 5000));

        helper.succeedWhen(() -> {
            assertHolds(helper, basic, Fluids.WATER, TestTank.CAPACITY);
            assertHolds(helper, terminus, Fluids.WATER, 5000);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void providerKnowsWhatItsTankHoldsAndSendsIt(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity provider = pipe(helper, PipeKind.FLUID_PROVIDER, 0, 1);
        TestTank source = tank(helper, 0, 2, Fluids.LAVA, 4000);
        PipeBlockEntity receiver = pipe(helper, PipeKind.FLUID_BASIC, 1, 1);
        TestTank target = tank(helper, 1, 2);
        McFluidType lava = new McFluidType(Fluids.LAVA);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(provider.fluidPipe().providable().equals(java.util.Map.of(lava, 4000)), "the provider should offer its lava");
            helper.assertTrue(provider.fluidPipe().provide(lava, 1500, receiver.routerId()) == 1500, "the provider should send what is asked for");
        });

        helper.succeedWhen(() -> {
            assertHolds(helper, source, Fluids.LAVA, 2500);
            assertHolds(helper, target, Fluids.LAVA, 1500);
        });
    }

    private static FluidBatch lava(int amount) {
        return new FluidBatch(new McFluidType(Fluids.LAVA), amount);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void requestedFluidIsDeliveredIntoTheTank(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.FLUID_REQUEST, 0, 1);
        TestTank target = tank(helper, 0, 2);
        transport(helper, 1, 1);
        pipe(helper, PipeKind.FLUID_PROVIDER, 2, 1);
        TestTank source = tank(helper, 2, 2, Fluids.LAVA, 8000);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(request.fluidPipe().available().equals(java.util.Map.of(new McFluidType(Fluids.LAVA), 8000)),
                    "the lava of the provider should be on offer");
            helper.assertTrue(request.fluidPipe().request(lava(3000)).started(), "request should be possible");
        });

        helper.succeedWhen(() -> {
            assertHolds(helper, target, Fluids.LAVA, 3000);
            assertHolds(helper, source, Fluids.LAVA, 5000);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aLargeRequestArrivesInSeveralContainers(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.FLUID_REQUEST, 0, 1);
        TestTank target = tank(helper, 0, 2);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        tank(helper, 1, 2, Fluids.LAVA, TestTank.CAPACITY);

        helper.runAfterDelay(SETTLE, () -> request.fluidPipe().request(lava(12000)));

        helper.succeedWhen(() -> assertHolds(helper, target, Fluids.LAVA, 12000));
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aFluidRequestForMoreThanThereIsMovesNothing(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.FLUID_REQUEST, 0, 1);
        TestTank target = tank(helper, 0, 2);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        TestTank source = tank(helper, 1, 2, Fluids.LAVA, 2000);

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(!request.fluidPipe().request(lava(3000)).started(), "request should fail"));

        helper.runAfterDelay(60, () -> {
            assertHolds(helper, target, Fluids.LAVA, 0);
            assertHolds(helper, source, Fluids.LAVA, 2000);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aFluidRequestWithoutPowerMovesNothing(GameTestHelper helper) {
        PipeBlockEntity request = pipe(helper, PipeKind.FLUID_REQUEST, 0, 1);
        tank(helper, 0, 2);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        TestTank source = tank(helper, 1, 2, Fluids.LAVA, 2000);

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome outcome = request.fluidPipe().request(lava(1000));
            helper.assertTrue(!outcome.started() && !outcome.powered(), "request should fail for lack of power");
        });

        helper.runAfterDelay(60, () -> {
            assertHolds(helper, source, Fluids.LAVA, 2000);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theFluidRequestScreenListsAndOrders(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.FLUID_REQUEST, 0, 1);
        TestTank target = tank(helper, 0, 2);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        tank(helper, 1, 2, Fluids.LAVA, 8000);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(request.menuProvider());
            FluidRequestMenu menu = (FluidRequestMenu) player.containerMenu;
            ItemStack listed = menu.getSlot(0).getItem();
            helper.assertTrue(FluidRequestMenu.listedAmount(listed) == 8000, "the list should show 8000 mB but shows " + listed);
            helper.assertTrue(listed.getHoverName().getString().equals("Lava"), "the entry is called " + listed.getHoverName().getString());

            menu.clicked(0, 0, ClickType.PICKUP, player);
            // one bucket twice and a hundred mB, the single mB of a fresh screen does not count
            menu.clickMenuButton(player, 6);
            menu.clickMenuButton(player, 6);
            menu.clickMenuButton(player, 5);
            helper.assertTrue(menu.amount() == 2100, "the amount should be 2100 but is " + menu.amount());
            menu.clickMenuButton(player, FluidRequestMenu.BUTTON_REQUEST);
        });

        helper.succeedWhen(() -> assertHolds(helper, target, Fluids.LAVA, 2100));
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void supplierKeepsItsTankStocked(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_SUPPLIER, 0, 1);
        TestTank target = tank(helper, 0, 2, Fluids.LAVA, 500);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        TestTank source = tank(helper, 1, 2, Fluids.LAVA, TestTank.CAPACITY);
        supplier.fluidPipe().stock(new McFluidType(Fluids.LAVA), 4000, false, FluidSupply.Minimum.NONE);

        // long enough for the supplier to look at its tank three times
        helper.runAfterDelay(320, () -> {
            assertHolds(helper, target, Fluids.LAVA, 4000);
            assertHolds(helper, source, Fluids.LAVA, TestTank.CAPACITY - 3500);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void supplierWaitsUntilItsMinimumIsMissing(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_SUPPLIER, 0, 1);
        TestTank target = tank(helper, 0, 2, Fluids.LAVA, 3500);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        tank(helper, 1, 2, Fluids.LAVA, TestTank.CAPACITY);
        supplier.fluidPipe().stock(new McFluidType(Fluids.LAVA), 4000, false, FluidSupply.Minimum.ONE_BUCKET);

        boolean[] drained = {false};

        helper.runAfterDelay(130, () -> {
            assertHolds(helper, target, Fluids.LAVA, 3500);
            target.drain(600);
            drained[0] = true;
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(drained[0], "too early");
            assertHolds(helper, target, Fluids.LAVA, 4000);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void supplierThatTakesPartsGetsWhatThereIs(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_SUPPLIER, 0, 1);
        TestTank target = tank(helper, 0, 2);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        tank(helper, 1, 2, Fluids.LAVA, 1500);
        supplier.fluidPipe().stock(new McFluidType(Fluids.LAVA), 4000, true, FluidSupply.Minimum.NONE);

        helper.succeedWhen(() -> assertHolds(helper, target, Fluids.LAVA, 1500));
    }

    @GameTest(template = EMPTY)
    public static void theSupplierSettingsSetFluidAmountAndModes(GameTestHelper helper) {
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_SUPPLIER, 0, 1);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(supplier.menuProvider());
            FluidConfigMenu menu = (FluidConfigMenu) player.containerMenu;
            menu.setCarried(new ItemStack(Items.LAVA_BUCKET));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            // twice a thousand more, then ten less
            menu.clickMenuButton(player, FluidConfigMenu.BUTTON_FIRST_STEP + 7);
            menu.clickMenuButton(player, FluidConfigMenu.BUTTON_FIRST_STEP + 7);
            menu.clickMenuButton(player, FluidConfigMenu.BUTTON_FIRST_STEP + 2);
            menu.clickMenuButton(player, FluidConfigMenu.BUTTON_PARTIAL);
            menu.clickMenuButton(player, FluidConfigMenu.BUTTON_MINIMUM);

            FluidSupply expected = new FluidSupply(new McFluidType(Fluids.LAVA), 1990, true, FluidSupply.Minimum.TWO_BUCKETS);
            helper.assertTrue(supplier.fluidPipe().supply().equals(java.util.Optional.of(expected)),
                    "the supplier keeps " + supplier.fluidPipe().supply());
            helper.assertTrue(menu.amount() == 1990 && menu.partial() && menu.minimum() == 2, "the screen should show the same");

            CompoundTag saved = supplier.saveWithoutMetadata(helper.getLevel().registryAccess());
            supplier.fluidPipe().stock(new McFluidType(Fluids.WATER), 1, false, FluidSupply.Minimum.NONE);
            supplier.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.assertTrue(supplier.fluidPipe().supply().equals(java.util.Optional.of(expected)), "the settings should survive saving");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void fluidOrderedForASatelliteGoesIntoItsTank(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity satellite = pipe(helper, PipeKind.FLUID_SATELLITE, 0, 1);
        TestTank target = tank(helper, 0, 2);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        tank(helper, 1, 2, Fluids.LAVA, 8000);

        helper.runAfterDelay(SETTLE, () -> satellite.fluidPipe().request(lava(2000)));

        helper.succeedWhen(() -> assertHolds(helper, target, Fluids.LAVA, 2000));
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void aSatelliteAsksAgainForFluidThatWasSpilledOnTheWay(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity satellite = pipe(helper, PipeKind.FLUID_SATELLITE, 0, 1);
        TestTank target = tank(helper, 0, 2);
        for (int x = 1; x <= 4; x++) {
            transport(helper, x, 1);
        }
        pipe(helper, PipeKind.FLUID_PROVIDER, 5, 1);
        TestTank source = tank(helper, 5, 2, Fluids.LAVA, TestTank.CAPACITY);
        boolean[] broken = {false};

        helper.runAfterDelay(SETTLE, () -> satellite.fluidPipe().request(lava(5000)));
        // the pipes break while the lava is in them and are put back a little later
        helper.onEachTick(() -> {
            if (!broken[0] && helper.getBlockEntity(new BlockPos(3, 1, 1)) instanceof PipeBlockEntity middle
                    && !middle.travellingItems().isEmpty()) {
                broken[0] = true;
                for (int x = 1; x <= 4; x++) {
                    helper.destroyBlock(new BlockPos(x, 1, 1));
                }
                helper.runAfterDelay(10, () -> {
                    for (int x = 1; x <= 4; x++) {
                        transport(helper, x, 1);
                    }
                });
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(broken[0], "the pipes should have been broken");
            assertHolds(helper, target, Fluids.LAVA, 5000);
            assertHolds(helper, source, Fluids.LAVA, TestTank.CAPACITY - 10000);
        });
    }

    // FINDING: a satellite is never told when its fluid gives up on a routed pipe, so it never asks again
    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void aSatelliteAsksAgainForFluidThatGaveUpInARoutedPipe(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity satellite = pipe(helper, PipeKind.FLUID_SATELLITE, 0, 1);
        TestTank target = tank(helper, 0, 2);
        transport(helper, 1, 1);
        transport(helper, 2, 1);
        pipe(helper, PipeKind.FLUID_PROVIDER, 3, 1);
        TestTank source = tank(helper, 3, 2, Fluids.LAVA, TestTank.CAPACITY);

        // the way is cut right after the order, so the lava waits in the provider pipe until it gives up
        helper.runAfterDelay(SETTLE, () -> {
            satellite.fluidPipe().request(lava(5000));
            helper.destroyBlock(new BlockPos(1, 1, 1));
        });
        // and comes back long after that
        helper.runAfterDelay(SETTLE + 350, () -> transport(helper, 1, 1));

        helper.succeedWhen(() -> helper.assertTrue(target.amount() == 5000, "the satellite should have its 5000 mB, it has "
                + target.amount() + " mB while " + (TestTank.CAPACITY - source.amount()) + " mB left the provider"));
    }

    // FINDING: fluid that does not fit where it arrives is only partly sent on, the rest is gone though another pipe takes it
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void fluidThatDoesNotFitOnArrivalIsAllSentOn(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        transport(helper, 1, 1);
        PipeBlockEntity near = pipe(helper, PipeKind.FLUID_BASIC, 2, 1);
        lists(near, 0, Fluids.WATER);
        TestTank nearTank = tank(helper, 2, 2);
        transport(helper, 3, 1);
        // two more sinks with room for 3000 each, their tanks are full and their own side tanks hold 2000
        PipeBlockEntity second = pipe(helper, PipeKind.FLUID_BASIC, 4, 1);
        lists(second, 0, Fluids.WATER);
        tank(helper, 4, 2, Fluids.WATER, TestTank.CAPACITY);
        PipeBlockEntity third = pipe(helper, PipeKind.FLUID_BASIC, 5, 1);
        lists(third, 0, Fluids.WATER);
        tank(helper, 5, 2, Fluids.WATER, TestTank.CAPACITY);
        McFluidType water = new McFluidType(Fluids.WATER);
        boolean[] filledUp = {false};

        helper.runAfterDelay(SETTLE, () -> {
            second.fluidPipe().pipeTanks().side(Side.UP).fill(new FluidBatch(water, 2000), false);
            third.fluidPipe().pipeTanks().side(Side.UP).fill(new FluidBatch(water, 2000), false);
            pumpInto(helper, insertion, Fluids.WATER, 5000);
        });
        // while the water is on its way the near pipe and its tank fill up from somewhere else
        helper.onEachTick(() -> {
            if (!filledUp[0] && helper.getBlockEntity(new BlockPos(1, 1, 1)) instanceof PipeBlockEntity middle
                    && !middle.travellingItems().isEmpty()) {
                filledUp[0] = true;
                nearTank.fill(Fluids.WATER, TestTank.CAPACITY);
                near.fluidPipe().pipeTanks().side(Side.UP).fill(new FluidBatch(water, 5000), false);
                near.fluidPipe().pipeTanks().centre().fill(new FluidBatch(water, 10000), false);
            }
        });

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(filledUp[0], "the near pipe should have been filled up");
            int sentOn = second.fluidPipe().pipeTanks().side(Side.UP).amount() + second.fluidPipe().pipeTanks().centre().amount()
                    + third.fluidPipe().pipeTanks().side(Side.UP).amount() + third.fluidPipe().pipeTanks().centre().amount() - 4000;
            helper.assertTrue(sentOn == 5000, "all 5000 mB should have been sent on, but only " + sentOn + " mB arrived");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void theSatelliteSettingsSetItsName(GameTestHelper helper) {
        PipeBlockEntity satellite = pipe(helper, PipeKind.FLUID_SATELLITE, 0, 1);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(satellite.menuProvider());
            MenuRequests.applyText(player, player.containerMenu.containerId, SatelliteNames.FIELD_OWN, " Lava tank ");
            helper.assertTrue(satellite.satelliteName().equals("Lava tank"), "the satellite takes the name without the blanks around it");
            helper.assertTrue(((FluidConfigMenu) player.containerMenu).satelliteName().equals("Lava tank"), "the menu shows the name");
            MenuRequests.applyText(player, player.containerMenu.containerId, SatelliteNames.FIELD_OWN, "  ");
            helper.assertTrue(satellite.satelliteName().equals("Lava tank"), "a blank name is refused");
            helper.succeed();
        });
    }

    private static BlockPos chest(GameTestHelper helper, int x, int y, ItemStack... contents) {
        BlockPos pos = new BlockPos(x, y, 1);
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return pos;
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = helper.getBlockEntity(pos);
        return container.countItem(item);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void containerSupplierOrdersFullBucketsAndSendsTheEmptyOnesOff(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_CONTAINER_SUPPLIER, 0, 1);
        TestTank target = tank(helper, 0, 2);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        BlockPos full = chest(helper, 1, 2, new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.WATER_BUCKET));
        pipe(helper, PipeKind.BASIC, 2, 1).setDefaultRoute(true);
        BlockPos empty = chest(helper, 2, 2);
        // buckets do not stack, two slots are two buckets
        supplier.config().setItem(0, new ItemStack(Items.WATER_BUCKET));
        supplier.config().setItem(1, new ItemStack(Items.WATER_BUCKET));

        // long enough for the supplier to look at its tank three times
        helper.runAfterDelay(320, () -> {
            assertHolds(helper, target, Fluids.WATER, 2000);
            helper.assertTrue(count(helper, empty, Items.BUCKET) == 2, "two empty buckets should have come back");
            helper.assertTrue(count(helper, full, Items.WATER_BUCKET) == 1, "one full bucket should be left");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void containerSupplierOrdersNothingForATankThatHoldsEnough(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_CONTAINER_SUPPLIER, 0, 1);
        tank(helper, 0, 2, Fluids.WATER, 1500);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        BlockPos full = chest(helper, 1, 2, new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.WATER_BUCKET));
        supplier.config().setItem(0, new ItemStack(Items.WATER_BUCKET));
        supplier.config().setItem(1, new ItemStack(Items.WATER_BUCKET));

        helper.runAfterDelay(220, () -> {
            helper.assertTrue(count(helper, full, Items.WATER_BUCKET) == 2, "no bucket should have been ordered");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void theContainerSupplierSettingsListFullContainersOnly(GameTestHelper helper) {
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_CONTAINER_SUPPLIER, 0, 1);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(supplier.menuProvider());
            player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
            player.containerMenu.clicked(4, 0, ClickType.PICKUP, player);
            helper.assertTrue(supplier.config().getItem(4).isEmpty(), "an empty bucket holds no fluid");

            player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
            player.containerMenu.clicked(4, 0, ClickType.PICKUP, player);
            helper.assertTrue(supplier.config().getItem(4).is(Items.WATER_BUCKET), "the slot should list a water bucket");
            helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET), "the bucket stays in the hand");

            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.containerMenu.clicked(4, 0, ClickType.PICKUP, player);
            helper.assertTrue(supplier.config().getItem(4).isEmpty(), "an empty hand clears the slot");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void tanksOfAPipeSurviveSavingAndLoading(GameTestHelper helper) {
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);

        helper.runAfterDelay(SETTLE, () -> {
            pumpInto(helper, insertion, Fluids.LAVA, 1234);
            CompoundTag saved = insertion.saveWithoutMetadata(helper.getLevel().registryAccess());
            insertion.fluidPipe().pipeTanks().side(Side.UP).drain(1234);

            insertion.loadWithComponents(saved, helper.getLevel().registryAccess());

            helper.assertTrue(insertion.fluidPipe().pipeTanks().side(Side.UP).holding(new McFluidType(Fluids.LAVA)) == 1234,
                    "the tank should hold its lava again");
            helper.succeed();
        });
    }
}
