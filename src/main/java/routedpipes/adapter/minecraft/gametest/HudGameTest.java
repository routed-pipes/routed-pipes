package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import routedpipes.adapter.minecraft.ChannelRegistry;
import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.HudGlassesItem;
import routedpipes.adapter.minecraft.HudPanelCodec;
import routedpipes.adapter.minecraft.HudWatch;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.menu.HudSettingsMenu;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.hud.HudCategory;
import routedpipes.domain.hud.HudElement;
import routedpipes.domain.channel.ChannelAccess;
import routedpipes.domain.hud.HudPanel;
import routedpipes.domain.hud.HudPanels;
import routedpipes.domain.hud.HudSettings;
import routedpipes.domain.hud.HudText;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.power.ProviderStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

// what a player who wears the glasses is told about the pipes around them
public class HudGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;

    // what the server sent, in the order it was sent
    private record Sent(boolean reset, Map<BlockPos, HudPanel> changed, List<BlockPos> gone) {
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static TestPlayer wearer(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Content.HUD_GLASSES.get()));
        return player;
    }

    private static HudPanel panelAt(GameTestHelper helper, TestPlayer player, int x) {
        return HudWatch.around(player).get(helper.absolutePos(new BlockPos(x, 1, 1)));
    }

    private static ItemBatch batch(Item item, int count) {
        return McItemType.batchOf(new ItemStack(item, count));
    }

    private static HudElement.Label label(String name, Object... args) {
        return new HudElement.Label(HudText.of("routedpipes.hud." + name, args), false);
    }

    @GameTest(template = EMPTY)
    public static void glassesAreWornOnTheHeadAndProtectFromNothing(GameTestHelper helper) {
        ItemStack glasses = new ItemStack(Content.HUD_GLASSES.get());
        TestPlayer player = TestPlayer.in(helper);

        helper.assertTrue(player.getEquipmentSlotForItem(glasses) == EquipmentSlot.HEAD, "they go on the head");
        helper.assertTrue(!glasses.isDamageableItem(), "they never wear out");
        helper.assertTrue(glasses.getItem().getDefaultAttributeModifiers().modifiers().isEmpty(), "no armour");
        pipe(helper, PipeKind.SATELLITE, 2);
        helper.assertTrue(HudWatch.around(player).isEmpty(), "without glasses on there is nothing to see");
        player.setItemSlot(EquipmentSlot.HEAD, glasses);
        helper.assertTrue(!HudWatch.around(player).isEmpty(), "with them there is");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void providerAndBasicPipesShowTheirLists(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = wearer(helper);
        pipe(helper, PipeKind.PROVIDER, 2);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);
        Container chest = helper.getBlockEntity(new BlockPos(2, 2, 1));
        chest.setItem(0, new ItemStack(Items.STONE, 64));
        chest.setItem(1, new ItemStack(Items.STONE, 36));
        chest.setItem(2, new ItemStack(Items.COAL, 5));
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 4);
        basic.config().setItem(0, new ItemStack(Items.DIAMOND));
        basic.setDefaultRoute(true);
        pipe(helper, PipeKind.BASIC, 3);
        pipe(helper, PipeKind.REQUEST, 5);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(new HudPanel(HudCategory.PROVIDER, 100, 110, List.of(label("provided"),
                    new HudElement.Grid(List.of(batch(Items.STONE, 100), batch(Items.COAL, 5)), 4, 12)))
                    .equals(panelAt(helper, player, 2)), "the provider lists the chest, got " + panelAt(helper, player, 2));
            helper.assertTrue(new HudPanel(HudCategory.CHASSIS, 100, 100, List.of(label("takes"),
                    new HudElement.Grid(List.of(batch(Items.DIAMOND, 1)), 3, 12), label("default_route")))
                    .equals(panelAt(helper, player, 4)), "the basic pipe lists its filter, got " + panelAt(helper, player, 4));
            helper.assertTrue(panelAt(helper, player, 3) == null, "a fresh basic pipe has nothing to show");
            helper.assertTrue(panelAt(helper, player, 5) == null, "a request pipe never has");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void chassisShowsItsModulesAndWhatIsSetOnThem(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = wearer(helper);
        BlockPos pos = new BlockPos(2, 1, 1);
        helper.setBlock(pos, Content.CHASSIS_PIPES.get(8).get());
        PipeBlockEntity chassis = helper.getBlockEntity(pos);
        helper.assertTrue(panelAt(helper, player, 2) == null, "an empty chassis has nothing to show");
        ItemStack sink = new ItemStack(Content.MODULES.get(ModuleKind.ITEM_SINK).get());
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), false);
        chassis.modules().setItem(0, sink);
        ItemStack extractor = new ItemStack(Content.MODULES.get(ModuleKind.EXTRACTOR).get());
        chassis.modules().setItem(3, extractor);

        HudPanel panel = panelAt(helper, player, 2);
        HudElement.Grid diamonds = new HudElement.Grid(List.of(batch(Items.DIAMOND, 1)), 3, 12);
        helper.assertTrue(new HudPanel(HudCategory.CHASSIS, 100, 100, List.of(new HudElement.Modules(List.of(
                new HudElement.Module(McItemType.batchOf(sink), List.of(diamonds)),
                new HudElement.Module(McItemType.batchOf(extractor), List.of(label("nothing")))), List.of(diamonds))))
                .equals(panel), "both modules in slot order and what the chassis takes, got " + panel);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void craftingPipeShowsItsResultAndWhatIsLeftToCraft(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = wearer(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.CHEST);
        pipe(helper, PipeKind.PROVIDER, 1);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        Container logs = helper.getBlockEntity(new BlockPos(1, 2, 1));
        logs.setItem(0, new ItemStack(Items.OAK_LOG, 16));
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 2);
        helper.assertTrue(panelAt(helper, player, 2) == null, "no recipe, nothing to show");
        helper.setBlock(new BlockPos(2, 2, 1), Content.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(new BlockPos(2, 2, 1));
        table.pattern().setItem(0, new ItemStack(Items.OAK_LOG));
        crafting.importRecipe();
        HudElement.Entry result = new HudElement.Entry(HudText.of("routedpipes.hud.result"), batch(Items.OAK_PLANKS, 4));

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(new HudPanel(HudCategory.CRAFTING, 60, 47, List.of(result)).equals(panelAt(helper, player, 2)),
                    "the small panel with the result, got " + panelAt(helper, player, 2));
            helper.assertTrue(request.request(batch(Items.OAK_PLANKS, 8)).started(), "planks can be ordered");

            helper.assertTrue(new HudPanel(HudCategory.CRAFTING, 100, 58, List.of(result,
                    new HudElement.Entry(HudText.of("routedpipes.hud.todo"), batch(Items.OAK_PLANKS, 8))))
                    .equals(panelAt(helper, player, 2)), "the big panel with the open order, got " + panelAt(helper, player, 2));
            helper.succeedWhen(() -> helper.assertTrue(panelAt(helper, player, 2).height() == 47, "small again once all is shipped"));
        });
    }

    @GameTest(template = EMPTY)
    public static void satelliteShowsItsNumber(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = wearer(helper);
        PipeBlockEntity satellite = pipe(helper, PipeKind.SATELLITE, 2);
        satellite.setSatelliteName("7");
        HudElement.Label name = new HudElement.Label(HudText.of("routedpipes.hud.satellite", 7), true);

        helper.assertTrue(new HudPanel(HudCategory.SATELLITE, 100, 35, List.of(name)).equals(panelAt(helper, player, 2)),
                "a satellite always shows its number, got " + panelAt(helper, player, 2));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void powerJunctionAndProviderShowTheirCharge(GameTestHelper helper) {
        TestPlayer player = wearer(helper);
        GameTestPower.junction(helper, new BlockPos(2, 1, 1), 1234);
        GameTestPower.provider(helper, new BlockPos(3, 1, 1), 5678);

        helper.assertTrue(new HudPanel(HudCategory.POWER, 120, 80, List.of(label("stored_energy"),
                new HudElement.Bar(1234, PowerJunctionBlockEntity.CAPACITY), label("energy", 1234),
                label("energy_max", PowerJunctionBlockEntity.CAPACITY))).equals(panelAt(helper, player, 2)),
                "the junction, got " + panelAt(helper, player, 2));
        helper.assertTrue(panelAt(helper, player, 3).elements().get(1).equals(new HudElement.Bar(5678, ProviderStore.CAPACITY)),
                "the provider, got " + panelAt(helper, player, 3));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void supplierShowsWhatItKeepsStocked(GameTestHelper helper) {
        TestPlayer player = wearer(helper);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 2);
        helper.assertTrue(panelAt(helper, player, 2) == null, "nothing to stock, nothing to show");
        supplier.config().setItem(0, new ItemStack(Items.COAL, 16));
        supplier.config().setItem(1, new ItemStack(Items.COAL, 8));

        helper.assertTrue(new HudPanel(HudCategory.CHASSIS, 100, 100, List.of(label("stocks"),
                new HudElement.Grid(List.of(batch(Items.COAL, 24)), 3, 12))).equals(panelAt(helper, player, 2)),
                "the targets added up, got " + panelAt(helper, player, 2));
        helper.succeed();
    }

    // the scene of the connector tests: an order crosses a chest two connectors share
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void connectorShowsItsPanelAWhileAfterSomethingCameThrough(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = wearer(helper);
        pipe(helper, PipeKind.PROVIDER, 0);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.CHEST);
        Container stock = helper.getBlockEntity(new BlockPos(0, 2, 1));
        stock.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        PipeBlockEntity near = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 1);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        PipeBlockEntity far = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 3);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 4);
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.CHEST);
        GameTestPower.junction(helper, new BlockPos(4, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        UUID channel = ChannelRegistry.of(helper.getLevel()).channels()
                .create("test", UUID.randomUUID(), "Tester", ChannelAccess.PRIVATE, null).orElseThrow().id();
        near.connector().setChannel(channel);
        far.connector().setChannel(channel);
        // the far connector takes the stack out in the tick it arrives, so mostly the panel is seen empty
        HudPanel emptied = new HudPanel(HudCategory.CONNECTOR, 100, 100, List.of(label("expected"), new HudElement.Grid(List.of(), 3, 9)));
        long[] shownFrom = {-1};
        helper.onEachTick(() -> {
            if (shownFrom[0] < 0 && panelAt(helper, player, 3) != null) {
                helper.assertTrue(emptied.equals(panelAt(helper, player, 3)), "the panel after the hand over, got " + panelAt(helper, player, 3));
                shownFrom[0] = helper.getTick();
            }
        });

        helper.assertTrue(panelAt(helper, player, 3) == null, "nothing to show while it waits for nothing");
        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome outcome = request.request(batch(Items.COBBLESTONE, 10));
            helper.assertTrue(outcome.started(), "the order starts, missing " + outcome.missing() + ", powered " + outcome.powered()
                    + ", connectors " + near.getBlockState() + " " + far.getBlockState());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shownFrom[0] >= 0, "a panel once something came through the chest");
            helper.assertTrue(panelAt(helper, player, 3) == null, "gone two seconds later");
            helper.assertTrue(helper.getTick() - shownFrom[0] >= HudPanels.CONNECTOR_LINGER - 1, "not before");
        });
    }

    @GameTest(template = EMPTY)
    public static void theSettingsOfTheGlassesHideKindsOfPanels(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);
        pipe(helper, PipeKind.SATELLITE, 2);
        PipeBlockEntity supplier = pipe(helper, PipeKind.SUPPLIER, 4);
        supplier.config().setItem(0, new ItemStack(Items.COAL));
        ItemStack glasses = new ItemStack(Content.HUD_GLASSES.get());
        player.getInventory().setItem(0, glasses);
        player.getInventory().selected = 0;

        player.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof HudSettingsMenu, "using them in the hand opens the settings");
        HudSettingsMenu menu = (HudSettingsMenu) player.containerMenu;
        menu.clickMenuButton(player, HudCategory.SATELLITE.ordinal());
        helper.assertTrue(!HudGlassesItem.settingsOf(glasses).shows(HudCategory.SATELLITE), "satellites are hidden");
        helper.assertTrue(menu.settings().equals(HudSettings.ALL.toggled(HudCategory.SATELLITE)), "the rest is still shown");

        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(!menu.stillValid(player), "the settings close once the glasses are moved away");
        player.setItemSlot(EquipmentSlot.HEAD, glasses);
        helper.assertTrue(panelAt(helper, player, 2) == null, "the satellite has no panel");
        helper.assertTrue(panelAt(helper, player, 4) != null, "the supplier has one");

        ItemStack copy = ItemStack.parse(helper.getLevel().registryAccess(), glasses.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(HudGlassesItem.settingsOf(copy).equals(HudGlassesItem.settingsOf(glasses)), "the settings are kept on the glasses");
        helper.succeed();
    }

    // FINDING: the armour slots of the glasses settings take off a helmet with curse of binding, the inventory screen does not
    @GameTest(template = EMPTY)
    public static void theSettingsOfTheGlassesDoNotTakeOffCursedArmour(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);
        player.getInventory().setItem(0, new ItemStack(Content.HUD_GLASSES.get()));
        player.getInventory().selected = 0;
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.assertTrue(!player.inventoryMenu.getSlot(InventoryMenu.ARMOR_SLOT_START).mayPickup(player),
                "the inventory screen keeps the cursed helmet on");

        player.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        HudSettingsMenu menu = (HudSettingsMenu) player.containerMenu;
        // the helmet slot comes right after the hotbar
        menu.clicked(9, 0, ClickType.PICKUP, player);
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "the cursed helmet stays on, the cursor holds " + menu.getCarried());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void panelsShowOnlyNearThePlayer(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = wearer(helper);
        pipe(helper, PipeKind.SATELLITE, 2);
        helper.assertTrue(panelAt(helper, player, 2) != null, "two blocks away");

        player.setPos(player.getX() + 14, player.getY(), player.getZ());
        helper.assertTrue(panelAt(helper, player, 2) != null, "still in reach from the other side");
        player.setPos(player.getX() + 4, player.getY(), player.getZ());
        helper.assertTrue(panelAt(helper, player, 2) == null, "too far away");

        BlockPos pipe = helper.absolutePos(new BlockPos(2, 1, 1));
        player.setPos(pipe.getX() + 0.5, pipe.getY() + 0.5 - player.getEyeHeight(), pipe.getZ() + 0.9);
        helper.assertTrue(panelAt(helper, player, 2) == null, "with the head in the pipe");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aPlayerIsOnlySentWhatChanged(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer player = TestPlayer.in(helper);
        List<Sent> sent = new ArrayList<>();
        HudWatch watch = new HudWatch((receiver, reset, changed, gone) -> sent.add(new Sent(reset, changed, gone)));
        PipeBlockEntity first = pipe(helper, PipeKind.SATELLITE, 2);
        pipe(helper, PipeKind.SATELLITE, 4);
        BlockPos firstPos = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos secondPos = helper.absolutePos(new BlockPos(4, 1, 1));

        watch.refresh(player);
        helper.assertTrue(sent.isEmpty(), "nothing without glasses");

        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Content.HUD_GLASSES.get()));
        watch.refresh(player);
        // the pipes of the tests next to this one are in reach too
        helper.assertTrue(sent.size() == 1 && sent.get(0).reset() && sent.get(0).changed().keySet().containsAll(List.of(firstPos, secondPos))
                && sent.get(0).gone().isEmpty(), "everything at first, got " + sent);

        watch.refresh(player);
        helper.assertTrue(sent.size() == 1, "nothing while nothing changes");

        first.setSatelliteName("3");
        watch.refresh(player);
        helper.assertTrue(sent.size() == 2 && !sent.get(1).reset() && sent.get(1).changed().keySet().equals(Set.of(firstPos))
                && sent.get(1).gone().isEmpty(), "only the pipe that changed, got " + sent);

        helper.setBlock(new BlockPos(4, 1, 1), Blocks.AIR);
        watch.refresh(player);
        helper.assertTrue(sent.size() == 3 && sent.get(2).changed().isEmpty() && sent.get(2).gone().equals(List.of(secondPos)),
                "the broken pipe is gone, got " + sent);

        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        watch.refresh(player);
        helper.assertTrue(sent.size() == 4 && sent.get(3).reset() && sent.get(3).changed().isEmpty(), "all gone with the glasses off");
        watch.refresh(player);
        helper.assertTrue(sent.size() == 4, "and nothing after that");
        helper.succeed();
    }

    // what is sent has to arrive the way it was made
    @GameTest(template = EMPTY)
    public static void panelsSurviveTheWayToTheClient(GameTestHelper helper) {
        ItemStack sink = new ItemStack(Content.MODULES.get(ModuleKind.ITEM_SINK).get());
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), true);
        HudElement.Grid grid = new HudElement.Grid(List.of(batch(Items.STONE, 100_000), batch(Items.COAL, 1)), 4, 12);
        HudPanel panel = new HudPanel(HudCategory.CHASSIS, 100, 110, List.of(label("satellite", 7),
                new HudElement.Entry(HudText.of("routedpipes.hud.result"), batch(Items.OAK_PLANKS, 4)), grid,
                new HudElement.Bar(5_000_000_000L, 10_000_000_000L),
                new HudElement.Modules(List.of(new HudElement.Module(McItemType.batchOf(sink), List.of(grid))), List.of(label("nothing")))));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());

        HudPanelCodec.CODEC.encode(buffer, panel);

        HudPanel received = HudPanelCodec.CODEC.decode(buffer);
        helper.assertTrue(panel.equals(received), "the same panel comes out, got " + received);
        helper.succeed();
    }
}
