package logisticspipes.adapter.neoforge.gametest;

import java.util.List;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class ChassisGameTest {

    private static final String EMPTY = "empty";

    private static ItemStack module(ModuleKind kind) {
        return new ItemStack(LogisticsPipes.MODULES.get(kind).get());
    }

    private static PipeBlockEntity chassis(GameTestHelper helper, int slots, int x, ItemStack... modules) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.CHASSIS_PIPES.get(slots).get());
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        for (int slot = 0; slot < modules.length; slot++) {
            pipe.modules().setItem(slot, modules[slot]);
        }
        return pipe;
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
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

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        Container container = helper.getBlockEntity(pos);
        int count = container.countItem(item);
        helper.assertTrue(count == expected, "expected " + expected + " " + item + " but found " + count);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void extractorEmptiesItsChestIntoTheNetwork(GameTestHelper helper) {
        chassis(helper, 1, 0, module(ModuleKind.EXTRACTOR));
        BlockPos source = chest(helper, 0, new ItemStack(Items.COBBLESTONE, 40));
        helper.setBlock(new BlockPos(1, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos target = chest(helper, 2);

        helper.succeedWhen(() -> {
            assertCount(helper, target, Items.COBBLESTONE, 40);
            assertCount(helper, source, Items.COBBLESTONE, 0);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void extractorKeepsItemsWithoutADestination(GameTestHelper helper) {
        chassis(helper, 1, 0, module(ModuleKind.EXTRACTOR));
        BlockPos source = chest(helper, 0, new ItemStack(Items.COBBLESTONE, 40));
        pipe(helper, PipeKind.REQUEST, 1);

        helper.runAfterDelay(100, () -> {
            assertCount(helper, source, Items.COBBLESTONE, 40);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void sortsByItemSinkModules(GameTestHelper helper) {
        chassis(helper, 1, 0, module(ModuleKind.EXTRACTOR));
        chest(helper, 0, new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.DIRT, 7));
        ItemStack diamonds = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(diamonds, List.of(new ItemStack(Items.DIAMOND)), false);
        chassis(helper, 2, 1, diamonds);
        BlockPos diamondChest = chest(helper, 1);
        ItemStack stone = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(stone, List.of(new ItemStack(Items.COBBLESTONE)), false);
        chassis(helper, 2, 3, stone);
        BlockPos stoneChest = chest(helper, 3);
        helper.setBlock(new BlockPos(2, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        pipe(helper, PipeKind.BASIC, 4);
        helper.setBlock(new BlockPos(4, 1, 0), Blocks.CHEST);
        BlockPos rest = new BlockPos(4, 1, 0);

        helper.succeedWhen(() -> {
            assertCount(helper, diamondChest, Items.DIAMOND, 5);
            assertCount(helper, stoneChest, Items.COBBLESTONE, 20);
            assertCount(helper, rest, Items.DIRT, 7);
            assertCount(helper, rest, Items.DIAMOND, 0);
            assertCount(helper, rest, Items.COBBLESTONE, 0);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void providerModuleServesRequests(GameTestHelper helper) {
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        chassis(helper, 2, 1, module(ModuleKind.PROVIDER));
        chest(helper, 1, new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(15, () -> helper.assertTrue(
                request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 12))).complete(), "request"));

        helper.succeedWhen(() -> assertCount(helper, target, Items.COBBLESTONE, 12));
    }

    @GameTest(template = EMPTY)
    public static void modulesOnlyCountInSlotsTheChassisHas(GameTestHelper helper) {
        PipeBlockEntity small = chassis(helper, 1, 0, ItemStack.EMPTY, module(ModuleKind.EXTRACTOR));
        PipeBlockEntity large = chassis(helper, 2, 4, ItemStack.EMPTY, module(ModuleKind.EXTRACTOR));

        helper.assertTrue(!small.extracts(), "mk1 has only one slot");
        helper.assertTrue(large.extracts(), "mk2 uses its second slot");
        helper.assertTrue(!large.provides(), "no provider module");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemSinkSettingsLiveOnTheModule(GameTestHelper helper) {
        ItemStack sink = module(ModuleKind.ITEM_SINK);
        helper.assertTrue(ModuleItem.sinkOf(sink).equals(new ItemSink(java.util.Set.of(), false)), "fresh module takes nothing");

        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND), ItemStack.EMPTY, new ItemStack(Items.COAL)), true);
        ItemStack copy = ItemStack.parse(helper.getLevel().registryAccess(), sink.save(helper.getLevel().registryAccess())).orElseThrow();

        ItemSink restored = ModuleItem.sinkOf(copy);
        helper.assertTrue(restored.defaultRoute(), "default route flag");
        helper.assertTrue(restored.filter().size() == 2, "two filter items");
        helper.assertTrue(ModuleItem.filterOf(copy).get(2).is(Items.COAL), "slot positions are kept");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void chassisMenuTakesOnlyModulesInUsableSlots(GameTestHelper helper) {
        PipeBlockEntity pipe = chassis(helper, 2, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, module(ModuleKind.EXTRACTOR));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
        player.getInventory().setItem(2, module(ModuleKind.PROVIDER));
        player.getInventory().setItem(3, module(ModuleKind.ITEM_SINK));
        SimpleContainerData slots = new SimpleContainerData(1);
        slots.set(0, 2);
        ChassisMenu menu = new ChassisMenu(LogisticsPipes.CHASSIS_MENU.get(), 1, player.getInventory(), pipe.modules(), slots, user -> true);
        int hotbar = menu.slots.size() - 9;

        menu.quickMoveStack(player, hotbar);
        menu.quickMoveStack(player, hotbar + 1);
        menu.quickMoveStack(player, hotbar + 2);
        menu.quickMoveStack(player, hotbar + 3);

        helper.assertTrue(pipe.extracts() && pipe.provides(), "two modules plugged in");
        helper.assertTrue(pipe.modules().getItem(2).isEmpty(), "third slot is not usable on a mk2");
        helper.assertTrue(player.getInventory().getItem(1).getCount() == 3, "diamonds are not modules");
        helper.assertTrue(!player.getInventory().getItem(3).isEmpty(), "no room for a third module");
        helper.assertTrue(!menu.getSlot(2).isActive() && menu.getSlot(1).isActive(), "slot activity follows the chassis size");

        menu.quickMoveStack(player, 0);
        helper.assertTrue(!pipe.extracts(), "module taken out again");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pluggedModulesSurviveSaveAndLoad(GameTestHelper helper) {
        ItemStack sink = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), false);
        PipeBlockEntity original = chassis(helper, 2, 0, sink, module(ModuleKind.EXTRACTOR));
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PipeBlockEntity loaded = chassis(helper, 2, 4);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.extracts(), "extractor");
        helper.assertTrue(loaded.sinkPriority(McItemType.batchOf(new ItemStack(Items.DIAMOND)).type()).isPresent(), "sink filter");
        helper.assertTrue(loaded.sinkPriority(McItemType.batchOf(new ItemStack(Items.DIRT)).type()).isEmpty(), "nothing else");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemSinkModuleIsSetUpInTheHand(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack held = module(ModuleKind.ITEM_SINK);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        held.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof PipeConfigMenu, "using the module should open its settings");
        PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;

        menu.setCarried(new ItemStack(Items.DIAMOND, 4));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clickMenuButton(player, PipeConfigMenu.BUTTON_DEFAULT_ROUTE);

        ItemSink sink = ModuleItem.sinkOf(player.getMainHandItem());
        helper.assertTrue(sink.filter().contains(McItemType.batchOf(new ItemStack(Items.DIAMOND)).type()), "filter is stored on the module");
        helper.assertTrue(sink.defaultRoute() && menu.defaultRoute(), "default route is stored on the module");
        helper.assertTrue(ModuleItem.sinkOf(module(ModuleKind.ITEM_SINK)).filter().isEmpty(), "other modules are not touched");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(!menu.stillValid(player), "settings close when the module is gone");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void otherModulesHaveNoSettings(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack held = module(ModuleKind.EXTRACTOR);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        held.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.containerMenu == player.inventoryMenu, "nothing should open");
        player.discard();
        helper.succeed();
    }
}
