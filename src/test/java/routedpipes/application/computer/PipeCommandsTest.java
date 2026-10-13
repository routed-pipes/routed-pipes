package routedpipes.application.computer;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import routedpipes.application.LogisticsNetwork;
import routedpipes.domain.crafting.CraftingSetup;
import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeComputerPipe;
import routedpipes.testing.FakeComputerPipe.Event;
import routedpipes.testing.FakeNode;
import routedpipes.testing.FakeNode.Shipment;
import routedpipes.testing.Fixtures.TraitItem;
import org.junit.jupiter.api.Test;

class PipeCommandsTest {

    private static final RouterId OWN = router(1);
    private static final RouterId NEAR = router(2);
    private static final RouterId FAR = router(3);
    private static final RouterId ISLAND = router(9);
    private static final long BUILT = -100;
    private static final Caller COMPUTER = Caller.attached(7);
    private static final Caller REMOTE = Caller.REMOTE;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeComputerPipe.Events events = new FakeComputerPipe.Events();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode crafting = new FakeNode(PipeKind.CRAFTING);

    // routers in a row, one apart, linked both ways
    private void connect(List<RouterId> ids, List<FakeNode> nodes) {
        for (int i = 0; i < ids.size(); i++) {
            network.addRouter(ids.get(i), nodes.get(i), BUILT);
        }
        for (int i = 0; i < ids.size(); i++) {
            List<Link> links = new ArrayList<>();
            if (i > 0) {
                links.add(new Link(ids.get(i - 1), Side.WEST, 1));
            }
            if (i < ids.size() - 1) {
                links.add(new Link(ids.get(i + 1), Side.EAST, 1));
            }
            network.updateLinks(ids.get(i), links, BUILT);
        }
    }

    private void connect(RouterId... ids) {
        connect(List.of(ids), Arrays.stream(ids).map(id -> new FakeNode(PipeKind.BASIC)).toList());
    }

    private PipeCommands commands(PipeKind kind) {
        return commands(new FakeComputerPipe(OWN, new FakeNode(kind)));
    }

    // on the request pipe of the network with the provider and the crafting pipe
    private PipeCommands requesting() {
        connect(List.of(OWN, NEAR, FAR), List.of(request, provider, crafting));
        return commands(new FakeComputerPipe(OWN, request));
    }

    private static Map<String, Object> item(String name, int count) {
        return Map.of("name", name, "count", count);
    }

    private PipeCommands commands(FakeComputerPipe pipe) {
        return new PipeCommands(network, pipe, FakeComputerPipe.ITEMS, events);
    }

    private static Object[] call(PipeCommands commands, String name, Object... arguments) {
        return commands.call(name, COMPUTER, Arrays.asList(arguments));
    }

    @Test
    void pipesGoByTheTypeNamesOfTheOriginal() {
        assertThat(commands(PipeKind.BASIC).type()).isEqualTo("RoutedPipes:Normal");
        assertThat(commands(PipeKind.FLUID_REQUEST).type()).isEqualTo("RoutedPipes:Normal");
        assertThat(commands(PipeKind.REQUEST).type()).isEqualTo("RoutedPipes:Request");
        assertThat(commands(PipeKind.REQUEST_MK2).type()).isEqualTo("RoutedPipes:Request");
        assertThat(commands(PipeKind.REQUEST_TABLE).type()).isEqualTo("RoutedPipes:Request");
        assertThat(commands(PipeKind.CRAFTING).type()).isEqualTo("RoutedPipes:Crafting");
        assertThat(commands(PipeKind.CHASSIS).type()).isEqualTo("LogisticsChassiePipe");
        assertThat(call(commands(PipeKind.CHASSIS), "getType")).containsExactly("LogisticsChassiePipe");
    }

    @Test
    void tellsTheIdOfItsRouter() {
        assertThat(call(commands(PipeKind.BASIC), "getRouterUUID")).containsExactly(OWN.value().toString());
    }

    @Test
    void aPipeWithoutABuiltInModuleSaysSo() {
        PipeCommands commands = commands(PipeKind.SATELLITE);

        assertThat(call(commands, "hasLogisticsModule")).containsExactly(false);
        assertThat(call(commands, "getLogisticsModule")).containsExactly((Object) null);
    }

    @Test
    void describesTheBuiltInModuleAsATable() {
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, new FakeNode(PipeKind.BASIC));
        pipe.module = ModuleInfo.of(ModuleKind.ITEM_SINK, true, List.of(batch("stone", 1)), true);
        PipeCommands commands = commands(pipe);

        assertThat(call(commands, "hasLogisticsModule")).containsExactly(true);
        assertThat(call(commands, "getLogisticsModule")).containsExactly(Map.of("type", "ItemSink Module", "kind", "item_sink",
                "hasGui", true, "filter", List.of(Map.of("name", "stone", "count", 1)), "defaultRoute", true));
    }

    @Test
    void onlyModulesWithAListShowOne() {
        assertThat(ModuleInfo.of(ModuleKind.PROVIDER, true, List.of(), false))
                .isEqualTo(new ModuleInfo("Provider Module", "provider", true, Optional.of(List.of()), Optional.empty()));
        assertThat(ModuleInfo.of(ModuleKind.ADVANCED_EXTRACTOR, true, List.of(), false).filter()).isPresent();
        assertThat(ModuleInfo.of(ModuleKind.QUICK_SORT, false, List.of(batch("stone", 1)), true))
                .isEqualTo(new ModuleInfo("LogisticsModule", "quick_sort", false, Optional.empty(), Optional.empty()));
    }

    @Test
    void aMessageGoesToThatComputerAtEveryPipeInReach() {
        connect(OWN, NEAR, FAR);
        network.addRouter(ISLAND, new FakeNode(PipeKind.BASIC), BUILT);

        call(commands(PipeKind.BASIC), "sendMessage", 3.0, "hello");

        assertThat(events.queued).containsExactly(
                new Event(OWN, OptionalInt.of(3), "LP_MESSAGE", List.of(7, "hello")),
                new Event(NEAR, OptionalInt.of(3), "LP_MESSAGE", List.of(7, "hello")),
                new Event(FAR, OptionalInt.of(3), "LP_MESSAGE", List.of(7, "hello")));
    }

    @Test
    void aBroadcastGoesToEveryComputerInReach() {
        connect(OWN, NEAR);

        call(commands(PipeKind.BASIC), "sendBroadcast", "hello");

        assertThat(events.queued).containsExactly(
                new Event(OWN, OptionalInt.empty(), "LP_BROADCAST", List.of(7, "hello")),
                new Event(NEAR, OptionalInt.empty(), "LP_BROADCAST", List.of(7, "hello")));
    }

    @Test
    void aMessageNeedsTheIdOfAComputer() {
        assertThatThrownBy(() -> call(commands(PipeKind.BASIC), "sendMessage", "three", "hello"))
                .isInstanceOf(ComputerException.class).hasMessage("Argument 1 has to be a number");
    }

    @Test
    void messagesAreOnlyForTheComputerAtThePipe() {
        PipeCommands commands = commands(PipeKind.BASIC);

        assertThat(commands.names(true)).contains("sendMessage", "sendBroadcast", "getRouterUUID");
        assertThat(commands.names(false)).contains("getRouterUUID").doesNotContain("sendMessage", "sendBroadcast");
        assertThatThrownBy(() -> commands.call("sendBroadcast", REMOTE, List.of("hello")))
                .isInstanceOf(ComputerException.class).hasMessage("Permission denied");
        assertThat(events.queued).isEmpty();
    }

    @Test
    void anUnknownCommandIsAnError() {
        assertThatThrownBy(() -> call(commands(PipeKind.BASIC), "explode"))
                .isInstanceOf(ComputerException.class).hasMessage("No such method: explode");
    }

    @Test
    void helpListsTheCommandsTheCallerHas() {
        PipeCommands commands = commands(PipeKind.BASIC);

        String direct = (String) call(commands, "help")[0];
        String remote = (String) commands.call("help", REMOTE, List.of())[0];

        assertThat(direct).startsWith("Type: RoutedPipes:Normal\n")
                .contains("\ngetRouterUUID(): Returns the Router UUID")
                .contains("\nsendMessage(number computerId, any message): Sends a message");
        assertThat(remote).contains("getRouterUUID").doesNotContain("sendMessage");
    }

    @Test
    void onlyRequestPipesTakeRequests() {
        assertThat(commands(PipeKind.REQUEST).names(false)).contains("makeRequest", "getAvailableItems", "getCraftableItems", "getItemAmount");
        assertThat(commands(PipeKind.REQUEST_TABLE).names(false)).contains("makeRequest");
        assertThat(commands(PipeKind.BASIC).names(true)).doesNotContain("makeRequest", "reimport", "getChassieSize");
        assertThat(commands(PipeKind.CRAFTING).names(false)).contains("reimport", "getRecipe", "getOrders").doesNotContain("makeRequest");
        assertThat(commands(PipeKind.CHASSIS).names(false)).contains("getModuleInSlot", "getChassieSize").doesNotContain("reimport");
    }

    @Test
    void listsWhatCanBeHadAndWhatCanBeCrafted() {
        PipeCommands commands = requesting();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));

        assertThat(call(commands, "getAvailableItems")).containsExactly(List.of(item("log", 20)));
        assertThat(call(commands, "getCraftableItems")).containsExactly(List.of(Map.of("name", "planks")));
        assertThat(call(commands, "getItemAmount", "log")).containsExactly(20);
        assertThat(call(commands, "getItemAmount", Map.of("name", "planks"))).containsExactly(0);
    }

    @Test
    void aRequestMakesTheProviderSendAndCostsEnergy() {
        PipeCommands commands = requesting();
        provider.stock("log", 20);

        Object[] result = call(commands, "makeRequest", "log", 8.7);

        assertThat(result).containsExactly("DONE", List.of(item("log", 8)));
        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 8), OWN));
        assertThat(request.power).isEqualTo(1_000_000 - PipeCommands.REQUEST_ENERGY - 8 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM);
    }

    @Test
    void theAmountCanComeWithTheItem() {
        PipeCommands commands = requesting();
        provider.stock("log", 20);

        assertThat(call(commands, "makeRequest", Map.of("name", "log", "count", 3.0))).containsExactly("DONE", List.of(item("log", 3)));
        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 3), OWN));
    }

    @Test
    void aRequestThatCannotBeMetTellsWhatIsMissingAndSendsNothing() {
        PipeCommands commands = requesting();
        provider.stock("log", 20);

        assertThat(call(commands, "makeRequest", "log", 30.0)).containsExactly("MISSING", List.of(item("log", 10)));
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void withoutPowerNothingIsRequested() {
        PipeCommands commands = requesting();
        provider.stock("log", 20);
        List.of(request, provider, crafting).forEach(node -> node.power = 0);

        assertThat(call(commands, "makeRequest", "log", 1.0)).containsExactly("NO_POWER");
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void powerForTheRequestAloneIsNotEnough() {
        PipeCommands commands = requesting();
        provider.stock("log", 20);
        List.of(request, provider, crafting).forEach(node -> node.power = 0);
        request.power = PipeCommands.REQUEST_ENERGY;

        assertThat(call(commands, "makeRequest", "log", 1.0)).containsExactly("NO_POWER");
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void forcedCraftingLeavesTheStockOfTheItemAlone() {
        PipeCommands commands = requesting();
        provider.stock("log", 20).stock("planks", 10);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));

        assertThat(call(commands, "makeRequest", "planks", 4.0, true)).containsExactly("DONE", List.of(item("planks", 4)));

        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 1), FAR));
        assertThat(crafting.outputOrders.all()).extracting(OutputOrder::destination, OutputOrder::batch)
                .containsExactly(tuple(OWN, batch("planks", 4)));
    }

    @Test
    void withoutForcingStockComesFirst() {
        PipeCommands commands = requesting();
        provider.stock("log", 20).stock("planks", 10);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));

        call(commands, "makeRequest", "planks", 4.0, false);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("planks", 4), OWN));
    }

    @Test
    void forcedCraftingOfSomethingNobodyCraftsIsMissing() {
        PipeCommands commands = requesting();
        provider.stock("planks", 10);

        assertThat(call(commands, "makeRequest", "planks", 4.0, true)).containsExactly("MISSING", List.of(item("planks", 4)));
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void aRequestNeedsAnItemAndAnAmount() {
        PipeCommands commands = requesting();

        assertThatThrownBy(() -> call(commands, "makeRequest", null, 1.0)).hasMessage("Invalid ItemIdentifier");
        assertThatThrownBy(() -> call(commands, "makeRequest", "unknown:thing", 1.0)).hasMessage("Invalid ItemIdentifier");
        assertThatThrownBy(() -> call(commands, "makeRequest", Map.of("count", 1.0))).hasMessage("Invalid ItemIdentifier");
        assertThatThrownBy(() -> call(commands, "makeRequest", "log")).hasMessage("Argument 2 has to be a number");
        assertThatThrownBy(() -> call(commands, "makeRequest", "log", 0.5)).hasMessage("The amount has to be at least 1");
        assertThat(request.power).isEqualTo(1_000_000);
    }

    @Test
    void itemsWithDataAreToldApartByIt() {
        PipeCommands commands = requesting();
        TraitItem sharp = new TraitItem("sword", "", Set.of("sharp"), Set.of(), false);
        provider.contents.put(sharp, 1);
        provider.stock("sword", 5);

        Map<String, Object> listed = Map.of("name", "sword", "nbt", "sharp", "count", 1);
        assertThat(call(commands, "getAvailableItems")).containsExactly(List.of(listed, item("sword", 5)));
        assertThat(call(commands, "getItemAmount", listed)).containsExactly(1);

        assertThat(call(commands, "makeRequest", listed)[0]).isEqualTo("DONE");
        assertThat(provider.shipments).containsExactly(new Shipment(new ItemBatch(sharp, 1), OWN));
        assertThatThrownBy(() -> call(commands, "makeRequest", Map.of("name", "sword", "nbt", "blunt"), 1.0))
                .hasMessage("Invalid ItemIdentifier");
    }

    @Test
    void aCraftingPipeShowsItsRecipeAndWhatItOwes() {
        crafting.recipe = new Recipe(List.of(batch("log", 1), batch("stick", 2)), batch("planks", 4));
        crafting.outputOrders.add(new OutputOrder(NEAR, batch("planks", 8)));
        crafting.outputOrders.add(OutputOrder.extra(batch("bark", 1), 0));
        PipeCommands commands = commands(new FakeComputerPipe(OWN, crafting));

        assertThat(call(commands, "getRecipe")).containsExactly(Map.of("output", item("planks", 4),
                "inputs", List.of(item("log", 1), item("stick", 2)), "priority", 0));
        assertThat(call(commands, "getOrders")).containsExactly(List.of(
                Map.of("name", "planks", "count", 8, "destination", NEAR.value().toString()), item("bark", 1)));
    }

    @Test
    void aCraftingPipeWithoutARecipeHasNone() {
        PipeCommands commands = commands(new FakeComputerPipe(OWN, crafting));

        assertThat(call(commands, "getRecipe")).containsExactly((Object) null);
        assertThat(call(commands, "getOrders")).containsExactly(List.of());
    }

    @Test
    void theRecipeShowsPriorityAndByproduct() {
        crafting.crafts = List.of(new CraftingSetup(new Recipe(List.of(batch("log", 1)), batch("planks", 4)), List.of(), 0, 3,
                Optional.of(batch("bark", 1))));
        PipeCommands commands = commands(new FakeComputerPipe(OWN, crafting));

        assertThat(call(commands, "getRecipe")).containsExactly(Map.of("output", item("planks", 4),
                "inputs", List.of(item("log", 1)), "priority", 3, "byproduct", item("bark", 1)));
    }

    @Test
    void reimportTakesTheRecipeOverAgain() {
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, crafting);

        assertThat(call(commands(pipe), "reimport")).isEmpty();

        assertThat(pipe.recipeImports).isEqualTo(1);
    }

    @Test
    void aChassisShowsItsModulesBySlot() {
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, new FakeNode(PipeKind.CHASSIS));
        pipe.modules = List.of(Optional.of(ModuleInfo.of(ModuleKind.TERMINUS, true, List.of(batch("dirt", 1)), false)), Optional.empty());
        PipeCommands commands = commands(pipe);

        assertThat(call(commands, "getChassieSize")).containsExactly(2);
        assertThat(call(commands, "getModuleInSlot", 1.0)).containsExactly(Map.of("type", "Terminus Module", "kind", "terminus",
                "hasGui", true, "filter", List.of(item("dirt", 1))));
        assertThat(call(commands, "getModuleInSlot", 2.0)).containsExactly((Object) null);
        assertThatThrownBy(() -> call(commands, "getModuleInSlot", 3.0)).hasMessage("Slot out of Inventory");
        assertThatThrownBy(() -> call(commands, "getModuleInSlot", 0.0)).hasMessage("Slot out of Inventory");
    }

    @Test
    void aLockedPipeKeepsOutTheComputersItsStationDoesNotLetIn() {
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, request);
        pipe.allowedComputers = Set.of(3);
        PipeCommands commands = commands(pipe);

        for (String name : List.of("getRouterUUID", "hasLogisticsModule", "getLogisticsModule", "sendBroadcast", "makeRequest",
                "getAvailableItems", "getCraftableItems", "getItemAmount", "getPipeForUUID")) {
            assertThatThrownBy(() -> call(commands, name, "log", 1.0)).as(name)
                    .isInstanceOf(ComputerException.class).hasMessage("Permission denied");
        }
        assertThat(commands.call("getRouterUUID", Caller.attached(3), List.of())).containsExactly(OWN.value().toString());
        assertThat(events.queued).isEmpty();
    }

    @Test
    void craftingPipesAndChassisAreLockedToo() {
        FakeComputerPipe crafter = new FakeComputerPipe(OWN, crafting);
        crafter.allowedComputers = Set.of();
        FakeComputerPipe chassis = new FakeComputerPipe(OWN, new FakeNode(PipeKind.CHASSIS));
        chassis.allowedComputers = Set.of();

        for (String name : List.of("reimport", "getRecipe", "getOrders")) {
            assertThatThrownBy(() -> call(commands(crafter), name)).as(name).hasMessage("Permission denied");
        }
        for (String name : List.of("getModuleInSlot", "getChassieSize")) {
            assertThatThrownBy(() -> call(commands(chassis), name, 1.0)).as(name).hasMessage("Permission denied");
        }
        assertThat(crafter.recipeImports).isZero();
    }

    @Test
    void anyComputerMayAskWhetherItGetsIn() {
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, request);
        pipe.allowedComputers = Set.of(3);
        PipeCommands commands = commands(pipe);

        assertThat(call(commands, "canAccess")).containsExactly(false);
        assertThat(commands.call("canAccess", Caller.attached(3), List.of())).containsExactly(true);
        assertThat(call(commands, "getType")).containsExactly("RoutedPipes:Request");
        assertThat((String) call(commands, "help")[0]).contains("canAccess");
        // the station is asked once for each of the two, type and help do not bother it
        assertThat(pipe.accessChecks).isEqualTo(2);
    }

    @Test
    void aPipeThatIsNotLockedLetsEveryComputerIn() {
        assertThat(call(commands(PipeKind.BASIC), "canAccess")).containsExactly(true);
    }

    @Test
    void otherPipesAreOnlyHandedOutWithTheUpgrade() {
        connect(OWN, NEAR);
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, request);
        pipe.others.put(NEAR, new FakeComputerPipe(NEAR, provider));

        assertThatThrownBy(() -> call(commands(pipe), "getPipeForUUID", NEAR.value().toString()))
                .isInstanceOf(ComputerException.class).hasMessageStartingWith("Permission denied");
    }

    @Test
    void theUpgradeHandsOutThePipesOfTheNetwork() {
        connect(OWN, NEAR);
        network.addRouter(ISLAND, new FakeNode(PipeKind.BASIC), BUILT);
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, new FakeNode(PipeKind.BASIC));
        pipe.remoteControl = true;
        pipe.others.put(NEAR, new FakeComputerPipe(NEAR, request));
        pipe.others.put(ISLAND, new FakeComputerPipe(ISLAND, request));
        PipeCommands commands = commands(pipe);

        PipeCommands remote = (PipeCommands) call(commands, "getPipeForUUID", NEAR.value().toString())[0];

        assertThat(remote.type()).isEqualTo("RoutedPipes:Request");
        assertThat(remote.call("getRouterUUID", REMOTE, List.of())).containsExactly(NEAR.value().toString());
        assertThat(remote.names(false)).contains("makeRequest").doesNotContain("getPipeForUUID", "sendMessage", "sendBroadcast");
        // not connected, not loaded, and no id at all
        assertThat(call(commands, "getPipeForUUID", ISLAND.value().toString())).containsExactly((Object) null);
        assertThat(call(commands, "getPipeForUUID", FAR.value().toString())).containsExactly((Object) null);
        assertThat(call(commands, "getPipeForUUID", "the one over there")).containsExactly((Object) null);
        assertThat(call(commands, "getPipeForUUID")).containsExactly((Object) null);
    }

    @Test
    void aPipeReachedFromAfarAsksItsOwnStationAboutNoComputerInParticular() {
        connect(OWN, NEAR);
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, new FakeNode(PipeKind.BASIC));
        pipe.remoteControl = true;
        FakeComputerPipe locked = new FakeComputerPipe(NEAR, request);
        locked.allowedComputers = Set.of(7);
        pipe.others.put(NEAR, locked);
        PipeCommands remote = (PipeCommands) call(commands(pipe), "getPipeForUUID", NEAR.value().toString())[0];

        assertThatThrownBy(() -> remote.call("getRouterUUID", REMOTE, List.of())).hasMessage("Permission denied");
        assertThat(remote.call("canAccess", REMOTE, List.of())).containsExactly(false);

        locked.allowedComputers = Set.of(-1);
        assertThat(remote.call("getRouterUUID", REMOTE, List.of())).containsExactly(NEAR.value().toString());
    }

    @Test
    void theUpgradeOfAPipeReachedFromAfarIsOfNoUse() {
        connect(OWN, NEAR);
        FakeComputerPipe pipe = new FakeComputerPipe(OWN, new FakeNode(PipeKind.BASIC));
        pipe.remoteControl = true;
        FakeComputerPipe other = new FakeComputerPipe(NEAR, request);
        other.remoteControl = true;
        pipe.others.put(NEAR, other);
        PipeCommands remote = (PipeCommands) call(commands(pipe), "getPipeForUUID", NEAR.value().toString())[0];

        assertThatThrownBy(() -> remote.call("getPipeForUUID", REMOTE, List.of(OWN.value().toString()))).hasMessage("Permission denied");
    }
}
