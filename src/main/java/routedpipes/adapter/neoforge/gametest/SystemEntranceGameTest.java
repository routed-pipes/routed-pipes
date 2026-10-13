package routedpipes.adapter.neoforge.gametest;

import java.util.List;
import java.util.UUID;

import routedpipes.adapter.minecraft.FrequencyCardItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.FrequencyCardMenu;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// an entrance at x 0, beside it a basic pipe that takes everything, further off the destination pipes, each with a chest on top
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class SystemEntranceGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 15;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.setDefaultRoute(kind == PipeKind.BASIC);
        return pipe;
    }

    private static BlockPos chestOn(GameTestHelper helper, int x) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        return pos;
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = helper.getBlockEntity(pos);
        return container.countItem(item);
    }

    // a stack of cards that got their id the way a player gives it to them
    private static ItemStack cards(GameTestHelper helper, int count) {
        TestPlayer player = TestPlayer.in(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LogisticsPipes.FREQUENCY_CARD.get(), count));
        return LogisticsPipes.FREQUENCY_CARD.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
    }

    private static void insert(PipeBlockEntity pipe, ItemStack cards) {
        pipe.frequencySlot().card().setItem(0, cards.copyWithCount(1));
    }

    @GameTest(template = EMPTY)
    public static void usingAStackOfBlankCardsGivesThemAllTheSameId(GameTestHelper helper) {
        ItemStack blank = new ItemStack(LogisticsPipes.FREQUENCY_CARD.get(), 2);
        helper.assertTrue(FrequencyCardItem.frequencyOf(blank).isEmpty(), "a new card is blank");

        ItemStack pair = cards(helper, 2);
        UUID id = FrequencyCardItem.frequencyOf(pair).orElseThrow();
        helper.assertTrue(pair.getCount() == 2, "both cards are still one stack, so they have the same id");

        TestPlayer player = TestPlayer.in(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, pair);
        pair.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(FrequencyCardItem.frequencyOf(pair).orElseThrow().equals(id), "using a card again changes nothing");
        helper.assertTrue(!FrequencyCardItem.frequencyOf(cards(helper, 1)).orElseThrow().equals(id), "other cards get another id");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void entranceSendsItemsToTheDestinationWithTheSameCard(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity entrance = pipe(helper, PipeKind.SYSTEM_ENTRANCE, 0);
        pipe(helper, PipeKind.BASIC, 1);
        BlockPos sink = chestOn(helper, 1);
        PipeBlockEntity other = pipe(helper, PipeKind.SYSTEM_DESTINATION, 2);
        BlockPos wrong = chestOn(helper, 2);
        PipeBlockEntity destination = pipe(helper, PipeKind.SYSTEM_DESTINATION, 3);
        BlockPos target = chestOn(helper, 3);
        ItemStack pair = cards(helper, 2);
        insert(entrance, pair);
        insert(destination, pair);
        insert(other, cards(helper, 1));

        helper.runAfterDelay(SETTLE, () -> entrance.accept(new ItemStack(Items.COBBLESTONE, 16), Direction.UP));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, target, Items.COBBLESTONE) == 16, "the items end up at the paired destination");
            helper.assertTrue(count(helper, sink, Items.COBBLESTONE) == 0 && count(helper, wrong, Items.COBBLESTONE) == 0,
                    "and nowhere else");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void entranceWithoutAPairedDestinationSortsLikeAnyPipe(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity entrance = pipe(helper, PipeKind.SYSTEM_ENTRANCE, 0);
        pipe(helper, PipeKind.BASIC, 1);
        BlockPos sink = chestOn(helper, 1);
        PipeBlockEntity destination = pipe(helper, PipeKind.SYSTEM_DESTINATION, 2);
        BlockPos target = chestOn(helper, 2);
        insert(entrance, cards(helper, 1));
        insert(destination, cards(helper, 1));

        helper.runAfterDelay(SETTLE, () -> entrance.accept(new ItemStack(Items.COBBLESTONE, 16), Direction.UP));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, sink, Items.COBBLESTONE) == 16, "the items go to the sink");
            helper.assertTrue(count(helper, target, Items.COBBLESTONE) == 0, "the destination with another card gets nothing");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void ofTwoPairedDestinationsTheNearestGetsTheItems(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity entrance = pipe(helper, PipeKind.SYSTEM_ENTRANCE, 0);
        helper.setBlock(new BlockPos(1, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity near = pipe(helper, PipeKind.SYSTEM_DESTINATION, 2);
        BlockPos nearChest = chestOn(helper, 2);
        PipeBlockEntity far = pipe(helper, PipeKind.SYSTEM_DESTINATION, 3);
        BlockPos farChest = chestOn(helper, 3);
        ItemStack cards = cards(helper, 3);
        insert(entrance, cards);
        insert(near, cards);
        insert(far, cards);

        helper.runAfterDelay(SETTLE, () -> entrance.accept(new ItemStack(Items.COBBLESTONE, 16), Direction.UP));
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(count(helper, nearChest, Items.COBBLESTONE) == 16 && count(helper, farChest, Items.COBBLESTONE) == 0,
                    "everything goes to the nearer one");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void theSlotTakesOneFrequencyCardWhichDropsWithThePipeAndSurvivesSaveAndLoad(GameTestHelper helper) {
        PipeBlockEntity entrance = pipe(helper, PipeKind.SYSTEM_ENTRANCE, 0);
        TestPlayer player = TestPlayer.in(helper);
        player.openMenu(entrance.menuProvider());
        FrequencyCardMenu menu = (FrequencyCardMenu) player.containerMenu;
        ItemStack pair = cards(helper, 2);
        UUID id = FrequencyCardItem.frequencyOf(pair).orElseThrow();

        menu.setCarried(new ItemStack(Items.PAPER));
        menu.clicked(FrequencyCardMenu.CARD_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(entrance.frequencySlot().card().isEmpty(), "nothing but a frequency card goes in");
        menu.setCarried(pair);
        menu.clicked(FrequencyCardMenu.CARD_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 1 && entrance.frequencySlot().card().getItem(0).getCount() == 1,
                "one card goes in");
        helper.assertTrue(entrance.frequency().filter(id::equals).isPresent(), "the pipe knows the id on its card");

        PipeBlockEntity loaded = pipe(helper, PipeKind.SYSTEM_DESTINATION, 2);
        loaded.loadWithComponents(entrance.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.frequency().filter(id::equals).isPresent(), "the card stays in the pipe");
        loaded.frequencySlot().card().clearContent();
        helper.assertTrue(loaded.frequency().isEmpty(), "without a card there is no id");

        helper.setBlock(new BlockPos(0, 1, 1), Blocks.AIR);
        List<ItemEntity> dropped = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM, new BlockPos(0, 1, 1), 2);
        helper.assertTrue(dropped.stream().anyMatch(drop -> FrequencyCardItem.frequencyOf(drop.getItem()).filter(id::equals).isPresent()),
                "breaking the pipe drops the card");
        helper.succeed();
    }
}
