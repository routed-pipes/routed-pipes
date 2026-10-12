package logisticspipes.adapter.neoforge.gametest;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.minecraft.RemoteOrdererItem;
import logisticspipes.adapter.minecraft.menu.MenuRequests;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.ProviderMode;
import logisticspipes.domain.logistics.SupplyMode;
import logisticspipes.application.LogisticsNetwork;
import logisticspipes.application.RequestOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class RequestGameTest {

    private static final String EMPTY = "empty";
    // long enough for every pipe to find its neighbours
    private static final int SETTLE = 5;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x, int y) {
        BlockPos pos = new BlockPos(x, y, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        // in these tests a basic pipe takes everything unless the test says otherwise
        pipe.setDefaultRoute(kind == PipeKind.BASIC);
        return pipe;
    }

    private static void transport(GameTestHelper helper, int x, int y) {
        helper.setBlock(new BlockPos(x, y, 1), LogisticsPipes.TRANSPORT_PIPE.get());
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

    private static ItemBatch batch(Item item, int count) {
        return McItemType.batchOf(new ItemStack(item, count));
    }

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        Container container = helper.getBlockEntity(pos);
        int count = container.countItem(item);
        helper.assertTrue(count == expected, "expected " + expected + " " + item + " but found " + count);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void requestDeliversFromProvider(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        transport(helper, 1, 1);
        transport(helper, 2, 1);
        pipe(helper, PipeKind.PROVIDER, 3, 1);
        BlockPos source = chest(helper, 3, 2, new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome plan = request.request(batch(Items.COBBLESTONE, 10));
            helper.assertTrue(plan.started(), "request should be possible");
        });

        helper.succeedWhen(() -> {
            assertCount(helper, target, Items.COBBLESTONE, 10);
            assertCount(helper, source, Items.COBBLESTONE, 54);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void requestSplitsLargeOrdersIntoStacks(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(SETTLE, () -> request.request(batch(Items.COBBLESTONE, 150)));

        helper.succeedWhen(() -> assertCount(helper, target, Items.COBBLESTONE, 150));
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void failedRequestMovesNothing(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        BlockPos source = chest(helper, 1, 2, new ItemStack(Items.COBBLESTONE, 5));

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome plan = request.request(batch(Items.COBBLESTONE, 10));
            helper.assertTrue(!plan.started(), "request should fail");
        });

        helper.runAfterDelay(60, () -> {
            helper.assertContainerEmpty(target);
            assertCount(helper, source, Items.COBBLESTONE, 5);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void requestedItemPopsOutWithoutChest(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(SETTLE, () -> request.request(batch(Items.COBBLESTONE, 10)));

        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.COBBLESTONE, new BlockPos(0, 1, 1), 2));
    }

    // one provider feeding two request pipes behind a fork of plain pipes
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void forkInTransportPipesLeadsToTheRightRouter(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIAMOND, 8));
        transport(helper, 2, 1);
        transport(helper, 3, 1);
        PipeBlockEntity east = pipe(helper, PipeKind.REQUEST, 4, 1);
        BlockPos eastChest = chest(helper, 4, 2);
        transport(helper, 2, 2);
        PipeBlockEntity top = pipe(helper, PipeKind.REQUEST, 2, 3);
        BlockPos topChest = chest(helper, 3, 3);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(east.request(batch(Items.COBBLESTONE, 10)).started(), "east request");
            helper.assertTrue(top.request(batch(Items.DIAMOND, 3)).started(), "top request");
        });

        helper.runAfterDelay(150, () -> {
            assertCount(helper, eastChest, Items.COBBLESTONE, 10);
            assertCount(helper, eastChest, Items.DIAMOND, 0);
            assertCount(helper, topChest, Items.DIAMOND, 3);
            assertCount(helper, topChest, Items.COBBLESTONE, 0);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void supplierKeepsChestStocked(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        supplier.config().setItem(0, new ItemStack(Items.COAL, 16));
        transport(helper, 1, 1);
        pipe(helper, PipeKind.PROVIDER, 2, 1);
        chest(helper, 2, 2, new ItemStack(Items.COAL, 64));

        // several upkeep rounds later it must still be exactly the target amount
        helper.runAfterDelay(200, () -> {
            assertCount(helper, target, Items.COAL, 16);
            helper.succeed();
        });
    }

    // furnace in the middle: satellite on top feeds ore, crafting pipe below collects ingots
    @GameTest(template = EMPTY, timeoutTicks = 800)
    public static void craftsThroughFurnaceWithSatellite(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        transport(helper, 1, 1);
        pipe(helper, PipeKind.PROVIDER, 2, 1);
        chest(helper, 2, 2, new ItemStack(Items.RAW_IRON, 8));
        transport(helper, 3, 1);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 4, 1);
        BlockPos furnace = new BlockPos(4, 2, 1);
        helper.setBlock(furnace, Blocks.FURNACE);
        Container fuel = helper.getBlockEntity(furnace);
        fuel.setItem(1, new ItemStack(Items.COAL, 1));
        PipeBlockEntity satellite = pipe(helper, PipeKind.SATELLITE, 4, 3);
        transport(helper, 5, 1);
        transport(helper, 5, 2);
        transport(helper, 5, 3);

        crafting.config().setItem(PipeBlockEntity.SATELLITE_ROW, new ItemStack(Items.RAW_IRON, 1));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));
        crafting.setSatelliteId(3);
        satellite.setSatelliteId(3);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(request.available().containsKey(batch(Items.IRON_INGOT, 1).type()), "ingots should be craftable");
            RequestOutcome plan = request.request(batch(Items.IRON_INGOT, 2));
            helper.assertTrue(plan.started(), "crafting request should be possible, missing " + plan.missing());
        });

        helper.succeedWhen(() -> assertCount(helper, target, Items.IRON_INGOT, 2));
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void requestMenuOrdersTheSelectedItem(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.DIAMOND, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.runAfterDelay(SETTLE, () -> {
            RequestMenu menu = new RequestMenu(LogisticsPipes.REQUEST_MENU.get(), 1, request, user -> true);
            ItemStack first = menu.getSlot(0).getItem();
            helper.assertTrue(first.is(Items.COBBLESTONE) && first.getCount() == 1, "cobblestone should be listed first");
            helper.assertTrue(RequestMenu.listedCount(first) == 128, "listed amount should be 128");
            helper.assertTrue(menu.getSlot(1).getItem().is(Items.DIAMOND), "diamond should be listed second");

            menu.clicked(1, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.selectedSlot() == 1 && menu.getCarried().isEmpty(), "clicking should only select");
            menu.clickMenuButton(player, 3);
            menu.clickMenuButton(player, 3);
            helper.assertTrue(menu.amount() == 3, "amount should be 3");
            menu.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
        });

        helper.succeedWhen(() -> assertCount(helper, target, Items.DIAMOND, 3));
    }

    @GameTest(template = EMPTY)
    public static void settingsMenuMarksItemsWithoutTakingThem(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 0, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PipeConfigMenu menu = new PipeConfigMenu(LogisticsPipes.CONFIG_MENUS.get(PipeKind.SUPPLIER).get(), 1, player.getInventory(),
                PipeKind.SUPPLIER, supplier.config(), new SimpleContainerData(PipeConfigMenu.DATA_COUNT), user -> true, () -> { });

        menu.setCarried(new ItemStack(Items.COAL, 16));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 16, "held stack must stay in hand");
        helper.assertTrue(supplier.config().getItem(0).is(Items.COAL) && supplier.config().getItem(0).getCount() == 16,
                "slot should remember 16 coal");

        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(supplier.config().getItem(0).getCount() == 17, "right click with the same item adds one");

        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(supplier.config().getItem(0).getCount() == 16, "right click with empty hand removes one");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(supplier.config().getItem(0).isEmpty(), "left click with empty hand clears");
        helper.assertTrue(supplier.supplyTargets().isEmpty(), "no targets left");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pipeStateSurvivesSaveAndLoad(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity original = pipe(helper, PipeKind.CRAFTING, 0, 1);
        original.config().setItem(0, new ItemStack(Items.RAW_IRON, 2));
        original.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));
        original.setSatelliteId(7);
        original.accept(new ItemStack(Items.DIAMOND, 5), Direction.UP);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PipeBlockEntity loaded = pipe(helper, PipeKind.CRAFTING, 4, 1);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.satelliteId() == 7, "satellite id");
        helper.assertTrue(loaded.recipe().equals(original.recipe()) && loaded.recipe().isPresent(), "recipe");
        helper.assertTrue(loaded.travellingItems().size() == 1
                && loaded.travellingItems().get(0).batch().equals(batch(Items.DIAMOND, 5)), "item in transit");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void remoteOrdererOpensItsLinkedRequestPipeFromAfar(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.DIAMOND, 9));
        TestPlayer player = TestPlayer.in(helper);
        player.setPos(player.getX() + 500, player.getY(), player.getZ());
        ItemStack orderer = new ItemStack(LogisticsPipes.REMOTE_ORDERER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, orderer);

        orderer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "an unlinked orderer opens nothing");

        BlockPos pipePos = helper.absolutePos(new BlockPos(0, 1, 1));
        orderer.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, orderer,
                new BlockHitResult(Vec3.atCenterOf(pipePos), Direction.UP, pipePos, false)));
        helper.assertTrue(RemoteOrdererItem.isLinked(orderer), "sneak use on a request pipe links the orderer");

        helper.runAfterDelay(SETTLE, () -> {
            orderer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof RequestMenu, "linked orderer opens the request screen");
            RequestMenu menu = (RequestMenu) player.containerMenu;
            helper.assertTrue(menu.stillValid(player), "distance does not matter");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND), "it lists what the pipe can order");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            menu.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
        });

        helper.succeedWhen(() -> {
            assertCount(helper, target, Items.DIAMOND, 1);
        });
    }

    @GameTest(template = EMPTY)
    public static void remoteOrdererDoesNotLinkToOtherPipesOrOpenRemovedOnes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.PROVIDER, 0, 1);
        pipe(helper, PipeKind.REQUEST, 2, 1);
        TestPlayer player = TestPlayer.in(helper);
        ItemStack orderer = new ItemStack(LogisticsPipes.REMOTE_ORDERER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, orderer);

        BlockPos provider = helper.absolutePos(new BlockPos(0, 1, 1));
        orderer.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, orderer,
                new BlockHitResult(Vec3.atCenterOf(provider), Direction.UP, provider, false)));
        helper.assertTrue(!RemoteOrdererItem.isLinked(orderer), "only request pipes can be linked");

        BlockPos request = helper.absolutePos(new BlockPos(2, 1, 1));
        orderer.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, orderer,
                new BlockHitResult(Vec3.atCenterOf(request), Direction.UP, request, false)));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR);
        orderer.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "a removed pipe cannot be opened");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void requestNeedsPowerAndUsesItUp(GameTestHelper helper) {
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 3, 1);
        BlockPos target = chest(helper, 3, 2);
        pipe(helper, PipeKind.PROVIDER, 4, 1);
        chest(helper, 4, 2, new ItemStack(Items.COBBLESTONE, 64));
        int cost = 10 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM;
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(4, 1, 2), cost - 1);

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome refused = request.request(batch(Items.COBBLESTONE, 10));
            helper.assertTrue(!refused.started() && !refused.powered(), "one unit short should refuse the request");
            helper.assertTrue(junction.stored() == cost - 1, "a refused request must not use energy");

            junction.receive(1, false);
            helper.assertTrue(request.request(batch(Items.COBBLESTONE, 10)).started(), "exactly enough should do");
            helper.assertTrue(junction.stored() == 0, "the request should use up the energy");
            helper.assertTrue(cost == 20, "ten provided items cost 20 FE");
        });

        helper.succeedWhen(() -> assertCount(helper, target, Items.COBBLESTONE, 10));
    }

    @GameTest(template = EMPTY)
    public static void junctionTakesEnergyFromOtherBlocksButGivesNoneBack(GameTestHelper helper) {
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(4, 1, 1), 0);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(new BlockPos(4, 1, 1)), Direction.UP);
        helper.assertTrue(energy != null && energy.canReceive() && !energy.canExtract(), "junction should accept energy only");

        helper.assertTrue(energy.receiveEnergy(500, true) == 500 && junction.stored() == 0, "simulating stores nothing");
        helper.assertTrue(energy.receiveEnergy(500, false) == 500 && energy.getEnergyStored() == 500, "energy goes in");
        helper.assertTrue(energy.extractEnergy(100, false) == 0 && junction.stored() == 500, "nothing comes out");
        helper.assertTrue(energy.receiveEnergy(Integer.MAX_VALUE, false) == PowerJunctionBlockEntity.CAPACITY - 500, "it fills up to its capacity");

        CompoundTag saved = junction.saveWithoutMetadata(helper.getLevel().registryAccess());
        PowerJunctionBlockEntity loaded = GameTestPower.junction(helper, new BlockPos(6, 1, 1), 0);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.stored() == PowerJunctionBlockEntity.CAPACITY, "stored energy survives save and load");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void clientsCanSetMarkersOfTheMenuTheyHaveOpenAndNothingElse(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0, 1);
        TestPlayer player = TestPlayer.in(helper);
        BlockPos pos = new BlockPos(0, 1, 1);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false));
        helper.assertTrue(player.containerMenu instanceof PipeConfigMenu, "settings should be open");
        int id = player.containerMenu.containerId;

        MenuRequests.apply(player, id, 0, new ItemStack(Items.RAW_IRON, 2));
        MenuRequests.apply(player, id, PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(crafting.recipe().isPresent(), "markers from the client set the recipe");

        MenuRequests.apply(player, id + 1, 1, new ItemStack(Items.DIAMOND));
        MenuRequests.apply(player, id, PipeBlockEntity.OUTPUT_SLOT + 1, new ItemStack(Items.DIAMOND));
        MenuRequests.apply(player, id, -1, new ItemStack(Items.DIAMOND));
        helper.assertTrue(crafting.config().getItem(1).isEmpty(), "a wrong menu id is ignored");
        helper.assertTrue(player.getInventory().isEmpty(), "slots that are not markers are never written");

        MenuRequests.apply(player, id, 2, new ItemStack(Items.COAL, 99));
        helper.assertTrue(crafting.config().getItem(2).getCount() == 64, "counts are capped");
        MenuRequests.apply(player, id, 2, ItemStack.EMPTY);
        helper.assertTrue(crafting.config().getItem(2).isEmpty(), "an empty stack clears the marker");

        player.containerMenu = player.inventoryMenu;
        MenuRequests.apply(player, id, 3, new ItemStack(Items.DIAMOND));
        helper.assertTrue(crafting.config().getItem(3).isEmpty(), "nothing happens once the menu is closed");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void searchNarrowsTheListByNameOrId(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIAMOND, 3),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.OAK_LOG, 5));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            request.openRequestScreen(player, false);
            RequestMenu menu = (RequestMenu) player.containerMenu;
            helper.assertTrue(menu.getSlot(3).hasItem() && !menu.getSlot(4).hasItem(), "four kinds listed at first");

            MenuRequests.applyText(player, menu.containerId, RequestMenu.TEXT_SEARCH, "  DIAmond ");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(1).getItem().is(Items.DIAMOND_PICKAXE)
                    && !menu.getSlot(2).hasItem(), "search by name, case and spaces do not matter");

            MenuRequests.applyText(player, menu.containerId, RequestMenu.TEXT_SEARCH, "minecraft:oak");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_LOG) && !menu.getSlot(1).hasItem(), "search by item id");

            menu.clicked(0, 0, ClickType.PICKUP, player);
            MenuRequests.applyText(player, menu.containerId, RequestMenu.TEXT_SEARCH, "cobble");
            helper.assertTrue(menu.selectedSlot() == -1, "a selection that is filtered out is not shown as selected");
            MenuRequests.applyText(player, menu.containerId, RequestMenu.TEXT_SEARCH, "");
            helper.assertTrue(menu.getSlot(3).hasItem() && menu.selectedSlot() >= 0, "clearing the search brings everything back");

            MenuRequests.applyText(player, menu.containerId + 1, RequestMenu.TEXT_SEARCH, "diamond");
            helper.assertTrue(menu.getSlot(3).hasItem(), "text for another menu is ignored");
            helper.succeed();
        });
    }

    // a large order to a chest with one free slot: what does not fit waits in the pipe and follows once there is room
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void orderedItemsWaitInThePipeUntilTheChestHasRoom(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        Container almostFull = helper.getBlockEntity(target);
        for (int slot = 1; slot < almostFull.getContainerSize(); slot++) {
            almostFull.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64));
        pipe(helper, PipeKind.BASIC, 2, 1);
        BlockPos elsewhere = chest(helper, 2, 2);

        helper.runAfterDelay(SETTLE + 10, () -> helper.assertTrue(
                request.request(batch(Items.COBBLESTONE, 100)).started(), "request"));

        helper.runAfterDelay(150, () -> {
            assertCount(helper, target, Items.COBBLESTONE, 64);
            helper.assertContainerEmpty(elsewhere);
            helper.assertTrue(request.travellingItems().stream().mapToInt(item -> item.batch().count()).sum() == 36,
                    "the rest should be waiting in the request pipe");
            almostFull.setItem(1, ItemStack.EMPTY);
        });

        helper.runAfterDelay(250, () -> {
            assertCount(helper, target, Items.COBBLESTONE, 100);
            helper.assertContainerEmpty(elsewhere);
            helper.assertTrue(request.travellingItems().isEmpty(), "nothing left in the pipe");
            helper.succeed();
        });
    }

    // both halves of a double chest are one inventory: two providers on it must not double the stock
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void twoProvidersOnADoubleChestListItsContentsOnce(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        pipe(helper, PipeKind.PROVIDER, 2, 1);
        // facing north the left half has its partner to the east
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        Container half = helper.getBlockEntity(new BlockPos(1, 2, 1));
        half.setItem(0, new ItemStack(Items.COBBLESTONE, 40));

        helper.runAfterDelay(SETTLE + 10, () -> {
            PipeBlockEntity first = helper.getBlockEntity(new BlockPos(1, 1, 1));
            PipeBlockEntity second = helper.getBlockEntity(new BlockPos(2, 1, 1));
            Integer seenBySecond = second.contents().get(batch(Items.COBBLESTONE, 1).type());
            helper.assertTrue(seenBySecond != null && seenBySecond == 40, "the pipe on the other half should see the cobblestone too");
            helper.assertTrue(first.inventories().values().equals(second.inventories().values()) || first.inventories().values()
                    .containsAll(second.inventories().values()), "both halves should count as one inventory");
            Integer listed = request.available().get(batch(Items.COBBLESTONE, 1).type());
            helper.assertTrue(listed != null && listed == 40, "the double chest holds 40 but the request pipe lists " + listed);
            helper.assertTrue(!request.request(batch(Items.COBBLESTONE, 60)).started(), "60 are not there to be had");
            helper.succeed();
        });
    }

    // two stacks of ten, one item stays behind in each
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void providerLeavesOneItemPerStack(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        BlockPos target = chest(helper, 0, 2);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 1, 1);
        provider.setMode(ProviderMode.LEAVE_ONE_PER_STACK.ordinal());
        BlockPos source = chest(helper, 1, 2, new ItemStack(Items.COAL, 10), new ItemStack(Items.COAL, 10));

        helper.runAfterDelay(SETTLE + 15, () -> {
            int offered = request.available().getOrDefault(batch(Items.COAL, 1).type(), 0);
            helper.assertTrue(offered == 18, "expected 18 coal on offer but found " + offered);
            helper.assertTrue(!request.request(batch(Items.COAL, 19)).plan().complete(), "19 is more than it hands out");
            helper.assertTrue(request.request(batch(Items.COAL, 18)).plan().complete(), "18 can be had");
        });
        helper.runAfterDelay(100, () -> {
            assertCount(helper, target, Items.COAL, 18);
            Container chest = helper.getBlockEntity(source);
            helper.assertTrue(chest.getItem(0).getCount() == 1 && chest.getItem(1).getCount() == 1, "one coal stays in each stack");
            helper.succeed();
        });
    }

    // the first slot is not offered, and the list keeps iron back
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void providerKeepsItsFirstStackAndWhatIsExcluded(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0, 1);
        chest(helper, 0, 2);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 1, 1);
        provider.setMode(ProviderMode.LEAVE_FIRST.ordinal());
        provider.config().setItem(0, new ItemStack(Items.IRON_INGOT));
        provider.setExclude(true);
        chest(helper, 1, 2, new ItemStack(Items.COAL, 10), new ItemStack(Items.COAL, 7), new ItemStack(Items.IRON_INGOT, 5));

        helper.runAfterDelay(SETTLE + 15, () -> {
            java.util.Map<logisticspipes.domain.item.ItemType, Integer> offered = request.available();
            helper.assertTrue(offered.getOrDefault(batch(Items.COAL, 1).type(), 0) == 7, "only the second stack of coal is offered");
            helper.assertTrue(!offered.containsKey(batch(Items.IRON_INGOT, 1).type()), "iron is kept back");
            provider.setExclude(false);
            helper.assertTrue(request.available().getOrDefault(batch(Items.IRON_INGOT, 1).type(), 0) == 5, "now iron is all it gives");
            helper.assertTrue(!request.available().containsKey(batch(Items.COAL, 1).type()), "and coal is not");
            helper.succeed();
        });
    }

    // a supplier set to fill everything keeps ordering past its target
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void infiniteSupplierFillsPastItsTarget(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 0, 1);
        supplier.config().setItem(0, new ItemStack(Items.COAL, 1));
        supplier.setMode(SupplyMode.INFINITE.ordinal());
        BlockPos target = chest(helper, 0, 2);
        pipe(helper, PipeKind.PROVIDER, 1, 1);
        chest(helper, 1, 2, new ItemStack(Items.COAL, 64), new ItemStack(Items.COAL, 64), new ItemStack(Items.COAL, 64));

        helper.runAfterDelay(300, () -> {
            assertCount(helper, target, Items.COAL, 192);
            helper.succeed();
        });
    }

    // the supplier pipe as a module in a chassis
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void activeSupplierModuleKeepsChestStocked(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos chassisPos = new BlockPos(0, 1, 1);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        ItemStack module = new ItemStack(LogisticsPipes.MODULES.get(logisticspipes.domain.logistics.ModuleKind.ACTIVE_SUPPLIER).get());
        logisticspipes.adapter.minecraft.ModuleItem.configureSupplier(module, java.util.List.of(new ItemStack(Items.COAL, 16)), SupplyMode.PARTIAL);
        chassis.modules().setItem(0, module);
        BlockPos target = chest(helper, 0, 2);
        transport(helper, 1, 1);
        pipe(helper, PipeKind.PROVIDER, 2, 1);
        chest(helper, 2, 2, new ItemStack(Items.COAL, 64));

        helper.runAfterDelay(200, () -> {
            assertCount(helper, target, Items.COAL, 16);
            helper.succeed();
        });
    }
}
