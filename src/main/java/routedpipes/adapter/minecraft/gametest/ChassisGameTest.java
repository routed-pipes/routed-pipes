package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.List;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.menu.ChassisMenu;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.domain.logistics.ItemSink;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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

public class ChassisGameTest {

    private static final String EMPTY = "empty";

    private static ItemStack module(ModuleKind kind) {
        return new ItemStack(Content.MODULES.get(kind).get());
    }

    private static PipeBlockEntity chassis(GameTestHelper helper, int slots, int x, ItemStack... modules) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.CHASSIS_PIPES.get(slots).get());
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        for (int slot = 0; slot < modules.length; slot++) {
            pipe.modules().setItem(slot, modules[slot]);
        }
        return pipe;
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

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        Container container = helper.getBlockEntity(pos);
        int count = container.countItem(item);
        helper.assertTrue(count == expected, "expected " + expected + " " + item + " but found " + count);
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void extractorEmptiesItsChestIntoTheNetwork(GameTestHelper helper) {
        GameTestPower.supply(helper);
        chassis(helper, 1, 0, module(ModuleKind.EXTRACTOR));
        BlockPos source = chest(helper, 0, new ItemStack(Items.COBBLESTONE, 40));
        helper.setBlock(new BlockPos(1, 1, 1), Content.TRANSPORT_PIPE.get());
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos target = chest(helper, 2);

        helper.succeedWhen(() -> {
            assertCount(helper, target, Items.COBBLESTONE, 40);
            assertCount(helper, source, Items.COBBLESTONE, 0);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void extractorKeepsItemsWithoutADestination(GameTestHelper helper) {
        GameTestPower.supply(helper);
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
        GameTestPower.supply(helper);
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
        helper.setBlock(new BlockPos(2, 1, 1), Content.TRANSPORT_PIPE.get());
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
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        chassis(helper, 2, 1, module(ModuleKind.PROVIDER));
        chest(helper, 1, new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(15, () -> helper.assertTrue(
                request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 12))).started(), "request"));

        helper.succeedWhen(() -> assertCount(helper, target, Items.COBBLESTONE, 12));
    }

    @GameTest(template = EMPTY)
    public static void modulesOnlyCountInSlotsTheChassisHas(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity small = chassis(helper, 1, 0, ItemStack.EMPTY, module(ModuleKind.EXTRACTOR));
        PipeBlockEntity large = chassis(helper, 2, 4, ItemStack.EMPTY, module(ModuleKind.EXTRACTOR));

        helper.assertTrue(small.extractions().isEmpty(), "mk1 has only one slot");
        helper.assertTrue(!large.extractions().isEmpty(), "mk2 uses its second slot");
        helper.assertTrue(!large.provides(), "no provider module");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemSinkSettingsLiveOnTheModule(GameTestHelper helper) {
        GameTestPower.supply(helper);
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
        GameTestPower.supply(helper);
        PipeBlockEntity pipe = chassis(helper, 2, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, module(ModuleKind.EXTRACTOR));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
        player.getInventory().setItem(2, module(ModuleKind.PROVIDER));
        player.getInventory().setItem(3, module(ModuleKind.ITEM_SINK));
        SimpleContainerData slots = new SimpleContainerData(ChassisMenu.DATA_COUNT);
        slots.set(0, 2);
        ChassisMenu menu = new ChassisMenu(Content.CHASSIS_MENU.get(), 1, player.getInventory(), pipe.modules(), pipe.moduleUpgrades(),
                slots, user -> true, () -> { }, (user, slot) -> { });
        int hotbar = menu.slots.size() - 9;

        menu.quickMoveStack(player, hotbar);
        menu.quickMoveStack(player, hotbar + 1);
        menu.quickMoveStack(player, hotbar + 2);
        menu.quickMoveStack(player, hotbar + 3);

        helper.assertTrue(!pipe.extractions().isEmpty() && pipe.provides(), "two modules plugged in");
        helper.assertTrue(pipe.modules().getItem(2).isEmpty(), "third slot is not usable on a mk2");
        helper.assertTrue(player.getInventory().getItem(1).getCount() == 3, "diamonds are not modules");
        helper.assertTrue(!player.getInventory().getItem(3).isEmpty(), "no room for a third module");
        helper.assertTrue(!menu.getSlot(2).isActive() && menu.getSlot(1).isActive(), "slot activity follows the chassis size");

        menu.quickMoveStack(player, 0);
        helper.assertTrue(pipe.extractions().isEmpty(), "module taken out again");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pluggedModulesSurviveSaveAndLoad(GameTestHelper helper) {
        GameTestPower.supply(helper);
        ItemStack sink = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), false);
        PipeBlockEntity original = chassis(helper, 2, 0, sink, module(ModuleKind.EXTRACTOR));
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PipeBlockEntity loaded = chassis(helper, 2, 4);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(!loaded.extractions().isEmpty(), "extractor");
        helper.assertTrue(loaded.sinkRules().size() == 1 && loaded.sinkRules().get(0).wants()
                .equals(java.util.Set.of(McItemType.batchOf(new ItemStack(Items.DIAMOND)).type())), "sink filter");
        helper.assertTrue(!loaded.sinkRules().get(0).takesUnlisted(), "nothing else");

        // a command or a structure may carry only the modules
        CompoundTag onlyModules = new CompoundTag();
        onlyModules.put("Modules", saved.getCompound("Modules"));
        PipeBlockEntity partial = chassis(helper, 2, 6);
        partial.loadWithComponents(onlyModules, helper.getLevel().registryAccess());
        helper.assertTrue(!partial.extractions().isEmpty(), "modules load without the other settings");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itemSinkModuleIsSetUpInTheHand(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = TestPlayer.in(helper);
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
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void otherModulesHaveNoSettings(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = TestPlayer.in(helper);
        ItemStack held = module(ModuleKind.EXTRACTOR);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        held.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(player.containerMenu == player.inventoryMenu, "nothing should open");
        helper.succeed();
    }

    // the diamond chest is full, so diamonds overflow into the terminus and not into the default chest
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void terminusCatchesOverflowBeforeTheDefaultRoute(GameTestHelper helper) {
        GameTestPower.supply(helper);
        chassis(helper, 1, 0, module(ModuleKind.EXTRACTOR));
        chest(helper, 0, new ItemStack(Items.DIAMOND, 5));
        ItemStack sink = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), false);
        chassis(helper, 1, 1, sink);
        BlockPos full = chest(helper, 1);
        Container fullChest = helper.getBlockEntity(full);
        for (int slot = 0; slot < fullChest.getContainerSize(); slot++) {
            fullChest.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        pipe(helper, PipeKind.BASIC, 2);
        helper.setBlock(new BlockPos(2, 1, 0), Blocks.CHEST);
        BlockPos fallback = new BlockPos(2, 1, 0);
        ItemStack terminus = module(ModuleKind.TERMINUS);
        ModuleItem.configureSink(terminus, List.of(new ItemStack(Items.DIAMOND)), false);
        chassis(helper, 1, 3, terminus);
        BlockPos overflow = chest(helper, 3);

        helper.succeedWhen(() -> {
            assertCount(helper, overflow, Items.DIAMOND, 5);
            assertCount(helper, fallback, Items.DIAMOND, 0);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void polymorphicSinkCollectsMoreOfWhatItHolds(GameTestHelper helper) {
        GameTestPower.supply(helper);
        chassis(helper, 1, 0, module(ModuleKind.EXTRACTOR));
        BlockPos source = chest(helper, 0, new ItemStack(Items.COAL, 20), new ItemStack(Items.DIRT, 9));
        chassis(helper, 1, 1, module(ModuleKind.POLYMORPHIC_ITEM_SINK));
        BlockPos target = chest(helper, 1, new ItemStack(Items.COAL, 1));

        helper.runAfterDelay(200, () -> {
            assertCount(helper, target, Items.COAL, 21);
            assertCount(helper, target, Items.DIRT, 0);
            assertCount(helper, source, Items.DIRT, 9);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void passiveSupplierFillsUpToItsTargetAndNoFurther(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity source = pipe(helper, PipeKind.BASIC, 0);
        ItemStack supplier = module(ModuleKind.PASSIVE_SUPPLIER);
        ModuleItem.configureSink(supplier, List.of(new ItemStack(Items.COAL, 8)), false);
        chassis(helper, 1, 1, supplier);
        BlockPos target = chest(helper, 1);

        helper.runAfterDelay(15, () -> source.accept(new ItemStack(Items.COAL, 8), net.minecraft.core.Direction.DOWN));
        helper.runAfterDelay(100, () -> {
            assertCount(helper, target, Items.COAL, 8);
            source.accept(new ItemStack(Items.COAL, 8), net.minecraft.core.Direction.DOWN);
        });
        helper.runAfterDelay(450, () -> {
            assertCount(helper, target, Items.COAL, 8);
            helper.assertItemEntityPresent(Items.COAL, new BlockPos(0, 1, 1), 2);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void quickSortOnlyFeedsPipesThatAskedByName(GameTestHelper helper) {
        GameTestPower.supply(helper);
        chassis(helper, 1, 0, module(ModuleKind.QUICK_SORT));
        BlockPos source = chest(helper, 0, new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.DIAMOND, 30), new ItemStack(Items.DIRT, 9));
        ItemStack sink = module(ModuleKind.ITEM_SINK);
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), false);
        chassis(helper, 1, 1, sink);
        BlockPos sorted = chest(helper, 1);
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos fallback = chest(helper, 2);

        helper.runAfterDelay(200, () -> {
            assertCount(helper, sorted, Items.DIAMOND, 94);
            assertCount(helper, source, Items.DIRT, 9);
            helper.assertContainerEmpty(fallback);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void modulesWithListsHaveSettingsAndTheOthersDoNot(GameTestHelper helper) {
        GameTestPower.supply(helper);
        for (ModuleKind kind : ModuleKind.values()) {
            TestPlayer player = TestPlayer.in(helper);
            ItemStack held = module(kind);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            held.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            boolean opened = player.containerMenu instanceof PipeConfigMenu;
            boolean expected = ModuleItem.settingsLayoutFor(kind).isPresent();
            helper.assertTrue(opened == expected, kind + " settings screen: expected " + expected);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void extractorStandsStillWithoutPower(GameTestHelper helper) {
        chassis(helper, 1, 4, module(ModuleKind.EXTRACTOR));
        BlockPos source = new BlockPos(4, 2, 1);
        helper.setBlock(source, Blocks.CHEST);
        Container contents = helper.getBlockEntity(source);
        contents.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        pipe(helper, PipeKind.BASIC, 5);
        BlockPos target = chest(helper, 5);

        helper.runAfterDelay(100, () -> {
            assertCount(helper, source, Items.COBBLESTONE, 40);
            helper.assertContainerEmpty(target);
            helper.succeed();
        });
    }

    // an extractor and a basic pipe on the same chest used to pass items round in a circle and burn power doing it
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void extractorDoesNotFeedAPipeOnItsOwnChest(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR);
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(4, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        chassis(helper, 1, 4, module(ModuleKind.EXTRACTOR));
        BlockPos shared = new BlockPos(4, 2, 1);
        helper.setBlock(shared, Blocks.CHEST);
        Container contents = helper.getBlockEntity(shared);
        contents.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        helper.setBlock(new BlockPos(5, 1, 1), Content.TRANSPORT_PIPE.get());
        helper.setBlock(new BlockPos(5, 2, 1), Content.pipe(PipeKind.BASIC));

        helper.runAfterDelay(200, () -> {
            assertCount(helper, shared, Items.COBBLESTONE, 40);
            helper.assertTrue(junction.stored() == PowerJunctionBlockEntity.CAPACITY, "nothing should have been moved or paid for");
            PipeBlockEntity basic = helper.getBlockEntity(new BlockPos(5, 2, 1));
            helper.assertTrue(basic.inventories().equals(java.util.Map.of(routedpipes.domain.routing.Side.WEST,
                    ((PipeBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 1))).inventories().values().iterator().next())),
                    "both pipes should see the same inventory");
            helper.succeed();
        });
    }

    // twenty coal arrive in one stack, the passive supplier wants eight: it takes exactly eight
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void passiveSupplierSplitsAStackThatIsTooLarge(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity source = pipe(helper, PipeKind.BASIC, 0);
        source.setDefaultRoute(false);
        ItemStack supplier = module(ModuleKind.PASSIVE_SUPPLIER);
        ModuleItem.configureSink(supplier, List.of(new ItemStack(Items.COAL, 8)), false);
        chassis(helper, 1, 1, supplier);
        BlockPos target = chest(helper, 1);
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos rest = chest(helper, 2);

        helper.runAfterDelay(15, () -> source.accept(new ItemStack(Items.COAL, 20), net.minecraft.core.Direction.DOWN));

        helper.runAfterDelay(120, () -> {
            assertCount(helper, target, Items.COAL, 8);
            assertCount(helper, rest, Items.COAL, 12);
            helper.succeed();
        });
    }

    // a chassis between two chests works on one of them, and on the other one after it is turned
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void chassisWorksOnTheInventoryItPointsAt(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity chassis = chassis(helper, 1, 1, module(ModuleKind.EXTRACTOR));
        BlockPos above = chest(helper, 1, new ItemStack(Items.COAL, 16));
        BlockPos below = new BlockPos(1, 0, 1);
        helper.setBlock(below, Blocks.CHEST);
        Container lower = helper.getBlockEntity(below);
        lower.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos target = chest(helper, 2);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(chassis.pointed().orElseThrow() == net.minecraft.core.Direction.DOWN, "points down first");
            assertCount(helper, target, Items.IRON_INGOT, 16);
            assertCount(helper, target, Items.COAL, 0);
            chassis.pointAtNext();
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(chassis.pointed().orElseThrow() == net.minecraft.core.Direction.UP, "points up after the turn");
            assertCount(helper, above, Items.COAL, 0);
            assertCount(helper, target, Items.COAL, 16);
            helper.succeed();
        });
    }

    // sends one stack into a line of three chassis and says which chest it has to end up in
    private static void sinkTest(GameTestHelper helper, ItemStack sent, ItemStack module, boolean taken) {
        GameTestPower.supply(helper);
        PipeBlockEntity source = pipe(helper, PipeKind.BASIC, 0);
        source.setDefaultRoute(false);
        chassis(helper, 1, 1, module);
        BlockPos special = chest(helper, 1);
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos fallback = chest(helper, 2);
        helper.runAfterDelay(15, () -> source.accept(sent.copy(), net.minecraft.core.Direction.DOWN));
        helper.runAfterDelay(100, () -> {
            assertCount(helper, taken ? special : fallback, sent.getItem(), sent.getCount());
            assertCount(helper, taken ? fallback : special, sent.getItem(), 0);
            helper.succeed();
        });
    }

    private static ItemStack named(ModuleKind kind, String... names) {
        ItemStack module = module(kind);
        ModuleItem.setNames(module, List.of(names));
        return module;
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void modSinkTakesItemsOfItsMod(GameTestHelper helper) {
        sinkTest(helper, new ItemStack(Content.TRANSPORT_PIPE.get(), 4), named(ModuleKind.MOD_ITEM_SINK, Content.MODID), true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void modSinkLeavesOtherModsAlone(GameTestHelper helper) {
        sinkTest(helper, new ItemStack(Items.COAL, 4), named(ModuleKind.MOD_ITEM_SINK, Content.MODID), false);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void tagSinkTakesItemsWithItsTag(GameTestHelper helper) {
        sinkTest(helper, new ItemStack(Items.BIRCH_PLANKS, 4), named(ModuleKind.TAG_ITEM_SINK, "minecraft:planks"), true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void tagSinkLeavesOtherItemsAlone(GameTestHelper helper) {
        sinkTest(helper, new ItemStack(Items.COBBLESTONE, 4), named(ModuleKind.TAG_ITEM_SINK, "minecraft:planks"), false);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void creativeTabSinkTakesItemsOfItsTab(GameTestHelper helper) {
        sinkTest(helper, new ItemStack(Items.DIAMOND_SWORD), named(ModuleKind.CREATIVE_TAB_ITEM_SINK, "minecraft:combat"), true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void creativeTabSinkLeavesOtherTabsAlone(GameTestHelper helper) {
        sinkTest(helper, new ItemStack(Items.COBBLESTONE, 4), named(ModuleKind.CREATIVE_TAB_ITEM_SINK, "minecraft:combat"), false);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 1);
        return stack;
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void enchantmentSinkTakesEnchantedItems(GameTestHelper helper) {
        sinkTest(helper, enchanted(helper, Items.IRON_PICKAXE), module(ModuleKind.ENCHANTMENT_SINK), true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void enchantmentSinkLeavesPlainItemsAlone(GameTestHelper helper) {
        sinkTest(helper, new ItemStack(Items.IRON_PICKAXE), module(ModuleKind.ENCHANTMENT_SINK), false);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void enchantmentSinkMk2TakesOnlyListedItems(GameTestHelper helper) {
        ItemStack module = module(ModuleKind.ENCHANTMENT_SINK_MK2);
        ModuleItem.configureSink(module, List.of(new ItemStack(Items.IRON_PICKAXE)), false);
        sinkTest(helper, enchanted(helper, Items.IRON_PICKAXE), module, true);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void enchantmentSinkMk2LeavesOtherEnchantedItemsAlone(GameTestHelper helper) {
        ItemStack module = module(ModuleKind.ENCHANTMENT_SINK_MK2);
        ModuleItem.configureSink(module, List.of(new ItemStack(Items.IRON_PICKAXE)), false);
        sinkTest(helper, enchanted(helper, Items.IRON_SWORD), module, false);
    }

    // only the listed coal leaves, the iron stays
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void advancedExtractorTakesOnlyListedItems(GameTestHelper helper) {
        GameTestPower.supply(helper);
        ItemStack extractor = module(ModuleKind.ADVANCED_EXTRACTOR);
        ModuleItem.configureFilter(extractor, List.of(new ItemStack(Items.COAL)), false);
        chassis(helper, 1, 0, extractor);
        BlockPos source = chest(helper, 0, new ItemStack(Items.COAL, 16), new ItemStack(Items.IRON_INGOT, 16));
        pipe(helper, PipeKind.BASIC, 1);
        BlockPos target = chest(helper, 1);

        helper.runAfterDelay(100, () -> {
            assertCount(helper, target, Items.COAL, 16);
            assertCount(helper, source, Items.IRON_INGOT, 16);
            helper.succeed();
        });
    }

    // names are added and removed the way the screen asks for it
    @GameTest(template = EMPTY)
    public static void namesMenuEditsTheListOnTheModule(GameTestHelper helper) {
        Player player = TestPlayer.in(helper);
        ItemStack module = module(ModuleKind.TAG_ITEM_SINK);
        player.getInventory().setItem(0, module);
        routedpipes.adapter.minecraft.menu.NamesMenu menu = new routedpipes.adapter.minecraft.menu.NamesMenu(
                Content.NAMES_MENU.get(), 1, player.getInventory(), module, user -> true, () -> { });
        player.containerMenu = menu;

        routedpipes.adapter.minecraft.menu.MenuRequests.applyText(player, 1, routedpipes.adapter.minecraft.menu.NamesMenu.FIELD_ADD, "minecraft:planks");
        routedpipes.adapter.minecraft.menu.MenuRequests.applyText(player, 1, routedpipes.adapter.minecraft.menu.NamesMenu.FIELD_ADD, "minecraft:planks");
        routedpipes.adapter.minecraft.menu.MenuRequests.applyText(player, 1, routedpipes.adapter.minecraft.menu.NamesMenu.FIELD_ADD, "minecraft:logs");
        helper.assertTrue(ModuleItem.namesOf(module).equals(List.of("minecraft:planks", "minecraft:logs")), "two names, no doubles");
        routedpipes.adapter.minecraft.menu.MenuRequests.applyText(player, 1, routedpipes.adapter.minecraft.menu.NamesMenu.FIELD_REMOVE, "minecraft:planks");
        helper.assertTrue(ModuleItem.namesOf(module).equals(List.of("minecraft:logs")), "one removed");
        helper.assertTrue(routedpipes.adapter.minecraft.ItemTraits.tags(new ItemStack(Items.OAK_PLANKS)).contains("minecraft:planks"),
                "planks offer their tag");
        helper.succeed();
    }

    // coal is on the list of the firewall, so only the iron gets to the chest behind it
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void firewallStopsListedItems(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity source = pipe(helper, PipeKind.BASIC, 0);
        source.setDefaultRoute(false);
        PipeBlockEntity firewall = pipe(helper, PipeKind.FIREWALL, 1);
        firewall.firewallSettings().list().setItem(0, new ItemStack(Items.COAL));
        firewall.firewallSettings().data().set(routedpipes.adapter.minecraft.FirewallSettings.POWER, 0);
        pipe(helper, PipeKind.BASIC, 2);
        BlockPos target = chest(helper, 2);

        helper.runAfterDelay(15, () -> {
            source.accept(new ItemStack(Items.COAL, 4), net.minecraft.core.Direction.DOWN);
            source.accept(new ItemStack(Items.IRON_INGOT, 4), net.minecraft.core.Direction.DOWN);
        });
        helper.runAfterDelay(120, () -> {
            assertCount(helper, target, Items.IRON_INGOT, 4);
            assertCount(helper, target, Items.COAL, 0);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void firewallSettingsAreSwitchedInItsScreenAndSurviveSaveAndLoad(GameTestHelper helper) {
        PipeBlockEntity firewall = pipe(helper, PipeKind.FIREWALL, 0);
        TestPlayer player = TestPlayer.in(helper);
        player.openMenu(firewall.menuProvider());

        helper.assertTrue(firewall.firewall().orElseThrow().blockPower() && !firewall.firewall().orElseThrow().blockSorting(),
                "a new firewall stops power and nothing else");
        player.containerMenu.clickMenuButton(player, routedpipes.adapter.minecraft.FirewallSettings.SORTING);
        player.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 3));
        player.containerMenu.clicked(5, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(player.containerMenu.getCarried().getCount() == 3, "listing an item does not take it");

        PipeBlockEntity loaded = pipe(helper, PipeKind.FIREWALL, 2);
        loaded.loadWithComponents(firewall.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.firewall().orElseThrow().blockSorting(), "sorting stays stopped");
        helper.assertTrue(loaded.firewall().orElseThrow().rejects(routedpipes.adapter.minecraft.McItemType.batchOf(new ItemStack(Items.DIAMOND)).type()),
                "diamonds stay on the list");
        helper.succeed();
    }
}
