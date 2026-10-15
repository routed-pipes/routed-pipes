package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.Platform;
import routedpipes.adapter.minecraft.ItemStorage;
import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.CraftingTableMenu;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.application.RequestOutcome;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.adapter.minecraft.menu.ChassisMenu;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class CraftingTableGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;

    private static CraftingTableBlockEntity table(GameTestHelper helper, int x, int y, Item... pattern) {
        BlockPos pos = new BlockPos(x, y, 1);
        helper.setBlock(pos, Content.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(pos);
        for (int slot = 0; slot < pattern.length; slot++) {
            if (pattern[slot] != null) {
                table.pattern().setItem(slot, new ItemStack(pattern[slot]));
            }
        }
        return table;
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        // in these tests a basic pipe takes everything unless the test says otherwise
        pipe.setDefaultRoute(kind == PipeKind.BASIC);
        return pipe;
    }

    private static BlockPos chest(GameTestHelper helper, int x, ItemStack... contents) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return pos;
    }

    private static void assertStack(GameTestHelper helper, ItemStack stack, Item item, int count, String what) {
        helper.assertTrue(stack.is(item) && stack.getCount() == count, what + ": expected " + count + " " + item + " but got " + stack);
    }

    private static void assertCount(GameTestHelper helper, Container container, Item item, int expected, String what) {
        helper.assertTrue(container.countItem(item) == expected,
                what + ": expected " + expected + " " + item + " but found " + container.countItem(item));
    }

    @GameTest(template = EMPTY)
    public static void knowsTheResultOfItsRecipeGrid(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1, Items.OAK_LOG);

        assertStack(helper, table.result(), Items.OAK_PLANKS, 4, "result");
        helper.assertTrue(table.available().isEmpty(), "nothing to take without ingredients");

        table.pattern().setItem(0, new ItemStack(Items.DIRT));
        helper.assertTrue(table.result().isEmpty(), "dirt alone is not a recipe");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftsOnlyWhenTheResultIsTaken(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1, Items.OAK_LOG);
        table.storage().setItem(0, new ItemStack(Items.OAK_LOG, 3));

        assertStack(helper, table.available(), Items.OAK_PLANKS, 12, "available");
        assertStack(helper, table.takeResult(6, true), Items.OAK_PLANKS, 6, "simulated");
        assertCount(helper, table.storage(), Items.OAK_LOG, 3, "simulation must not use ingredients");

        assertStack(helper, table.takeResult(6, false), Items.OAK_PLANKS, 6, "taken");
        assertCount(helper, table.storage(), Items.OAK_LOG, 1, "two crafts for six planks");
        assertStack(helper, table.surplus().getItem(0), Items.OAK_PLANKS, 2, "left over");

        assertStack(helper, table.takeResult(64, false), Items.OAK_PLANKS, 6, "rest");
        helper.assertTrue(table.storage().isEmpty() && table.surplus().isEmpty(), "everything used up");
        helper.assertTrue(table.takeResult(1, false).isEmpty(), "nothing left to craft");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void usesShapedRecipesAndEveryGridSlot(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1, Items.OAK_PLANKS, null, null, Items.OAK_PLANKS);
        table.storage().setItem(0, new ItemStack(Items.OAK_PLANKS, 5));

        assertStack(helper, table.available(), Items.STICK, 8, "two crafts from five planks");
        assertStack(helper, table.takeResult(8, false), Items.STICK, 8, "sticks");
        assertCount(helper, table.storage(), Items.OAK_PLANKS, 1, "one plank cannot make sticks");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void keepsWhatARecipeLeavesBehind(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1, Items.HONEY_BOTTLE);
        table.storage().setItem(0, new ItemStack(Items.HONEY_BOTTLE, 1));

        assertStack(helper, table.takeResult(3, false), Items.SUGAR, 3, "sugar");
        assertCount(helper, table.storage(), Items.GLASS_BOTTLE, 1, "empty bottle goes back into storage");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void otherBlocksInsertIngredientsAndExtractOnlyResults(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1, Items.OAK_LOG);
        ItemStorage items = Platform.get().items();
        BlockPos tablePos = helper.absolutePos(new BlockPos(0, 1, 1));
        helper.assertTrue(items.exists(helper.getLevel(), tablePos, Direction.DOWN), "table should expose an item handler");

        ItemStack rest = items.insert(helper.getLevel(), tablePos, Direction.DOWN, new ItemStack(Items.OAK_LOG, 2), false);
        helper.assertTrue(rest.isEmpty(), "logs should fit");
        assertCount(helper, table.storage(), Items.OAK_LOG, 2, "storage");

        int result = items.held(helper.getLevel(), tablePos, Direction.DOWN).size() - 1;
        for (int slot = 0; slot < result; slot++) {
            helper.assertTrue(items.extractSlot(helper.getLevel(), tablePos, Direction.DOWN, slot, 64).isEmpty(),
                    "ingredients must stay inside");
        }
        assertStack(helper, items.held(helper.getLevel(), tablePos, Direction.DOWN).get(result), Items.OAK_PLANKS, 8, "offered");
        assertStack(helper, items.extractSlot(helper.getLevel(), tablePos, Direction.DOWN, result, 4), Items.OAK_PLANKS, 4, "extracted");
        assertCount(helper, table.storage(), Items.OAK_LOG, 1, "one log used");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void menuSetsTheRecipeGridWithMarkers(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingTableMenu menu = new CraftingTableMenu(Content.CRAFTING_TABLE_MENU.get(), 1, player.getInventory(), table);

        menu.setCarried(new ItemStack(Items.OAK_LOG, 16));
        menu.clicked(4, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 16, "held stack must stay in hand");
        assertStack(helper, table.pattern().getItem(4), Items.OAK_LOG, 1, "marker");
        menu.broadcastChanges();
        assertStack(helper, menu.getSlot(9).getItem(), Items.OAK_PLANKS, 4, "preview");

        menu.clicked(9, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.OAK_LOG), "the preview cannot be taken");

        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(4, 0, ClickType.PICKUP, player);
        helper.assertTrue(table.pattern().isEmpty(), "empty hand clears the marker");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void shiftClickMovesItemsBetweenPlayerAndStorage(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 10));
        CraftingTableMenu menu = new CraftingTableMenu(Content.CRAFTING_TABLE_MENU.get(), 1, player.getInventory(), table);
        int hotbar = menu.slots.size() - 9;

        menu.quickMoveStack(player, hotbar);
        assertCount(helper, table.storage(), Items.OAK_LOG, 10, "into storage");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "out of the hotbar");

        menu.quickMoveStack(player, 10);
        helper.assertTrue(table.storage().isEmpty(), "out of storage");
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 10, "back to the player");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tableStateSurvivesSaveAndLoad(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity original = table(helper, 0, 1, Items.OAK_LOG);
        original.storage().setItem(3, new ItemStack(Items.OAK_LOG, 7));
        original.takeResult(2, false);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        CraftingTableBlockEntity loaded = table(helper, 4, 1);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        assertStack(helper, loaded.result(), Items.OAK_PLANKS, 4, "recipe");
        assertStack(helper, loaded.storage().getItem(3), Items.OAK_LOG, 6, "storage slot");
        assertStack(helper, loaded.surplus().getItem(0), Items.OAK_PLANKS, 2, "left over");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void craftingPipeImportsTheRecipeAndCraftsOnRequest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, new ItemStack(Items.OAK_LOG, 16));
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 2);
        CraftingTableBlockEntity table = table(helper, 2, 2, Items.OAK_LOG);

        crafting.importRecipe();
        helper.assertTrue(crafting.recipe().isPresent(), "recipe should be imported from the table");

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome plan = request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 10)));
            helper.assertTrue(plan.started(), "planks should be craftable, missing " + plan.missing());
        });

        helper.succeedWhen(() -> {
            Container chest = helper.getBlockEntity(target);
            assertCount(helper, chest, Items.OAK_PLANKS, 10, "delivered");
            assertStack(helper, table.surplus().getItem(0), Items.OAK_PLANKS, 2, "third craft leaves two");
        });
    }

    // sticks need planks, planks need logs, each step on its own table
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void craftsIngredientsOnAnotherTableFirst(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        BlockPos source = chest(helper, 1, new ItemStack(Items.OAK_LOG, 16));
        PipeBlockEntity planks = pipe(helper, PipeKind.CRAFTING, 2);
        table(helper, 2, 2, Items.OAK_LOG);
        PipeBlockEntity sticks = pipe(helper, PipeKind.CRAFTING, 3);
        table(helper, 3, 2, Items.OAK_PLANKS, null, null, Items.OAK_PLANKS);
        planks.importRecipe();
        sticks.importRecipe();

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome plan = request.request(McItemType.batchOf(new ItemStack(Items.STICK, 8)));
            helper.assertTrue(plan.started(), "sticks should be craftable, missing " + plan.missing());
        });

        helper.succeedWhen(() -> {
            Container chest = helper.getBlockEntity(target);
            Container logs = helper.getBlockEntity(source);
            assertCount(helper, chest, Items.STICK, 8, "delivered");
            assertCount(helper, logs, Items.OAK_LOG, 15, "one log is enough");
        });
    }

    @GameTest(template = EMPTY)
    public static void markersFromTheClientFillTheRecipeGridOneItemEach(GameTestHelper helper) {
        GameTestPower.supply(helper);
        CraftingTableBlockEntity table = table(helper, 0, 1);
        TestPlayer player = TestPlayer.in(helper);
        player.containerMenu = new CraftingTableMenu(Content.CRAFTING_TABLE_MENU.get(), 7, player.getInventory(), table);

        MenuRequests.apply(player, 7, 0, new ItemStack(Items.OAK_PLANKS, 30));
        MenuRequests.apply(player, 7, 3, new ItemStack(Items.OAK_PLANKS, 30));
        MenuRequests.apply(player, 7, 9, new ItemStack(Items.DIAMOND));
        MenuRequests.apply(player, 7, 10, new ItemStack(Items.DIAMOND));

        assertStack(helper, table.pattern().getItem(0), Items.OAK_PLANKS, 1, "grid marker");
        assertStack(helper, table.result(), Items.STICK, 4, "recipe");
        helper.assertTrue(table.storage().isEmpty(), "the preview and the storage cannot be set from the client");
        helper.succeed();
    }

    // the two planks left over from an earlier craft fill the next small order, no log is used for it
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void leftoverOutputIsUsedForTheNextRequest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        BlockPos source = chest(helper, 1, new ItemStack(Items.OAK_LOG, 16));
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 2);
        CraftingTableBlockEntity table = table(helper, 2, 2, Items.OAK_LOG);
        crafting.importRecipe();

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(
                request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 10))).started(), "first request"));
        helper.runAfterDelay(150, () -> {
            Container chest = helper.getBlockEntity(target);
            Container logs = helper.getBlockEntity(source);
            assertCount(helper, chest, Items.OAK_PLANKS, 10, "first delivery");
            assertCount(helper, logs, Items.OAK_LOG, 13, "three logs for ten planks");
            Integer listed = request.available().get(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS)).type());
            helper.assertTrue(listed != null && listed == 2, "the two spare planks should be on offer, listed " + listed);
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 2))).started(), "second request");
        });
        helper.runAfterDelay(300, () -> {
            Container chest = helper.getBlockEntity(target);
            Container logs = helper.getBlockEntity(source);
            assertCount(helper, chest, Items.OAK_PLANKS, 12, "second delivery");
            assertCount(helper, logs, Items.OAK_LOG, 13, "no log used for the spare planks");
            helper.assertTrue(table.surplus().isEmpty(), "the spare planks are gone");
            helper.succeed();
        });
    }

    // the same as a crafting pipe, but as a module in a chassis that points at the table
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void crafterModuleCraftsOnRequest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, new ItemStack(Items.OAK_LOG, 16));
        BlockPos chassisPos = new BlockPos(2, 1, 1);
        helper.setBlock(chassisPos, Content.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        ItemStack crafter = new ItemStack(Content.MODULES.get(ModuleKind.CRAFTER).get());
        ModuleItem.configureCrafter(crafter, List.of(new ItemStack(Items.OAK_LOG)), new ItemStack(Items.OAK_PLANKS, 4), "", 0);
        chassis.modules().setItem(0, crafter);
        table(helper, 2, 2, Items.OAK_LOG);

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome plan = request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 8)));
            helper.assertTrue(plan.started(), "planks should be craftable, missing " + plan.missing());
        });

        helper.succeedWhen(() -> {
            Container chest = helper.getBlockEntity(target);
            assertCount(helper, chest, Items.OAK_PLANKS, 8, "delivered");
        });
    }

    // the settings button in the chassis opens the module where it sits, and import reads the table next to the pipe
    @GameTest(template = EMPTY)
    public static void crafterModuleIsSetUpInsideTheChassis(GameTestHelper helper) {
        BlockPos chassisPos = new BlockPos(2, 1, 1);
        helper.setBlock(chassisPos, Content.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        chassis.modules().setItem(0, new ItemStack(Content.MODULES.get(ModuleKind.CRAFTER).get()));
        table(helper, 2, 2, Items.OAK_LOG);
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(chassis.menuProvider());
            player.containerMenu.clickMenuButton(player, ChassisMenu.BUTTON_SETTINGS);
            helper.assertTrue(player.containerMenu instanceof PipeConfigMenu, "the module settings should be open");
            player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_IMPORT_RECIPE);
            player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_PRIORITY_UP);
            helper.assertTrue(chassis.crafts().size() == 1, "the module should have the recipe of the table");
            helper.assertTrue(chassis.crafts().get(0).recipe().output().equals(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 4))),
                    "four planks");
            helper.assertTrue(chassis.crafts().get(0).priority() == 1, "priority one");
            helper.succeed();
        });
    }

    // the table's storage cannot be taken out of from outside, but a supplier on it still counts what is in it
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void supplierOnTheTableCountsItsStorage(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 0);
        supplier.config().setItem(0, new ItemStack(Items.RAW_IRON, 16));
        supplier.setMode(routedpipes.domain.logistics.SupplyMode.PARTIAL.ordinal());
        CraftingTableBlockEntity table = table(helper, 0, 2, Items.RAW_IRON);
        table.storage().setItem(0, new ItemStack(Items.RAW_IRON, 32));
        pipe(helper, PipeKind.PROVIDER, 1);
        BlockPos source = chest(helper, 1, new ItemStack(Items.RAW_IRON, 64));

        helper.runAfterDelay(200, () -> {
            Container chest = helper.getBlockEntity(source);
            assertCount(helper, chest, Items.RAW_IRON, 64, "nothing should have been ordered");
            assertCount(helper, table.storage(), Items.RAW_IRON, 32, "the table keeps what it had");
            helper.succeed();
        });
    }
}
