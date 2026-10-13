package routedpipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.CraftingTableMenu;
import routedpipes.adapter.minecraft.menu.IngredientQuestions;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// what the server does when a recipe viewer moves a recipe into a crafting table that has a pipe next to it
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class CraftingTableOrderGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;
    // the grid of the stick recipe the way a recipe viewer sends it
    private static final List<ItemStack> STICKS = List.of(new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY,
            new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

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

    private static CraftingTableBlockEntity table(GameTestHelper helper, int x) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, LogisticsPipes.CRAFTING_TABLE.get());
        return helper.getBlockEntity(pos);
    }

    // a player with the screen of the table at x open
    private static TestPlayer playerAtTable(GameTestHelper helper, int x) {
        TestPlayer player = TestPlayer.in(helper);
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false));
        helper.assertTrue(player.containerMenu instanceof CraftingTableMenu, "the table should be open");
        return player;
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void ordersTheIngredientsThatAreNotInStorage(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 0);
        Container source = chest(helper, 0, new ItemStack(Items.OAK_PLANKS, 16));
        pipe(helper, PipeKind.CRAFTING, 1);
        CraftingTableBlockEntity table = table(helper, 1);
        table.storage().setItem(0, new ItemStack(Items.OAK_PLANKS, 1));

        helper.runAfterDelay(SETTLE, () -> {
            TestPlayer player = playerAtTable(helper, 1);
            CraftingTableMenu menu = (CraftingTableMenu) player.containerMenu;

            MenuRequests.askIngredients(player, menu.containerId, 7, STICKS, false);
            helper.assertTrue(menu.answeredQuestion() == 7, "the answer carries the number of the question");
            helper.assertTrue(menu.network() == CraftingTableMenu.NETWORK_SUPPLIES && menu.shortfall().isEmpty(), "the network has the planks");
            helper.assertTrue(source.countItem(Items.OAK_PLANKS) == 16, "asking orders nothing");

            MenuRequests.askIngredients(player, menu.containerId, 0, STICKS, true);
            helper.assertTrue(source.countItem(Items.OAK_PLANKS) == 15, "one plank was in storage, one is ordered");
            MenuRequests.askIngredients(player, menu.containerId, 0, STICKS, true);
            helper.assertTrue(source.countItem(Items.OAK_PLANKS) == 15, "the plank on its way is not ordered again");
        });

        helper.runAfterDelay(150, () -> {
            helper.assertTrue(table.storage().countItem(Items.OAK_PLANKS) == 2, "the ordered plank arrives in the storage of the table, found "
                    + table.storage().countItem(Items.OAK_PLANKS));
            helper.assertTrue(table.result().isEmpty(), "only the markers set the recipe grid");
            helper.assertTrue(source.countItem(Items.OAK_PLANKS) == 15, "nothing else was taken");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void tellsWhatTheNetworkCannotBringAndThenOrdersNothing(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 0);
        Container source = chest(helper, 0, new ItemStack(Items.OAK_PLANKS, 1), new ItemStack(Items.COAL, 5));
        pipe(helper, PipeKind.REQUEST, 1);
        CraftingTableBlockEntity table = table(helper, 1);
        List<ItemStack> torchesAndMore = List.of(new ItemStack(Items.COAL), new ItemStack(Items.STICK), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS));

        helper.runAfterDelay(SETTLE, () -> {
            TestPlayer player = playerAtTable(helper, 1);
            CraftingTableMenu menu = (CraftingTableMenu) player.containerMenu;

            MenuRequests.askIngredients(player, menu.containerId, 3, torchesAndMore, false);
            List<ItemStack> shortfall = menu.shortfall();
            helper.assertTrue(menu.answeredQuestion() == 3 && menu.network() == CraftingTableMenu.NETWORK_MISSING, "something is missing");
            helper.assertTrue(shortfall.size() == 2 && shortfall.get(0).is(Items.OAK_PLANKS) && CraftingTableMenu.shortfallCount(shortfall.get(0)) == 2
                    && shortfall.get(1).is(Items.STICK) && CraftingTableMenu.shortfallCount(shortfall.get(1)) == 1,
                    "two planks and a stick are missing, got " + shortfall);

            MenuRequests.askIngredients(player, menu.containerId, 0, torchesAndMore, true);
            helper.assertTrue(menu.network() == CraftingTableMenu.NETWORK_MISSING, "ordering says the same");

            MenuRequests.askIngredients(player, menu.containerId + 1, 9, torchesAndMore, false);
            helper.assertTrue(menu.answeredQuestion() == 0, "a question for another menu is ignored");
            player.containerMenu = player.inventoryMenu;
            MenuRequests.askIngredients(player, menu.containerId, 9, torchesAndMore, false);
            helper.assertTrue(menu.answeredQuestion() == 0, "and so is one for a menu that was closed");
        });

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(table.storage().isEmpty(), "not even the coal the network has is brought");
            helper.assertTrue(source.countItem(Items.COAL) == 5 && source.countItem(Items.OAK_PLANKS) == 1, "the chest keeps everything");
            helper.succeed();
        });
    }

    // the planks are not in any chest, another crafting pipe makes them from logs
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void missingIngredientsAreCraftedElsewhereInTheNetwork(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 0);
        Container source = chest(helper, 0, new ItemStack(Items.OAK_LOG, 8));
        pipe(helper, PipeKind.CRAFTING, 1);
        CraftingTableBlockEntity table = table(helper, 1);
        PipeBlockEntity planks = pipe(helper, PipeKind.CRAFTING, 2);
        table(helper, 2).pattern().setItem(0, new ItemStack(Items.OAK_LOG));
        planks.importRecipe();

        helper.runAfterDelay(SETTLE, () -> {
            TestPlayer player = playerAtTable(helper, 1);
            CraftingTableMenu menu = (CraftingTableMenu) player.containerMenu;
            MenuRequests.askIngredients(player, menu.containerId, 1, STICKS, false);
            helper.assertTrue(menu.network() == CraftingTableMenu.NETWORK_SUPPLIES, "planks can be crafted, missing " + menu.shortfall());
            MenuRequests.askIngredients(player, menu.containerId, 0, STICKS, true);
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(table.storage().countItem(Items.OAK_PLANKS) == 2, "two planks are crafted and brought to the table");
            helper.assertTrue(source.countItem(Items.OAK_LOG) == 7, "from one log");
        });
    }

    @GameTest(template = EMPTY)
    public static void aTableWithoutAPipeHasNoNetworkToAsk(GameTestHelper helper) {
        GameTestPower.supply(helper);
        // a provider next to the table cannot order, and a crafting pipe one block away does not reach it
        pipe(helper, PipeKind.PROVIDER, 3);
        table(helper, 3);
        pipe(helper, PipeKind.CRAFTING, 4);
        TestPlayer player = playerAtTable(helper, 3);
        CraftingTableMenu menu = (CraftingTableMenu) player.containerMenu;

        MenuRequests.askIngredients(player, menu.containerId, 5, STICKS, true);

        helper.assertTrue(menu.answeredQuestion() == 5 && menu.network() == CraftingTableMenu.NETWORK_NONE && menu.shortfall().isEmpty(),
                "no network, so nothing to report");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theSlotsThatCarryTheAnswerCannotBeUsedAsStorage(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.REQUEST, 1);
        CraftingTableBlockEntity table = table(helper, 1);

        helper.runAfterDelay(SETTLE, () -> {
            TestPlayer player = playerAtTable(helper, 1);
            CraftingTableMenu menu = (CraftingTableMenu) player.containerMenu;
            MenuRequests.askIngredients(player, menu.containerId, 1, STICKS, false);
            int answerSlot = 9 + 1 + CraftingTableBlockEntity.STORAGE_SLOTS;
            helper.assertTrue(menu.getSlot(answerSlot).getItem().is(Items.OAK_PLANKS) && !menu.getSlot(answerSlot).isActive(),
                    "the missing planks sit in a slot no screen shows");

            menu.clicked(answerSlot, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty(), "they cannot be picked up");
            menu.setCarried(new ItemStack(Items.DIAMOND, 3));
            menu.clicked(answerSlot, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().getCount() == 3 && menu.getSlot(answerSlot).getItem().is(Items.OAK_PLANKS), "nothing can be put in");
            helper.assertTrue(menu.quickMoveStack(player, answerSlot).isEmpty() && player.getInventory().isEmpty(), "shift click moves nothing out");

            // more planks than the 27 backpack slots and the hotbar hold together still do not spill into them
            for (int slot = 0; slot < CraftingTableBlockEntity.STORAGE_SLOTS; slot++) {
                table.storage().setItem(slot, new ItemStack(Items.OAK_PLANKS, 64));
            }
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            menu.quickMoveStack(player, 10);
            helper.assertTrue(table.storage().countItem(Items.OAK_PLANKS) == 64 * CraftingTableBlockEntity.STORAGE_SLOTS,
                    "with a full backpack a shift click moves nothing");
            helper.succeed();
        });
    }

    // the client half of asking against a real menu: its questions go straight to the server half,
    // the answers come back through the menu
    @GameTest(template = EMPTY)
    public static void recipeViewerKeepsAnAnswerPerRecipeAndAsksAgainWhenItIsOld(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 0);
        Container source = chest(helper, 0, new ItemStack(Items.OAK_PLANKS, 1), new ItemStack(Items.COAL, 5));
        pipe(helper, PipeKind.REQUEST, 1);
        table(helper, 1);
        List<ItemStack> coal = List.of(new ItemStack(Items.COAL));

        helper.runAfterDelay(SETTLE, () -> {
            TestPlayer player = playerAtTable(helper, 1);
            CraftingTableMenu menu = (CraftingTableMenu) player.containerMenu;
            long[] clock = {0};
            List<Integer> asked = new ArrayList<>();
            IngredientQuestions questions = new IngredientQuestions((containerId, question, ingredients) -> {
                asked.add(question);
                MenuRequests.askIngredients(player, containerId, question, ingredients, false);
            }, () -> clock[0]);

            helper.assertTrue(questions.answer(menu, STICKS).isEmpty() && asked.equals(List.of(1)), "the first look asks and knows nothing yet");
            Optional<IngredientQuestions.Answer> sticks = questions.answer(menu, STICKS);
            helper.assertTrue(sticks.isPresent() && sticks.get().network() == CraftingTableMenu.NETWORK_MISSING
                    && sticks.get().shortfall().size() == 1 && sticks.get().shortfall().get(0).is(Items.OAK_PLANKS)
                    && CraftingTableMenu.shortfallCount(sticks.get().shortfall().get(0)) == 1, "the next look has the answer: one plank short");
            helper.assertTrue(asked.size() == 1, "a fresh answer is not asked for again");

            helper.assertTrue(questions.answer(menu, coal).isEmpty() && asked.equals(List.of(1, 2)), "another recipe is asked about on its own");
            helper.assertTrue(questions.answer(menu, coal).orElseThrow().network() == CraftingTableMenu.NETWORK_SUPPLIES, "the network has coal");
            helper.assertTrue(questions.answer(menu, STICKS).orElseThrow().network() == CraftingTableMenu.NETWORK_MISSING && asked.size() == 2,
                    "the answer for the sticks is still there");

            source.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
            clock[0] = IngredientQuestions.REFRESH_MILLIS - 1;
            helper.assertTrue(questions.answer(menu, STICKS).orElseThrow().network() == CraftingTableMenu.NETWORK_MISSING && asked.size() == 2,
                    "not old enough to ask again");
            clock[0] = IngredientQuestions.REFRESH_MILLIS;
            helper.assertTrue(questions.answer(menu, STICKS).orElseThrow().network() == CraftingTableMenu.NETWORK_MISSING
                    && asked.equals(List.of(1, 2, 3)), "an old answer is shown until the new one is in");
            helper.assertTrue(questions.answer(menu, STICKS).orElseThrow().network() == CraftingTableMenu.NETWORK_SUPPLIES, "now there are enough planks");

            questions.forget();
            helper.assertTrue(questions.answer(menu, STICKS).isEmpty() && asked.size() == 4, "after an order everything is asked anew");

            // a server that never answers is asked one question at a time, and again after a while
            List<Integer> unanswered = new ArrayList<>();
            IngredientQuestions lonely = new IngredientQuestions((containerId, question, ingredients) -> unanswered.add(question), () -> clock[0]);
            lonely.answer(menu, STICKS);
            lonely.answer(menu, coal);
            helper.assertTrue(lonely.answer(menu, STICKS).isEmpty() && unanswered.size() == 1, "only one question is open at a time");
            clock[0] += IngredientQuestions.REFRESH_MILLIS;
            lonely.answer(menu, coal);
            helper.assertTrue(unanswered.equals(List.of(1, 2)), "a question without an answer is given up after a while");
            helper.succeed();
        });
    }

    // the network is locked by a station, the player at the table may not request there
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void theTableOrdersOnlyForPlayersWhoMayRequest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 0);
        Container source = chest(helper, 0, new ItemStack(Items.OAK_PLANKS, 16));
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 1);
        table(helper, 1);
        BlockPos stationPos = new BlockPos(3, 1, 1);
        helper.setBlock(stationPos, LogisticsPipes.SECURITY_STATION.get());
        routedpipes.adapter.minecraft.SecurityStationBlockEntity station = helper.getBlockEntity(stationPos);
        TestPlayer owner = TestPlayer.in(helper);
        helper.getBlockState(stationPos).getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(stationPos),
                helper.getBlockState(stationPos), owner, ItemStack.EMPTY);
        ItemStack card = LogisticsPipes.SECURITY_CARD.get().cardFor(station.id());
        provider.security().card().setItem(0, card.copy());
        crafting.security().card().setItem(0, card.copy());

        helper.runAfterDelay(SETTLE, () -> {
            TestPlayer stranger = playerAtTable(helper, 1);
            CraftingTableMenu menu = (CraftingTableMenu) stranger.containerMenu;

            MenuRequests.askIngredients(stranger, menu.containerId, 0, STICKS, true);
            helper.assertTrue(source.countItem(Items.OAK_PLANKS) == 16, "a player who may not request orders nothing");

            station.station().settings().set(stranger.getUUID(), routedpipes.domain.security.Permission.ACTIVE_REQUESTING, true);
            MenuRequests.askIngredients(stranger, menu.containerId, 0, STICKS, true);
            helper.assertTrue(source.countItem(Items.OAK_PLANKS) == 14, "with the permission the planks are ordered");
            helper.succeed();
        });
    }
}
