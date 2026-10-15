package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.KeptIngredients;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.ProgrammerItem;
import routedpipes.adapter.minecraft.RequestTable;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.program.ProgramCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

// a programmer comes out of the craft as it went in, so one does for any number of crafts
public class KeptIngredientGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;

    private static ItemStack programmer() {
        ItemStack programmer = new ItemStack(Content.LOGISTICS_PROGRAMMER.get());
        ProgrammerItem.flash(programmer, ProgramCatalog.byId("request_pipe").orElseThrow());
        return programmer;
    }

    private static Item chip() {
        return RecipeGameTest.item("chip_basic");
    }

    private static Item basicPipe() {
        return RecipeGameTest.item("basic_logistics_pipe");
    }

    private static Item requestPipe() {
        return RecipeGameTest.item("request_pipe");
    }

    // the grid of a request pipe
    private static void fill(Container grid) {
        grid.setItem(1, programmer());
        grid.setItem(3, new ItemStack(Items.REDSTONE));
        grid.setItem(4, new ItemStack(chip()));
        grid.setItem(5, new ItemStack(Items.REDSTONE));
        grid.setItem(6, new ItemStack(Items.IRON_INGOT));
        grid.setItem(7, new ItemStack(basicPipe()));
        grid.setItem(8, new ItemStack(Items.IRON_INGOT));
    }

    // what that many request pipes are made of, without the programmer
    private static ItemStack[] materials(int crafts) {
        return new ItemStack[] {new ItemStack(Items.REDSTONE, 2 * crafts), new ItemStack(chip(), crafts),
                new ItemStack(Items.IRON_INGOT, 2 * crafts), new ItemStack(basicPipe(), crafts)};
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
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

    private static void assertCount(GameTestHelper helper, Container container, Item item, int expected, String what) {
        helper.assertTrue(container.countItem(item) == expected,
                what + ": expected " + expected + " " + item + " but found " + container.countItem(item));
    }

    @GameTest(template = EMPTY)
    public static void tableCraftsAsOftenAsTheMaterialsAllowWithOneProgrammer(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos pos = new BlockPos(0, 1, 1);
        helper.setBlock(pos, Content.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(pos);
        fill(table.pattern());
        table.storage().setItem(0, programmer());
        ItemStack[] materials = materials(3);
        for (int slot = 0; slot < materials.length; slot++) {
            table.storage().setItem(slot + 1, materials[slot]);
        }

        helper.assertTrue(table.available().is(requestPipe()) && table.available().getCount() == 3,
                "three request pipes can be taken, got " + table.available());
        ItemStack taken = table.takeResult(3, false);
        helper.assertTrue(taken.is(requestPipe()) && taken.getCount() == 3, "took " + taken);
        assertCount(helper, table.storage(), Content.LOGISTICS_PROGRAMMER.get(), 1, "the programmer stays");
        assertCount(helper, table.storage(), chip(), 0, "the chips are used up");

        table.storage().clearContent();
        materials = materials(1);
        for (int slot = 0; slot < materials.length; slot++) {
            table.storage().setItem(slot, materials[slot]);
        }
        helper.assertTrue(table.available().isEmpty(), "nothing without the programmer");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void requestTableOrdersOneProgrammerForSeveralCrafts(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity pipe = pipe(helper, PipeKind.REQUEST_TABLE, 0);
        RequestTable table = pipe.requestTable();
        pipe(helper, PipeKind.PROVIDER, 1);
        ItemStack[] materials = materials(3);
        Container source = chest(helper, 1, programmer(), materials[0], materials[1], materials[2], materials[3]);
        fill(table.grid());

        helper.runAfterDelay(SETTLE + 15, () -> {
            helper.assertTrue(table.missing(3).isEmpty(), "one programmer does for three crafts, missing " + table.missing(3));
            helper.assertTrue(table.orderAndCraft(3).started(), "three crafts are ordered");
        });
        helper.runAfterDelay(200, () -> {
            assertCount(helper, table.buffer(), requestPipe(), 3, "three request pipes");
            assertCount(helper, table.buffer(), Content.LOGISTICS_PROGRAMMER.get(), 1, "the programmer is still there");
            assertCount(helper, source, Content.LOGISTICS_PROGRAMMER.get(), 0, "it came from the network");
            helper.assertTrue(table.pending() == 0, "nothing left to craft");
            helper.succeed();
        });
    }

    // the programmer is brought once and stays in the table, the next request does not need another one
    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void craftingPipeBringsTheProgrammerOnlyWhenTheTableHasNone(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        Container target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        ItemStack[] materials = materials(4);
        Container source = chest(helper, 1, programmer(), materials[0], materials[1], materials[2], materials[3]);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 2);
        BlockPos tablePos = new BlockPos(2, 2, 1);
        helper.setBlock(tablePos, Content.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(tablePos);
        fill(table.pattern());
        crafting.importRecipe();

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(
                request.request(McItemType.batchOf(new ItemStack(requestPipe(), 3))).started(), "first request"));
        helper.runAfterDelay(220, () -> {
            assertCount(helper, target, requestPipe(), 3, "first delivery");
            assertCount(helper, table.storage(), Content.LOGISTICS_PROGRAMMER.get(), 1, "the programmer stays in the table");
            assertCount(helper, source, Content.LOGISTICS_PROGRAMMER.get(), 0, "only one was taken");
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(requestPipe(), 1))).started(),
                    "the second request needs no programmer from the network");
        });
        helper.runAfterDelay(440, () -> {
            assertCount(helper, target, requestPipe(), 4, "second delivery");
            assertCount(helper, table.storage(), Content.LOGISTICS_PROGRAMMER.get(), 1, "still the one programmer");
            helper.succeed();
        });
    }

    // a crafter module in a chassis does the same as a crafting pipe, the network has no programmer here
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void crafterModuleUsesTheProgrammerItsTableHas(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        Container target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        ItemStack[] materials = materials(2);
        chest(helper, 1, materials);
        BlockPos chassisPos = new BlockPos(2, 1, 1);
        helper.setBlock(chassisPos, Content.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        BlockPos tablePos = new BlockPos(2, 2, 1);
        helper.setBlock(tablePos, Content.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(tablePos);
        fill(table.pattern());
        table.storage().setItem(0, programmer());
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < CraftingTableBlockEntity.PATTERN_SLOTS; slot++) {
            inputs.add(table.pattern().getItem(slot).copy());
        }
        ItemStack crafter = new ItemStack(Content.MODULES.get(ModuleKind.CRAFTER).get());
        ModuleItem.configureCrafter(crafter, inputs, new ItemStack(requestPipe()), "", 0);
        chassis.modules().setItem(0, crafter);

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome outcome = request.request(McItemType.batchOf(new ItemStack(requestPipe(), 2)));
            helper.assertTrue(outcome.started(), "two request pipes without a programmer from the network, missing " + outcome.missing());
        });
        helper.runAfterDelay(250, () -> {
            assertCount(helper, target, requestPipe(), 2, "delivered");
            assertCount(helper, table.storage(), Content.LOGISTICS_PROGRAMMER.get(), 1, "the programmer stays");
            helper.succeed();
        });
    }

    // shears that wear down by one with every craft, like a tool of some other mod
    private static final CraftingRecipe WEARING = new CustomRecipe(CraftingBookCategory.MISC) {
        @Override
        public boolean matches(CraftingInput input, Level level) {
            return true;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return new ItemStack(Items.IRON_NUGGET);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < input.size(); slot++) {
                ItemStack stack = input.getItem(slot);
                if (stack.is(Items.SHEARS) && stack.getDamageValue() + 1 < stack.getMaxDamage()) {
                    ItemStack worn = stack.copy();
                    worn.setDamageValue(stack.getDamageValue() + 1);
                    remaining.set(slot, worn);
                }
            }
            return remaining;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return true;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return null;
        }
    };

    private static List<ItemStack> wearingGrid() {
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.SHEARS));
        grid.set(1, new ItemStack(Items.IRON_INGOT));
        return grid;
    }

    @GameTest(template = EMPTY)
    public static void aWornToolIsUsedUntilItBreaks(GameTestHelper helper) {
        List<ItemStack> grid = wearingGrid();
        helper.assertTrue(KeptIngredients.of(WEARING, CraftingInput.of(3, 3, grid)).size() == 1, "the shears are kept");
        SimpleContainer storage = new SimpleContainer(4);
        ItemStack shears = new ItemStack(Items.SHEARS);
        shears.setDamageValue(shears.getMaxDamage() - 3);
        storage.setItem(0, shears);
        storage.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        List<ItemStack> made = new ArrayList<>();

        for (int craft = 0; craft < 5; craft++) {
            if (KeptIngredients.craft(WEARING, grid, storage, left -> storage.addItem(left))) {
                made.add(new ItemStack(Items.IRON_NUGGET));
            }
        }

        helper.assertTrue(made.size() == 3, "worn shears that have three uses left craft three times, made " + made.size());
        helper.assertTrue(storage.countItem(Items.SHEARS) == 0, "and then they are gone");
        helper.assertTrue(storage.countItem(Items.IRON_INGOT) == 2, "the iron is used once per craft");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aWornToolWearsDownInTheTableAndCountsAsThere(GameTestHelper helper) {
        List<ItemStack> grid = wearingGrid();
        SimpleContainer storage = new SimpleContainer(4);
        ItemStack shears = new ItemStack(Items.SHEARS);
        shears.setDamageValue(10);
        storage.setItem(0, shears);
        storage.setItem(1, new ItemStack(Items.IRON_INGOT, 2));

        helper.assertTrue(KeptIngredients.craft(WEARING, grid, storage, left -> storage.addItem(left)), "a craft with worn shears");
        ItemStack after = storage.getItem(0).is(Items.SHEARS) ? storage.getItem(0) : storage.getItem(2);
        helper.assertTrue(after.is(Items.SHEARS) && after.getDamageValue() == 11, "the very shears wear down, got " + after);
        helper.succeed();
    }
}
