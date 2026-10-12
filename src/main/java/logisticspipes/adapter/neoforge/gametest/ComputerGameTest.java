package logisticspipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import logisticspipes.adapter.minecraft.CraftingTableBlockEntity;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PipeComputer;
import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.minecraft.SecurityStationBlockEntity;
import logisticspipes.adapter.minecraft.SecurityStations;
import logisticspipes.adapter.minecraft.menu.SecurityStationMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.application.computer.Caller;
import logisticspipes.application.computer.ComputerException;
import logisticspipes.application.computer.PipeCommands;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.UpgradeKind;
import logisticspipes.domain.security.SecuritySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// what a computer gets out of real pipes, without a computer: the commands are the same with and without CC: Tweaked
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class ComputerGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;
    private static final Caller COMPUTER = Caller.attached(7);

    static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static PipeCommands commands(PipeBlockEntity pipe) {
        return PipeComputer.of(pipe).orElseThrow().commands((target, computer, event, arguments) -> { });
    }

    private static Object[] call(PipeBlockEntity pipe, String name, Object... arguments) {
        return commands(pipe).call(name, COMPUTER, Arrays.asList(arguments));
    }

    private static void assertEquals(GameTestHelper helper, Object expected, Object actual) {
        helper.assertTrue(expected.equals(actual), "expected " + expected + " but got " + actual);
    }

    @GameTest(template = EMPTY)
    public static void plainPipesHaveNothingForComputers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 1);
        helper.setBlock(pos, LogisticsPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity plain = helper.getBlockEntity(pos);

        helper.assertTrue(PipeComputer.of(plain).isEmpty(), "a plain pipe has commands");
        helper.assertTrue(PipeComputer.of(pipe(helper, PipeKind.BASIC, 2)).isPresent(), "a routed pipe has no commands");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aPipeTellsItsTypeAndItsId(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 2);

        assertEquals(helper, "LogisticsPipes:Normal", call(basic, "getType")[0]);
        assertEquals(helper, "LogisticsPipes:Request", call(request, "getType")[0]);
        assertEquals(helper, basic.routerId().value().toString(), call(basic, "getRouterUUID")[0]);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aBasicPipeShowsItsListAsAnItemSinkModule(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        basic.config().setItem(0, new ItemStack(Items.COBBLESTONE));
        basic.setDefaultRoute(true);

        assertEquals(helper, true, call(basic, "hasLogisticsModule")[0]);
        assertEquals(helper, Map.of("type", "ItemSink Module", "kind", "item_sink", "hasGui", true, "defaultRoute", true,
                "filter", List.of(Map.of("name", "minecraft:cobblestone", "displayName", "Cobblestone", "count", 1))),
                call(basic, "getLogisticsModule")[0]);
        assertEquals(helper, false, call(pipe(helper, PipeKind.SATELLITE, 2), "hasLogisticsModule")[0]);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aBroadcastReachesThePipesOfTheNetwork(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 1);
        List<String> heard = new ArrayList<>();
        PipeCommands commands = PipeComputer.of(basic).orElseThrow().commands((target, computer, event, arguments) ->
                heard.add(target.value() + " " + event + " " + Arrays.asList(arguments)));

        helper.runAfterDelay(SETTLE, () -> {
            commands.call("sendBroadcast", COMPUTER, List.of("hello"));
            assertEquals(helper, List.of(basic.routerId().value() + " LP_BROADCAST [7, hello]",
                    provider.routerId().value() + " LP_BROADCAST [7, hello]"), heard);
            helper.succeed();
        });
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

    private static Map<String, Object> item(String name, String displayName, int count) {
        return Map.of("name", name, "displayName", displayName, "count", count);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aRequestFromAComputerIsDelivered(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(SETTLE, () -> {
            assertEquals(helper, List.of(item("minecraft:cobblestone", "Cobblestone", 64)), call(request, "getAvailableItems")[0]);
            assertEquals(helper, 64, call(request, "getItemAmount", "minecraft:cobblestone")[0]);
            assertEquals(helper, List.of("MISSING", List.of(item("minecraft:cobblestone", "Cobblestone", 6))),
                    Arrays.asList(call(request, "makeRequest", "minecraft:cobblestone", 70.0)));
            assertEquals(helper, List.of("DONE", List.of(item("minecraft:cobblestone", "Cobblestone", 10))),
                    Arrays.asList(call(request, "makeRequest", "minecraft:cobblestone", 10.0)));
        });

        helper.succeedWhen(() -> {
            Container container = helper.getBlockEntity(target);
            helper.assertTrue(container.countItem(Items.COBBLESTONE) == 10, "the cobblestone has not arrived");
        });
    }

    @GameTest(template = EMPTY)
    public static void withoutPowerAComputerGetsNothing(GameTestHelper helper) {
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(SETTLE, () -> {
            assertEquals(helper, List.of("NO_POWER"), Arrays.asList(call(request, "makeRequest", "minecraft:cobblestone", 10.0)));
            helper.succeed();
        });
    }

    // a worn tool is not the same item as a new one, the table from the list is how a script asks for exactly it
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void anItemWithDataIsRequestedByItsTable(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(5);
        chest(helper, 1, new ItemStack(Items.IRON_PICKAXE), worn);

        helper.runAfterDelay(SETTLE, () -> {
            List<?> available = (List<?>) call(request, "getAvailableItems")[0];
            List<?> withData = available.stream().filter(entry -> ((Map<?, ?>) entry).containsKey("nbt")).toList();
            assertEquals(helper, 2, available.size());
            assertEquals(helper, 1, withData.size());
            assertEquals(helper, "DONE", call(request, "makeRequest", withData.get(0))[0]);
        });

        helper.succeedWhen(() -> {
            Container container = helper.getBlockEntity(target);
            helper.assertTrue(container.getItem(0).getDamageValue() == 5 && container.getItem(0).getCount() == 1,
                    "the worn pickaxe has not arrived");
        });
    }

    @GameTest(template = EMPTY)
    public static void aCraftingPipeImportsAndShowsItsRecipe(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        BlockPos pos = new BlockPos(0, 2, 1);
        helper.setBlock(pos, LogisticsPipes.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(pos);
        table.pattern().setItem(0, new ItemStack(Items.OAK_LOG));

        assertEquals(helper, "LogisticsPipes:Crafting", call(crafting, "getType")[0]);
        helper.assertTrue(call(crafting, "getRecipe")[0] == null, "a recipe before the import");
        call(crafting, "reimport");

        assertEquals(helper, Map.of("output", item("minecraft:oak_planks", "Oak Planks", 4),
                "inputs", List.of(item("minecraft:oak_log", "Oak Log", 1)), "priority", 0), call(crafting, "getRecipe")[0]);
        assertEquals(helper, List.of(), call(crafting, "getOrders")[0]);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aCraftingPipeShowsWhatItOwes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        chest(helper, 0);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 1);
        crafting.config().setItem(0, new ItemStack(Items.OAK_LOG));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OAK_PLANKS, 4));
        pipe(helper, PipeKind.PROVIDER, 2);
        chest(helper, 2, new ItemStack(Items.OAK_LOG, 16));

        helper.runAfterDelay(SETTLE, () -> {
            assertEquals(helper, List.of(Map.of("name", "minecraft:oak_planks", "displayName", "Oak Planks")),
                    call(request, "getCraftableItems")[0]);
            assertEquals(helper, "DONE", call(request, "makeRequest", "minecraft:oak_planks", 8.0)[0]);
            Map<String, Object> owed = new HashMap<>(item("minecraft:oak_planks", "Oak Planks", 8));
            owed.put("destination", request.routerId().value().toString());
            assertEquals(helper, List.of(owed), call(crafting, "getOrders")[0]);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void aChassisShowsItsModules(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 1);
        helper.setBlock(pos, LogisticsPipes.CHASSIS_PIPES.get(2).get());
        PipeBlockEntity chassis = helper.getBlockEntity(pos);
        ItemStack sink = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get());
        ModuleItem.configureSink(sink, List.of(new ItemStack(Items.DIAMOND)), true);
        chassis.modules().setItem(1, sink);

        assertEquals(helper, "LogisticsChassiePipe", call(chassis, "getType")[0]);
        assertEquals(helper, 2, call(chassis, "getChassieSize")[0]);
        helper.assertTrue(call(chassis, "getModuleInSlot", 1.0)[0] == null, "a module in the empty slot");
        assertEquals(helper, Map.of("type", "ItemSink Module", "kind", "item_sink", "hasGui", true, "defaultRoute", true,
                "filter", List.of(item("minecraft:diamond", "Diamond", 1))), call(chassis, "getModuleInSlot", 2.0)[0]);
        helper.succeed();
    }

    // a station at the place the junctions of GameTestPower leave free, with the pipe locked to it
    private static SecurityStationBlockEntity lockedTo(GameTestHelper helper, PipeBlockEntity pipe) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, LogisticsPipes.SECURITY_STATION.get());
        SecurityStationBlockEntity station = helper.getBlockEntity(pos);
        station.station();
        pipe.security().card().setItem(0, LogisticsPipes.SECURITY_CARD.get().cardFor(station.id()));
        return station;
    }

    private static boolean denied(PipeBlockEntity pipe, int computer) {
        try {
            commands(pipe).call("getRouterUUID", Caller.attached(computer), List.of());
            return false;
        } catch (ComputerException e) {
            return e.getMessage().equals("Permission denied");
        }
    }

    @GameTest(template = EMPTY)
    public static void aLockedPipeOnlyLetsListedComputersIn(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        SecuritySettings settings = lockedTo(helper, basic).station().settings();

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(denied(basic, 7), "a computer nobody listed gets in");
            assertEquals(helper, false, call(basic, "canAccess")[0]);
            settings.addComputerException(7);
            helper.assertFalse(denied(basic, 7), "the listed computer is kept out");
            assertEquals(helper, true, call(basic, "canAccess")[0]);
            helper.assertTrue(denied(basic, 8), "another computer gets in");

            settings.setComputersAllowed(true);
            helper.assertTrue(denied(basic, 7), "with computers allowed the listed one gets in");
            helper.assertFalse(denied(basic, 8), "with computers allowed another one is kept out");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void lookingAComputerUpCostsEnergy(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(0, 1, 2), SecuritySettings.CHECK_ENERGY);
        lockedTo(helper, basic).station().settings().setComputersAllowed(true);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertFalse(denied(basic, 7), "with energy the computer is kept out");
            assertEquals(helper, 0, junction.draw(Integer.MAX_VALUE, true));
            helper.assertTrue(denied(basic, 7), "without energy the computer gets in");
            assertEquals(helper, false, call(basic, "canAccess")[0]);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void aPipeWithoutACardLetsEveryComputerIn(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);

        helper.assertFalse(denied(basic, 7), "a pipe that is not locked keeps a computer out");
        assertEquals(helper, true, call(basic, "canAccess")[0]);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theStationMenuSetsWhichComputersGetIn(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, LogisticsPipes.SECURITY_STATION.get());
        SecurityStationBlockEntity station = helper.getBlockEntity(pos);
        SecuritySettings settings = station.station().settings();
        SecurityStationMenu menu = new SecurityStationMenu(LogisticsPipes.SECURITY_STATION_MENU.get(), 1, player.getInventory(), station);

        assertEquals(helper, ModList.get().isLoaded("computercraft"), menu.hasComputers());
        menu.clickMenuButton(player, SecurityStationMenu.BUTTON_COMPUTERS);
        assertEquals(helper, SecurityStationMenu.Tab.COMPUTERS, menu.tab());
        helper.assertFalse(menu.computersAllowed(), "computers are allowed from the start");
        menu.clickMenuButton(player, SecurityStationMenu.BUTTON_COMPUTERS_ALLOWED);
        helper.assertTrue(menu.computersAllowed() && settings.computersAllowed(), "the switch does nothing");

        // more than fit on a page, one of them beyond what a data slot holds
        for (int computer = 0; computer < SecurityStationMenu.COMPUTERS_PER_PAGE; computer++) {
            menu.setText(SecurityStationMenu.TEXT_ADD_COMPUTER, String.valueOf(computer));
        }
        menu.setText(SecurityStationMenu.TEXT_ADD_COMPUTER, " 70000 ");
        menu.setText(SecurityStationMenu.TEXT_ADD_COMPUTER, "seven");
        menu.setText(SecurityStationMenu.TEXT_ADD_COMPUTER, "-1");
        assertEquals(helper, List.of(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 70000), settings.computerExceptions());
        // adding one shows the page it is on
        assertEquals(helper, List.of(-1, 0, 1, 2, 3, 4, 5, 6, 7), menu.computers());
        assertEquals(helper, 2, menu.computerPages());
        menu.clickMenuButton(player, SecurityStationMenu.BUTTON_NEXT_COMPUTERS);
        assertEquals(helper, List.of(8, 70000), menu.computers());
        menu.clickMenuButton(player, SecurityStationMenu.BUTTON_NEXT_COMPUTERS);
        assertEquals(helper, 1, menu.computerPage());
        menu.clickMenuButton(player, SecurityStationMenu.BUTTON_PREVIOUS_COMPUTERS);
        assertEquals(helper, 0, menu.computerPage());

        menu.setText(SecurityStationMenu.TEXT_REMOVE_COMPUTER, "70000");
        menu.setText(SecurityStationMenu.TEXT_REMOVE_COMPUTER, "-1");
        assertEquals(helper, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), menu.computers());
        assertEquals(helper, 1, menu.computerPages());
        // the other tabs still work the way they did
        menu.clickMenuButton(player, SecurityStationMenu.Tab.PLAYERS.ordinal());
        assertEquals(helper, SecurityStationMenu.Tab.PLAYERS, menu.tab());
        menu.clickMenuButton(player, SecurityStationMenu.BUTTON_AUTHORIZED);
        helper.assertFalse(settings.authorized(), "the button for the cards switched tabs");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void whichComputersGetInIsSavedWithTheWorld(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        SecurityStations stations = new SecurityStations();
        SecuritySettings settings = stations.station(id).settings();
        settings.setComputersAllowed(true);
        settings.addComputerException(70000);
        settings.addComputerException(-1);

        SecuritySettings loaded = SecurityStations.load(stations.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess()).station(id).settings();

        helper.assertTrue(loaded.computersAllowed(), "the switch is lost");
        assertEquals(helper, List.of(-1, 70000), loaded.computerExceptions());
        helper.succeed();
    }

    private static ItemStack remoteControl() {
        return new ItemStack(LogisticsPipes.UPGRADES.get(UpgradeKind.CC_REMOTE_CONTROL).get());
    }

    @GameTest(template = EMPTY)
    public static void theRemoteControlUpgradeHandsOutOtherPipes(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 1);
        String other = request.routerId().value().toString();

        helper.runAfterDelay(SETTLE, () -> {
            try {
                call(basic, "getPipeForUUID", other);
                helper.fail("another pipe without the upgrade");
            } catch (ComputerException e) {
                helper.assertTrue(e.getMessage().startsWith("Permission denied"), e.getMessage());
            }
            helper.assertTrue(basic.installUpgrade(remoteControl()), "the upgrade does not fit a basic pipe");

            PipeCommands remote = (PipeCommands) call(basic, "getPipeForUUID", other)[0];
            assertEquals(helper, "LogisticsPipes:Request", remote.type());
            assertEquals(helper, List.of(other), Arrays.asList(remote.call("getRouterUUID", Caller.REMOTE, List.of())));
            assertEquals(helper, List.of(), remote.call("getAvailableItems", Caller.REMOTE, List.of())[0]);
            helper.assertTrue(call(basic, "getPipeForUUID", UUID.randomUUID().toString())[0] == null, "a pipe that does not exist");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void theRemoteControlUpgradeGoesIntoPipesOnly(GameTestHelper helper) {
        for (PipeKind kind : List.of(PipeKind.BASIC, PipeKind.CRAFTING, PipeKind.CHASSIS, PipeKind.FLUID_BASIC)) {
            BlockPos pos = new BlockPos(kind.ordinal() % 5, 1, 1 + kind.ordinal() / 5 * 2);
            helper.setBlock(pos, kind == PipeKind.CHASSIS ? LogisticsPipes.CHASSIS_PIPES.get(1).get() : LogisticsPipes.pipe(kind));
            PipeBlockEntity pipe = helper.getBlockEntity(pos);
            helper.assertTrue(pipe.installUpgrade(remoteControl()), "the upgrade does not fit " + kind);
        }
        for (ModuleKind module : ModuleKind.values()) {
            helper.assertFalse(UpgradeKind.CC_REMOTE_CONTROL.fitsModule(module), "the upgrade fits the module " + module);
        }
        helper.succeed();
    }
}
