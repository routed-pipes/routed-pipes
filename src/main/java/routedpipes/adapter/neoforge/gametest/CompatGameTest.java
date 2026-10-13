package routedpipes.adapter.neoforge.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.ChannelRegistry;
import routedpipes.adapter.minecraft.FluidContainerItem;
import routedpipes.adapter.minecraft.McFluidType;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PipeDetails;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import routedpipes.adapter.minecraft.menu.CraftingTableMenu;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.adapter.neoforge.CapabilityItemStorage;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.channel.ChannelAccess;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

// the same delivery and ordering checks against inventories of vanilla and of other mods
// blocks of mods that are not installed are skipped, so the normal test run only covers the vanilla ones
// hoppers are left out, one on top of a pipe empties itself into the pipe
@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class CompatGameTest {

    private static final List<String> INVENTORIES = List.of(
            "minecraft:chest",
            "minecraft:barrel",
            "minecraft:dropper",
            "sophisticatedstorage:chest",
            "sophisticatedstorage:barrel",
            "sophisticatedstorage:iron_chest",
            "sophisticatedstorage:limited_barrel_1",
            "storagedrawers:oak_full_drawers_1",
            "storagedrawers:oak_full_drawers_4",
            "mekanism:basic_bin",
            "mekanism:personal_chest",
            "ae2:sky_stone_chest",
            "create:item_vault",
            "create:depot",
            "create:basin",
            "immersiveengineering:crate",
            "ironchest:iron_chest",
            "ironchest:diamond_chest",
            "projecte:alchemical_chest");

    @GameTestGenerator
    public static Collection<TestFunction> inventories() {
        List<TestFunction> tests = new ArrayList<>();
        for (String id : INVENTORIES) {
            ResourceLocation key = ResourceLocation.parse(id);
            if (!BuiltInRegistries.BLOCK.containsKey(key)) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(key);
            String name = id.replace(':', '.');
            tests.add(test("compat.delivers_into." + name, helper -> deliversInto(helper, block)));
            tests.add(test("compat.orders_from." + name, helper -> ordersFrom(helper, block)));
        }
        return tests;
    }

    private static final List<String> TANKS = List.of(
            "create:fluid_tank",
            "mekanism:basic_fluid_tank");

    @GameTestGenerator
    public static Collection<TestFunction> tanks() {
        List<TestFunction> tests = new ArrayList<>();
        for (String id : TANKS) {
            if (has(id)) {
                String name = id.replace(':', '.');
                tests.add(test("compat.fills_tank." + name, helper -> fillsTank(helper, block(id)), 400));
                tests.add(test("compat.extracts_from_tank." + name, helper -> extractsFromTank(helper, block(id)), 600));
                tests.add(test("compat.orders_from_tank." + name, helper -> ordersFromTank(helper, block(id)), 400));
            }
        }
        return tests;
    }

    private static IFluidHandler tankAt(GameTestHelper helper, BlockPos pos) {
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.SOUTH);
        helper.assertTrue(handler != null, "no tank at " + pos);
        return handler;
    }

    private static void fluidFilter(PipeBlockEntity pipe, Fluid fluid) {
        pipe.config().setItem(0, FluidContainerItem.marker(new McFluidType(fluid)));
    }

    // fluid pumped into an insertion pipe ends up in the tank of the other mod
    private static void fillsTank(GameTestHelper helper, Block tank) {
        GameTestPower.supply(helper);
        PipeBlockEntity insertion = pipe(helper, PipeKind.FLUID_INSERTION, 0, 1);
        fluidFilter(pipe(helper, PipeKind.FLUID_BASIC, 1, 1), Fluids.WATER);
        BlockPos target = new BlockPos(1, 1, 0);
        helper.setBlock(target, tank);

        helper.runAfterDelay(10, () -> {
            IFluidHandler pump = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, insertion.getBlockPos(), Direction.UP);
            helper.assertTrue(pump != null && pump.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.EXECUTE) == 3000,
                    "the insertion pipe should take what is pumped in");
        });

        helper.succeedWhen(() -> {
            FluidStack held = tankAt(helper, target).getFluidInTank(0);
            helper.assertTrue(held.getFluid() == Fluids.WATER && held.getAmount() == 3000, "the tank holds " + held);
        });
    }

    // fluid in the tank of the other mod can be ordered
    private static void ordersFromTank(GameTestHelper helper, Block tank) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.FLUID_REQUEST, 0, 1);
        FluidTank target = TestTank.place(helper, new BlockPos(0, 2, 1), Fluids.EMPTY, 0);
        pipe(helper, PipeKind.FLUID_PROVIDER, 1, 1);
        BlockPos source = new BlockPos(1, 1, 0);
        helper.setBlock(source, tank);
        helper.assertTrue(tankAt(helper, source).fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE) == 4000,
                "could not fill the tank");

        helper.runAfterDelay(10, () -> helper.assertTrue(
                request.fluidPipe().request(new FluidBatch(new McFluidType(Fluids.WATER), 2500)).started(), "request should be possible"));

        helper.succeedWhen(() -> helper.assertTrue(target.getFluidAmount() == 2500, "only " + target.getFluidAmount() + " mB arrived"));
    }

    // an extractor pipe empties the tank of the other mod
    private static void extractsFromTank(GameTestHelper helper, Block tank) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.FLUID_EXTRACTOR, 0, 1);
        BlockPos source = new BlockPos(0, 1, 0);
        helper.setBlock(source, tank);
        fluidFilter(pipe(helper, PipeKind.FLUID_BASIC, 1, 1), Fluids.WATER);
        FluidTank target = TestTank.place(helper, new BlockPos(1, 2, 1), Fluids.EMPTY, 0);
        helper.assertTrue(tankAt(helper, source).fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE) == 2000,
                "could not fill the tank");

        helper.succeedWhen(() -> helper.assertTrue(target.getFluidAmount() == 2000, "only " + target.getFluidAmount() + " mB came out"));
    }

    // tests that need a working machine or network of another mod
    @GameTestGenerator
    public static Collection<TestFunction> machines() {
        List<TestFunction> tests = new ArrayList<>();
        if (has("ae2:interface")) {
            tests.add(test("compat.ae2.stores_through_interface", CompatGameTest::ae2StoresThroughInterface, 400));
            tests.add(test("compat.ae2.stores_into_me_chest", CompatGameTest::ae2StoresIntoMeChest, 400));
            tests.add(test("compat.ae2.orders_from_stocking_interface", CompatGameTest::ae2OrdersFromStockingInterface, 600));
        }
        if (has("createaddition:alternator")) {
            tests.add(test("compat.createaddition.alternator_charges_junction", CompatGameTest::alternatorChargesJunction, 400));
            tests.add(test("compat.createaddition.alternator_charges_power_provider",
                    CompatGameTest::alternatorChargesPowerProvider, 400));
        }
        if (has("createaddition:electric_motor")) {
            tests.add(test("compat.createaddition.supplier_pipe_charges_electric_motor",
                    CompatGameTest::supplierPipeChargesElectricMotor, 400));
        }
        if (has("create:millstone")) {
            tests.add(test("compat.create.crafts_with_millstone", CompatGameTest::createCraftsWithMillstone, 2400));
        }
        if (has("mekanism:basic_fluid_tank")) {
            tests.add(test("compat.mekanism.container_supplier_keeps_the_emptied_tank",
                    CompatGameTest::containerSupplierKeepsTheEmptiedMekanismTank, 400));
        }
        if (has("mekanism:basic_universal_cable") && has("createaddition:alternator")) {
            tests.add(test("compat.mekanism.cable_charges_junction", CompatGameTest::mekanismCableChargesJunction, 400));
        }
        if (has("enderstorage:ender_chest")) {
            tests.add(test("compat.enderstorage.ender_chests_join_two_networks", CompatGameTest::enderChestsJoinTwoNetworks, 400));
            // ender tanks of one colour are one tank, so only one test may use them
            tests.add(test("compat.fills_tank.enderstorage.ender_tank",
                    helper -> fillsTank(helper, block("enderstorage:ender_tank")), 400));
        }
        if (has("bigreactors:basic_reactorfuelrod") && has("mekanism:uranium_ore")) {
            tests.add(test("compat.pack.mekanism_uranium_is_reactor_fuel", CompatGameTest::mekanismUraniumIsReactorFuel));
        }
        if (ModList.get().isLoaded("jei")) {
            tests.add(test("compat.jei.table_transfer_links_and_reports", CompatGameTest::jeiTableTransferLinksAndReports));
        }
        if (ModList.get().isLoaded("jade")) {
            tests.add(test("compat.jade.sends_pipe_details", CompatGameTest::jadeSendsPipeDetails));
        }
        return tests;
    }

    // FINDING: a container supplier destroys a full container that has no crafting remainder, like a mekanism tank
    private static void containerSupplierKeepsTheEmptiedMekanismTank(GameTestHelper helper) {
        GameTestPower.supply(helper);
        ItemStack full = new ItemStack(item("mekanism:basic_fluid_tank"));
        var handler = full.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(handler != null && handler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                "could not fill the mekanism tank");
        ItemStack container = handler.getContainer().copy();
        PipeBlockEntity supplier = pipe(helper, PipeKind.FLUID_CONTAINER_SUPPLIER, 0, 1);
        FluidTank target = TestTank.place(helper, new BlockPos(0, 2, 1), Fluids.EMPTY, 0);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        BlockPos fullChest = new BlockPos(1, 2, 1);
        helper.setBlock(fullChest, Blocks.CHEST);
        ((Container) helper.getBlockEntity(fullChest)).setItem(0, container.copy());
        pipe(helper, PipeKind.BASIC, 2, 1);
        BlockPos emptyChest = new BlockPos(2, 2, 1);
        helper.setBlock(emptyChest, Blocks.CHEST);
        supplier.config().setItem(0, container.copy());

        helper.runAfterDelay(320, () -> {
            helper.assertTrue(target.getFluidAmount() == 1000, "the water should be in the tank, it holds " + target.getFluidAmount());
            int tanks = 0;
            for (BlockPos chest : List.of(fullChest, emptyChest)) {
                Container inventory = (Container) helper.getBlockEntity(chest);
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    if (inventory.getItem(slot).is(container.getItem())) {
                        tanks += inventory.getItem(slot).getCount();
                    }
                }
            }
            for (int x = 0; x <= 2; x++) {
                PipeBlockEntity pipe = helper.getBlockEntity(new BlockPos(x, 1, 1));
                tanks += (int) pipe.travellingItems().stream()
                        .filter(item -> McItemType.stackOf(item.batch()).is(container.getItem())).count();
            }
            tanks += helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                    entity -> entity.getItem().is(container.getItem())).size();
            helper.assertTrue(tanks == 1, "the emptied mekanism tank should still be somewhere, found " + tanks);
            helper.succeed();
        });
    }

    private static boolean has(String id) {
        return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id));
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x, int y) {
        BlockPos pos = new BlockPos(x, y, 1);
        helper.setBlock(pos, RoutedPipes.pipe(kind));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        // in these tests a basic pipe takes everything unless the test says otherwise
        pipe.setDefaultRoute(kind == PipeKind.BASIC);
        return pipe;
    }

    // puts a stack into whatever face of the block takes it
    private static void insertAnywhere(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        ItemStack rest = stack;
        for (Direction side : Direction.values()) {
            IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), side);
            if (handler != null && !rest.isEmpty()) {
                rest = ItemHandlerHelper.insertItemStacked(handler, rest, false);
            }
        }
        helper.assertTrue(rest.isEmpty(), "could not insert " + stack + " at " + pos);
    }

    // how many of an item a storage cell holds, read from the saved cell because there is no compile dependency on ae2
    private static long storedInCell(GameTestHelper helper, BlockPos holder, String slot, Item wanted) {
        CompoundTag saved = helper.getBlockEntity(holder).saveWithoutMetadata(helper.getLevel().registryAccess());
        ListTag stored = saved.getCompound("inv").getCompound(slot).getCompound("components").getList("ae2:storage_cell_inv", Tag.TAG_COMPOUND);
        long count = 0;
        for (int index = 0; index < stored.size(); index++) {
            if (stored.getCompound(index).getString("id").equals(BuiltInRegistries.ITEM.getKey(wanted).toString())) {
                count += stored.getCompound(index).getLong("#");
            }
        }
        return count;
    }

    // items sent to a basic pipe on an ME interface end up in the storage cell of the network
    private static void ae2StoresThroughInterface(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        helper.setBlock(new BlockPos(1, 1, 1), RoutedPipes.TRANSPORT_PIPE.get());
        pipe(helper, PipeKind.BASIC, 2);
        helper.setBlock(new BlockPos(2, 2, 1), block("ae2:interface"));
        BlockPos drive = new BlockPos(3, 2, 1);
        helper.setBlock(drive, block("ae2:drive"));
        helper.setBlock(new BlockPos(4, 2, 1), block("ae2:creative_energy_cell"));

        helper.runAfterDelay(5, () -> insertAnywhere(helper, drive, new ItemStack(item("ae2:item_storage_cell_1k"))));
        helper.runAfterDelay(60, () -> {
            start.accept(new ItemStack(Items.COBBLESTONE, 40), Direction.DOWN);
            start.accept(new ItemStack(Items.DIAMOND, 5), Direction.DOWN);
        });

        helper.succeedWhen(() -> {
            long cobblestone = storedInCell(helper, drive, "item0", Items.COBBLESTONE);
            long diamonds = storedInCell(helper, drive, "item0", Items.DIAMOND);
            helper.assertTrue(cobblestone == 40 && diamonds == 5,
                    "the cell holds " + cobblestone + " cobblestone and " + diamonds + " diamonds, expected 40 and 5");
        });
    }

    private static void ae2StoresIntoMeChest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        helper.setBlock(new BlockPos(1, 1, 1), RoutedPipes.TRANSPORT_PIPE.get());
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos chest = new BlockPos(2, 2, 1);
        helper.setBlock(chest, block("ae2:chest"));
        helper.setBlock(new BlockPos(3, 2, 1), block("ae2:creative_energy_cell"));

        helper.runAfterDelay(5, () -> insertAnywhere(helper, chest, new ItemStack(item("ae2:item_storage_cell_1k"))));
        helper.runAfterDelay(60, () -> start.accept(new ItemStack(Items.COBBLESTONE, 40), Direction.DOWN));

        helper.succeedWhen(() -> {
            long cobblestone = storedInCell(helper, chest, "item1", Items.COBBLESTONE);
            helper.assertTrue(cobblestone == 40, "the cell in the chest holds " + cobblestone + " cobblestone, expected 40");
        });
    }

    // an interface set to keep cobblestone in stock hands it to a provider pipe, and refills from the network
    private static void ae2OrdersFromStockingInterface(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = new BlockPos(0, 2, 1);
        helper.setBlock(target, Blocks.CHEST);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 1);
        BlockPos interfacePos = new BlockPos(1, 2, 1);
        helper.setBlock(interfacePos, block("ae2:interface"));
        BlockPos drive = new BlockPos(2, 2, 1);
        helper.setBlock(drive, block("ae2:drive"));
        helper.setBlock(new BlockPos(3, 2, 1), block("ae2:creative_energy_cell"));

        helper.runAfterDelay(5, () -> {
            insertAnywhere(helper, drive, new ItemStack(item("ae2:item_storage_cell_1k")));
            // the same data the interface screen writes: keep 32 cobblestone in the first slot
            BlockEntity blockEntity = helper.getBlockEntity(interfacePos);
            CompoundTag saved = blockEntity.saveWithoutMetadata(helper.getLevel().registryAccess());
            ListTag config = new ListTag();
            CompoundTag stock = new CompoundTag();
            stock.putString("#t", "ae2:i");
            stock.putString("id", "minecraft:cobblestone");
            stock.putLong("#", 32);
            config.add(stock);
            for (int slot = 1; slot < 9; slot++) {
                config.add(new CompoundTag());
            }
            saved.put("config", config);
            blockEntity.loadWithComponents(saved, helper.getLevel().registryAccess());
            blockEntity.setChanged();
        });
        helper.runAfterDelay(60, () -> insertAnywhere(helper, interfacePos, new ItemStack(Items.COBBLESTONE, 64)));
        helper.runAfterDelay(61, () -> insertAnywhere(helper, interfacePos, new ItemStack(Items.COBBLESTONE, 36)));

        helper.runAfterDelay(200, () -> {
            Integer listed = provider.contents().get(McItemType.batchOf(new ItemStack(Items.COBBLESTONE)).type());
            helper.assertTrue(listed != null && listed == 32, "the interface should offer its 32 stocked cobblestone, provider lists " + listed
                    + ", interface is " + helper.getBlockEntity(interfacePos).saveWithoutMetadata(helper.getLevel().registryAccess()));
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 20))).started(), "request refused");

            helper.succeedWhen(() -> {
                int delivered = count(helper, target, Items.COBBLESTONE);
                long inCell = storedInCell(helper, drive, "item0", Items.COBBLESTONE);
                int restocked = count(helper, interfacePos, Items.COBBLESTONE);
                helper.assertTrue(delivered == 20, "expected 20 delivered but found " + delivered);
                helper.assertTrue(restocked == 32 && inCell == 100 - 32 - 20,
                        "after refilling the interface should hold 32 and the cell 48, found " + restocked + " and " + inCell);
            });
        });
    }

    private static BlockState facing(Block block, Direction direction) {
        BlockState state = block.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty facing && facing.getPossibleValues().contains(direction)) {
                state = state.setValue(facing, direction);
            }
        }
        return state;
    }

    // a generator of another mod next to a junction has to fill it: creative motor, alternator, junction in a row
    private static void alternatorChargesJunction(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1), facing(block("create:creative_motor"), Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 1), facing(block("createaddition:alternator"), Direction.WEST));
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(2, 1, 1), 0);
        PowerJunctionBlockEntity above = GameTestPower.junction(helper, new BlockPos(1, 2, 1), 0);

        helper.succeedWhen(() -> helper.assertTrue(junction.stored() > 0 || above.stored() > 0,
                "no energy arrived, alternator is " + helper.getBlockState(new BlockPos(1, 1, 1)) + " "
                        + helper.getBlockEntity(new BlockPos(1, 1, 1)).saveWithoutMetadata(helper.getLevel().registryAccess())));
    }

    // the same row with a power provider at the end
    private static void alternatorChargesPowerProvider(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1), facing(block("create:creative_motor"), Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 1), facing(block("createaddition:alternator"), Direction.WEST));
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, new BlockPos(2, 1, 1), 0);
        PowerProviderBlockEntity above = GameTestPower.provider(helper, new BlockPos(1, 2, 1), 0);

        helper.succeedWhen(() -> helper.assertTrue(provider.stored() > 0 || above.stored() > 0,
                "no energy arrived, alternator is " + helper.getBlockState(new BlockPos(1, 1, 1))));
    }

    // a machine of another mod next to a pipe with a supplier upgrade gets energy from the provider behind the pipe
    // a cable of mekanism between a generator and a junction has to fill the junction
    // creative motor, alternator, then a cable on each side the alternator may send to
    private static void mekanismCableChargesJunction(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1), facing(block("create:creative_motor"), Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 1), facing(block("createaddition:alternator"), Direction.WEST));
        helper.setBlock(new BlockPos(2, 1, 1), block("mekanism:basic_universal_cable"));
        helper.setBlock(new BlockPos(1, 2, 1), block("mekanism:basic_universal_cable"));
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(3, 1, 1), 0);
        PowerJunctionBlockEntity above = GameTestPower.junction(helper, new BlockPos(1, 3, 1), 0);

        helper.succeedWhen(() -> helper.assertTrue(junction.stored() > 0 || above.stored() > 0,
                "no energy arrived, the cables are " + helper.getBlockState(new BlockPos(2, 1, 1)) + " and "
                        + helper.getBlockState(new BlockPos(1, 2, 1))));
    }

    // what Tekkit players did with ender chests: two bases with no pipe between them, a connector on an ender chest in each
    // ender chests of one colour are one inventory, so this is the only test that uses them
    private static void enderChestsJoinTwoNetworks(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 0);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.CHEST);
        Container stock = helper.getBlockEntity(new BlockPos(0, 2, 1));
        stock.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        PipeBlockEntity near = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 1);
        helper.setBlock(new BlockPos(1, 2, 1), block("enderstorage:ender_chest"));
        PipeBlockEntity far = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 5);
        helper.setBlock(new BlockPos(5, 2, 1), block("enderstorage:ender_chest"));
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 6);
        BlockPos target = new BlockPos(6, 2, 1);
        helper.setBlock(target, Blocks.CHEST);
        // power does not cross a connector, so the other side has its own
        GameTestPower.junction(helper, new BlockPos(6, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        UUID channel = ChannelRegistry.of(helper.getLevel()).channels()
                .create("ender", UUID.randomUUID(), "Tester", ChannelAccess.PRIVATE, null).orElseThrow().id();
        near.connector().setChannel(channel);
        far.connector().setChannel(channel);

        helper.runAfterDelay(10, () -> {
            RequestOutcome outcome = request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 10)));
            helper.assertTrue(outcome.started(), "the order across the ender chests starts, missing " + outcome.missing()
                    + ", connectors " + near.getBlockState() + " and " + far.getBlockState());
        });
        helper.succeedWhen(() -> {
            Container arrived = helper.getBlockEntity(target);
            helper.assertTrue(arrived.countItem(Items.COBBLESTONE) == 10, "the chest of the request pipe holds "
                    + arrived.countItem(Items.COBBLESTONE) + ", the far connector still waits for " + far.connector().expected());
        });
    }

    // the play pack turns off the ore of Extreme Reactors, so its reactors have to burn the uranium of Mekanism
    private static void mekanismUraniumIsReactorFuel(GameTestHelper helper) {
        try {
            Method fuelOf = Class.forName("it.zerono.mods.extremereactors.api.reactor.ReactantMappingsRegistry")
                    .getMethod("getFromSolid", ItemStack.class);
            for (String id : List.of("mekanism:ingot_uranium", "bigreactors:yellorium_ingot")) {
                helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)), "no item " + id);
                Object fuel = fuelOf.invoke(null, new ItemStack(item(id)));
                helper.assertTrue(((java.util.Optional<?>) fuel).isPresent(), id + " is not reactor fuel");
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("could not ask Extreme Reactors: " + e);
        }
        helper.succeed();
    }

    private static void supplierPipeChargesElectricMotor(GameTestHelper helper) {
        GameTestPower.junction(helper, new BlockPos(1, 1, 2), 10_000);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, new BlockPos(1, 1, 0), 1_000_000);
        BlockPos motor = new BlockPos(1, 2, 1);
        helper.setBlock(motor, facing(block("createaddition:electric_motor"), Direction.UP));
        PowerSupplierGameTest.pipe(helper, new BlockPos(1, 1, 1), UpgradeKind.POWER_SUPPLIER_RF);

        helper.succeedWhen(() -> {
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(motor),
                    Direction.DOWN);
            helper.assertTrue(energy != null && energy.getEnergyStored() > 0, "the motor got no energy, the provider holds "
                    + provider.stored() + " and the motor " + (energy == null ? "takes none" : "is " + helper.getBlockState(motor)));
        });
    }

    // a crafting pipe beside a millstone turned by a creative motor: cobblestone in, gravel out
    private static void createCraftsWithMillstone(GameTestHelper helper) {
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(0, 2, 2), PowerJunctionBlockEntity.CAPACITY);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 2);
        BlockPos target = new BlockPos(0, 3, 1);
        helper.setBlock(target, Blocks.CHEST);
        pipe(helper, PipeKind.PROVIDER, 1, 2);
        BlockPos source = new BlockPos(1, 3, 1);
        helper.setBlock(source, Blocks.CHEST);
        Container stock = helper.getBlockEntity(source);
        stock.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.setBlock(new BlockPos(2, 2, 1), RoutedPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 3, 2);
        BlockPos millstone = new BlockPos(4, 2, 1);
        helper.setBlock(millstone, block("create:millstone"));
        helper.setBlock(new BlockPos(4, 1, 1), facing(block("create:creative_motor"), Direction.UP));
        crafting.config().setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.GRAVEL, 1));

        helper.runAfterDelay(40, () -> helper.assertTrue(
                request.request(McItemType.batchOf(new ItemStack(Items.GRAVEL, 3))).started(), "request refused"));

        helper.succeedWhen(() -> {
            int gravel = count(helper, target, Items.GRAVEL);
            helper.assertTrue(gravel == 3, "expected 3 gravel but found " + gravel + ", millstone is "
                    + helper.getBlockEntity(millstone).saveWithoutMetadata(helper.getLevel().registryAccess()));
            helper.assertTrue(junction.stored() < PowerJunctionBlockEntity.CAPACITY, "moving the items should have cost power");
        });
    }

    // runs the server half of the tooltip the way Jade does when a client looks at a pipe
    // everything of Jade is found by name because there is no compile dependency on it
    private static void jadeSendsPipeDetails(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        basic.config().setItem(0, new ItemStack(Items.DIAMOND));

        helper.runAfterDelay(5, () -> {
            CompoundTag data = new CompoundTag();
            try {
                Class<?> accessorType = Class.forName("snownee.jade.api.BlockAccessor");
                Object accessor = Proxy.newProxyInstance(accessorType.getClassLoader(), new Class<?>[] {accessorType},
                        (proxy, method, arguments) -> switch (method.getName()) {
                            case "getBlockEntity" -> basic;
                            case "getLevel" -> helper.getLevel();
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
                Object registration = Class.forName("snownee.jade.impl.WailaCommonRegistration").getMethod("instance").invoke(null);
                List<?> providers = (List<?>) registration.getClass().getMethod("getBlockNBTProviders", Block.class, BlockEntity.class)
                        .invoke(registration, basic.getBlockState().getBlock(), basic);
                Class<?> providerType = Class.forName("snownee.jade.api.IServerDataProvider");
                for (Object provider : providers) {
                    if (providerType.getMethod("getUid").invoke(provider).toString().equals("routedpipes:pipe_status")) {
                        providerType.getMethod("appendServerData", CompoundTag.class, Class.forName("snownee.jade.api.Accessor"))
                                .invoke(provider, data, accessor);
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            helper.assertTrue(data.getInt("LogisticsPipesRouters") == 0 && data.contains("LogisticsPipesEnergy"),
                    "Jade should get the status of the pipe, got " + data);
            PipeDetails details = PipeDetails.load(data.getCompound("LogisticsPipesDetails"), helper.getLevel().registryAccess());
            helper.assertTrue(details.listed().shown().size() == 1 && details.listed().shown().get(0).stack().is(Items.DIAMOND),
                    "Jade should get the filter of the pipe, got " + data);
            helper.succeed();
        });
    }

    // the transfer handler for the crafting table against the JEI that is installed, as far as that goes without a client:
    // a recipe with slots cannot be made up here, the slot views of JEI need client classes a server refuses to load
    // so the handler gets a recipe without ingredients, and the error it shows is made by hand. Everything is found by name
    private static void jeiTableTransferLinksAndReports(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.CRAFTING, 1);
        BlockPos tablePos = new BlockPos(1, 2, 1);
        helper.setBlock(tablePos, RoutedPipes.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(tablePos);
        table.pattern().setItem(4, new ItemStack(Items.OAK_LOG));

        helper.runAfterDelay(15, () -> {
            TestPlayer player = TestPlayer.in(helper);
            helper.getBlockState(tablePos).useWithoutItem(helper.getLevel(), player, new BlockHitResult(
                    Vec3.atCenterOf(helper.absolutePos(tablePos)), Direction.UP, helper.absolutePos(tablePos), false));
            CraftingTableMenu menu = (CraftingTableMenu) player.containerMenu;
            List<Boolean> orders = new ArrayList<>();
            try {
                // what the client would send goes to the server half directly
                Class<?> senderType = Class.forName("routedpipes.adapter.minecraft.client.ClientMenuLink$Sender");
                Field senderField = Class.forName("routedpipes.adapter.minecraft.client.ClientMenuLink").getDeclaredField("sender");
                senderField.setAccessible(true);
                Object oldSender = senderField.get(null);
                senderField.set(null, Proxy.newProxyInstance(senderType.getClassLoader(), new Class<?>[] {senderType}, (proxy, method, arguments) -> {
                    if (method.getName().equals("setMarker")) {
                        MenuRequests.apply(player, (int) arguments[0], (int) arguments[1], (ItemStack) arguments[2]);
                    } else if (method.getName().equals("askIngredients")) {
                        @SuppressWarnings("unchecked")
                        List<ItemStack> ingredients = (List<ItemStack>) arguments[2];
                        orders.add((boolean) arguments[3]);
                        MenuRequests.askIngredients(player, (int) arguments[0], (int) arguments[1], ingredients, (boolean) arguments[3]);
                    }
                    return null;
                }));
                try {
                    Class<?> slotsType = Class.forName("mezz.jei.api.gui.ingredient.IRecipeSlotsView");
                    Object slots = Proxy.newProxyInstance(slotsType.getClassLoader(), new Class<?>[] {slotsType}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getSlotViews")) {
                            return List.of();
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
                    Class<?> handlerType = Class.forName("routedpipes.adapter.jei.RoutedPipesJeiPlugin$TableTransfer");
                    Constructor<?> constructor = handlerType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object handler = constructor.newInstance();
                    // only methods the classes declare themselves are looked up, the ones inherited from JEI mention client classes
                    Method transfer = handlerType.getDeclaredMethod("transferRecipe", CraftingTableMenu.class, RecipeHolder.class, slotsType,
                            Player.class, boolean.class, boolean.class);
                    transfer.setAccessible(true);

                    helper.assertTrue(transfer.invoke(handler, menu, null, slots, player, false, false) == null, "looking at a recipe is no error");
                    helper.assertTrue(orders.equals(List.of(false)) && menu.answeredQuestion() == 1
                            && menu.network() == CraftingTableMenu.NETWORK_SUPPLIES, "the look asked the server, which had nothing to bring");
                    helper.assertTrue(table.pattern().getItem(4).is(Items.OAK_LOG), "and changed nothing");
                    helper.assertTrue(transfer.invoke(handler, menu, null, slots, player, false, true) == null, "the transfer goes through");
                    helper.assertTrue(table.pattern().isEmpty() && orders.equals(List.of(false, true)), "it sets the recipe grid and orders");

                    // the error for a recipe the network cannot serve, with what the server really reports as missing
                    MenuRequests.askIngredients(player, menu.containerId, 5, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS)), false);
                    Constructor<?> errorConstructor = Class.forName("routedpipes.adapter.jei.RoutedPipesJeiPlugin$TableTransfer$NetworkShortfall")
                            .getDeclaredConstructor(List.class);
                    errorConstructor.setAccessible(true);
                    Object error = errorConstructor.newInstance(menu.shortfall());
                    Method getType = error.getClass().getDeclaredMethod("getType");
                    getType.setAccessible(true);
                    helper.assertTrue(getType.invoke(error).toString().equals("COSMETIC"), "the button stays usable");
                    List<Object> lines = new ArrayList<>();
                    Class<?> tooltipType = Class.forName("mezz.jei.api.gui.builder.ITooltipBuilder");
                    Object tooltip = Proxy.newProxyInstance(tooltipType.getClassLoader(), new Class<?>[] {tooltipType}, (proxy, method, arguments) -> {
                        if (method.getName().equals("add")) {
                            lines.add(arguments[0]);
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
                    Method getTooltip = error.getClass().getDeclaredMethod("getTooltip", tooltipType);
                    getTooltip.setAccessible(true);
                    getTooltip.invoke(error, tooltip);
                    helper.assertTrue(lines.size() == 2 && lines.get(1).toString().contains("oak_planks") && lines.get(1).toString().contains("2"),
                            "the tooltip names the two planks the network cannot bring, got " + lines);
                } finally {
                    senderField.set(null, oldSender);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            helper.succeed();
        });
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body, int maxTicks) {
        return new TestFunction("defaultBatch", name, RoutedPipes.MODID + ":empty", maxTicks, 0, true, body);
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("defaultBatch", name, RoutedPipes.MODID + ":empty", 300, 0, true, body);
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, RoutedPipes.pipe(kind));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        // in these tests a basic pipe takes everything unless the test says otherwise
        pipe.setDefaultRoute(kind == PipeKind.BASIC);
        return pipe;
    }

    // the inventory as the pipe below it sees it
    private static IItemHandler handler(GameTestHelper helper, BlockPos pos) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.DOWN);
        helper.assertTrue(handler != null, "no item handler on the bottom face");
        return handler;
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        IItemHandler handler = handler(helper, pos);
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(item)) {
                count += handler.getStackInSlot(slot).getCount();
            }
        }
        return count;
    }

    private static void deliversInto(GameTestHelper helper, Block inventory) {
        GameTestPower.supply(helper);
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        helper.setBlock(new BlockPos(1, 1, 1), RoutedPipes.TRANSPORT_PIPE.get());
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos target = new BlockPos(2, 2, 1);
        helper.setBlock(target, inventory);

        helper.runAfterDelay(15, () -> start.accept(new ItemStack(Items.COBBLESTONE, 40), Direction.DOWN));

        helper.succeedWhen(() -> {
            int arrived = count(helper, target, Items.COBBLESTONE);
            helper.assertTrue(arrived == 40, "expected 40 cobblestone in the inventory but found " + arrived);
        });
    }

    private static void ordersFrom(GameTestHelper helper, Block inventory) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = new BlockPos(0, 2, 1);
        helper.setBlock(target, Blocks.CHEST);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 1);
        BlockPos source = new BlockPos(1, 2, 1);
        helper.setBlock(source, inventory);

        helper.runAfterDelay(15, () -> {
            // more than a stack, bins and drawers hand out one stack per call and all of it has to come
            int stocked = 0;
            for (int round = 0; round < 4; round++) {
                stocked += 50 - ItemHandlerHelper.insertItemStacked(handler(helper, source), new ItemStack(Items.COBBLESTONE, 50), false).getCount();
            }
            int wanted = Math.min(stocked, 130);
            helper.assertTrue(stocked >= 20, "could only put " + stocked + " cobblestone in");
            int putIn = stocked;

            Integer listed = provider.contents().get(McItemType.batchOf(new ItemStack(Items.COBBLESTONE)).type());
            helper.assertTrue(listed != null && listed == stocked, "holds " + stocked + " but the provider lists " + listed);
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, wanted))).started(), "request refused");

            helper.succeedWhen(() -> {
                int delivered = count(helper, target, Items.COBBLESTONE);
                int left = count(helper, source, Items.COBBLESTONE);
                helper.assertTrue(delivered == wanted, "expected " + wanted + " delivered but found " + delivered);
                helper.assertTrue(left == putIn - wanted, "expected " + (putIn - wanted) + " left but found " + left);
            });
        });
    }

    // a slot that holds 1000 items but only ever hands out 64 at a time, like a bin
    private static final class BulkSlot implements IItemHandler {
        private int stored = 1000;

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? new ItemStack(Items.COBBLESTONE, stored) : new ItemStack(Items.DIAMOND, 3);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        // the diamonds in slot 1 are locked in
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(Math.min(amount, 64), stored);
            if (!simulate) {
                stored -= taken;
            }
            return new ItemStack(Items.COBBLESTONE, taken);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1000;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    @GameTest(template = "empty")
    public static void bulkSlotsAreCountedInFullAndLockedSlotsNotAtAll(GameTestHelper helper) {
        List<ItemStack> contents = CapabilityItemStorage.contentsOf(new BulkSlot());

        helper.assertTrue(contents.size() == 1, "only the extractable slot counts, got " + contents);
        helper.assertTrue(contents.get(0).is(Items.COBBLESTONE) && contents.get(0).getCount() == 1000,
                "the whole slot content counts, got " + contents.get(0));
        helper.succeed();
    }
}
