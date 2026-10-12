package logisticspipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
            "create:item_vault");

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
