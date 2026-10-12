package logisticspipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.neoforge.CapabilityItemStorage;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.PipeKind;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

// the same delivery and ordering checks against inventories of vanilla and of other mods
// blocks of mods that are not installed are skipped, so the normal test run only covers the vanilla ones
// hoppers are left out, one on top of a pipe empties itself into the pipe
@GameTestHolder(LogisticsPipes.MODID)
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
            "create:basin");

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

    // tests that need a working machine or network of another mod
    @GameTestGenerator
    public static Collection<TestFunction> machines() {
        List<TestFunction> tests = new ArrayList<>();
        if (has("ae2:interface")) {
            tests.add(test("compat.ae2.stores_through_interface", CompatGameTest::ae2StoresThroughInterface, 400));
            tests.add(test("compat.ae2.stores_into_me_chest", CompatGameTest::ae2StoresIntoMeChest, 400));
            tests.add(test("compat.ae2.orders_from_stocking_interface", CompatGameTest::ae2OrdersFromStockingInterface, 600));
        }
        if (has("create:millstone")) {
            tests.add(test("compat.create.crafts_with_millstone", CompatGameTest::createCraftsWithMillstone, 2400));
        }
        return tests;
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
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
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
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        helper.setBlock(new BlockPos(1, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
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
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        helper.setBlock(new BlockPos(1, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
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
        helper.setBlock(new BlockPos(2, 2, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 3, 2);
        BlockPos millstone = new BlockPos(4, 2, 1);
        helper.setBlock(millstone, block("create:millstone"));
        BlockState motor = block("create:creative_motor").defaultBlockState();
        for (Property<?> property : motor.getProperties()) {
            if (property instanceof DirectionProperty facing && facing.getPossibleValues().contains(Direction.UP)) {
                motor = motor.setValue(facing, Direction.UP);
            }
        }
        helper.setBlock(new BlockPos(4, 1, 1), motor);
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

    private static TestFunction test(String name, Consumer<GameTestHelper> body, int maxTicks) {
        return new TestFunction("defaultBatch", name, LogisticsPipes.MODID + ":empty", maxTicks, 0, true, body);
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("defaultBatch", name, LogisticsPipes.MODID + ":empty", 300, 0, true, body);
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
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
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 0);
        start.setDefaultRoute(false);
        helper.setBlock(new BlockPos(1, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
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
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler(helper, source), new ItemStack(Items.COBBLESTONE, 64), false);
            rest = ItemHandlerHelper.insertItemStacked(handler(helper, source), new ItemStack(Items.COBBLESTONE, 36 + rest.getCount()), false);
            int stocked = 100 - rest.getCount();
            helper.assertTrue(stocked >= 20, "could only put " + stocked + " cobblestone in");

            Integer listed = provider.contents().get(McItemType.batchOf(new ItemStack(Items.COBBLESTONE)).type());
            helper.assertTrue(listed != null && listed == stocked, "holds " + stocked + " but the provider lists " + listed);
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 20))).started(), "request refused");

            helper.succeedWhen(() -> {
                int delivered = count(helper, target, Items.COBBLESTONE);
                int left = count(helper, source, Items.COBBLESTONE);
                helper.assertTrue(delivered == 20, "expected 20 delivered but found " + delivered);
                helper.assertTrue(left == stocked - 20, "expected " + (stocked - 20) + " left but found " + left);
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
