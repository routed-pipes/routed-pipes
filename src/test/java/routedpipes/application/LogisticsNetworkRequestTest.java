package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.InventoryRef;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.testing.FakeNode;
import routedpipes.testing.FakeNode.Shipment;
import routedpipes.testing.Fixtures.TestItem;
import routedpipes.domain.logistics.SupplyMode;
import routedpipes.domain.crafting.CraftingSetup;
import routedpipes.domain.logistics.Supply;
import routedpipes.domain.crafting.Cleanup;
import java.util.Optional;
import java.util.Set;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.logistics.PassiveSupplier;
import org.junit.jupiter.api.Test;

class LogisticsNetworkRequestTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId CRAFTING = router(3);
    private static final RouterId SATELLITE = router(4);
    private static final RouterId SUPPLIER = router(5);
    private static final RouterId ISLAND = router(9);

    // long after the network was put together
    private static final long NOW = 100;

    // the network was put together well before the tests start ticking it
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode crafting = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode satellite = new FakeNode(PipeKind.SATELLITE);
    private final FakeNode supplier = new FakeNode(PipeKind.SUPPLIER);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    // all five in a row, one apart, linked both ways
    private void connect() {
        List<RouterId> ids = List.of(REQUEST, PROVIDER, CRAFTING, SATELLITE, SUPPLIER);
        List<FakeNode> nodes = List.of(request, provider, crafting, satellite, supplier);
        supplier.inventories.add(Side.UP);
        for (int i = 0; i < ids.size(); i++) {
            network.addRouter(ids.get(i), nodes.get(i), BUILT);
        }
        for (int i = 0; i < ids.size(); i++) {
            List<Link> links = new java.util.ArrayList<>();
            if (i > 0) {
                links.add(new Link(ids.get(i - 1), Side.WEST, 1));
            }
            if (i < ids.size() - 1) {
                links.add(new Link(ids.get(i + 1), Side.EAST, 1));
            }
            network.updateLinks(ids.get(i), links, BUILT);
        }
    }

    @Test
    void listsProvidedAndCraftableItems() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));

        assertThat(network.available(REQUEST)).containsOnly(Map.entry(item("log"), 20), Map.entry(item("planks"), 0));
    }

    @Test
    void doesNotListWhatOnlyNonProvidersHold() {
        connect();
        supplier.stock("stone", 20);

        assertThat(network.available(REQUEST)).isEmpty();
    }

    @Test
    void doesNotListStockOfUnreachableProviders() {
        connect();
        network.addRouter(ISLAND, new FakeNode(PipeKind.PROVIDER).stock("gold", 5), BUILT);

        assertThat(network.available(REQUEST)).isEmpty();
    }

    @Test
    void requestMakesTheProviderSendItems() {
        connect();
        provider.stock("log", 20);

        RequestOutcome plan = network.request(REQUEST, batch("log", 8), 0);

        assertThat(plan.started()).isTrue();
        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 8), REQUEST));
        assertThat(provider.contents).containsEntry(item("log"), 12);
    }

    @Test
    void incompleteRequestTouchesNothing() {
        connect();
        provider.stock("log", 5);

        RequestOutcome plan = network.request(REQUEST, batch("log", 8), 0);

        assertThat(plan.missing()).containsOnly(Map.entry(item("log"), 3));
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void craftingRequestShipsIngredientsAndQueuesTheOutput() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));

        RequestOutcome plan = network.request(REQUEST, batch("planks", 8), 0);

        assertThat(plan.started()).isTrue();
        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 2), CRAFTING));
        assertThat(crafting.outputOrders.all()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("placed", "request")
                .containsExactly(new OutputOrder(REQUEST, batch("planks", 8)));
    }

    @Test
    void craftingPipeShipsOutputAsItAppears() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.request(REQUEST, batch("planks", 8), 0);

        network.tick(CRAFTING, 20);
        assertThat(crafting.shipments).isEmpty();

        crafting.stock("planks", 6);
        network.tick(CRAFTING, 40);
        assertThat(crafting.shipments).containsExactly(new Shipment(batch("planks", 6), REQUEST));
        assertThat(crafting.outputOrders.all()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("placed", "request")
                .containsExactly(new OutputOrder(REQUEST, batch("planks", 2), 40));

        crafting.stock("planks", 6);
        network.tick(CRAFTING, 60);
        assertThat(crafting.shipments).hasSize(2).last().isEqualTo(new Shipment(batch("planks", 2), REQUEST));
        assertThat(crafting.outputOrders.isEmpty()).isTrue();
    }

    @Test
    void onlyTheSatellitePartOfTheRecipeGoesToTheSatellite() {
        connect();
        provider.stock("ore", 20).stock("coal", 20);
        crafting.recipe = new Recipe(List.of(batch("coal", 1), batch("ore", 1)), batch("ingot", 1));
        crafting.satelliteInputs.add(batch("ore", 1));
        crafting.satelliteName = "7";
        satellite.satelliteName = "7";

        network.request(REQUEST, batch("ingot", 3), 0);

        assertThat(provider.shipments).containsExactlyInAnyOrder(
                new Shipment(batch("ore", 3), SATELLITE), new Shipment(batch("coal", 3), CRAFTING));
    }

    @Test
    void withoutASatelliteEverythingGoesToTheCraftingPipe() {
        connect();
        provider.stock("ore", 20);
        crafting.recipe = new Recipe(List.of(batch("ore", 1)), batch("ingot", 1));
        crafting.satelliteInputs.add(batch("ore", 1));

        network.request(REQUEST, batch("ingot", 3), 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("ore", 3), CRAFTING));
    }

    @Test
    void crafterWithoutItsSatelliteIsNotUsed() {
        connect();
        provider.stock("ore", 20);
        crafting.recipe = new Recipe(List.of(batch("ore", 1)), batch("ingot", 1));
        crafting.satelliteName = "7";

        assertThat(network.available(REQUEST)).containsOnlyKeys(item("ore"));
        assertThat(network.request(REQUEST, batch("ingot", 3), 0).started()).isFalse();
    }

    @Test
    void requestedItemPopsOutAtAPipeWithoutInventory() {
        connect();
        TravellingItem item = new TravellingItem(batch("log", 8), Side.EAST, REQUEST);

        assertThat(network.chooseExit(REQUEST, item, NOW)).isEmpty();
        assertThat(item.destination()).contains(REQUEST);
    }

    @Test
    void requestedItemGoesIntoTheInventoryNextToThePipe() {
        connect();
        request.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("log", 8), Side.EAST, REQUEST);

        assertThat(network.chooseExit(REQUEST, item, NOW)).contains(Side.UP);
    }

    @Test
    void onlyBasicPipesTakeUnrequestedItems() {
        connect();
        provider.inventories.add(Side.UP);
        supplier.inventories.add(Side.UP);
        crafting.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("log", 8), Side.DOWN, null);

        assertThat(network.chooseExit(REQUEST, item, NOW)).isEmpty();
    }

    @Test
    void supplierOrdersWhatItsInventoryLacks() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);
        supplier.stock("coal", 6);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 10), SUPPLIER));
    }

    @Test
    void supplierDoesNotReorderWhatIsAlreadyOnItsWay() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(SUPPLIER, 0);
        network.tick(SUPPLIER, 20);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 16), SUPPLIER));
    }

    @Test
    void supplierReordersOnceStockIsDownToHalf() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);
        supplier.stock("coal", 9);

        network.tick(SUPPLIER, 0);
        assertThat(provider.shipments).isEmpty();

        supplier.stock("coal", 8);
        network.tick(SUPPLIER, 20);
        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 8), SUPPLIER));
    }

    @Test
    void supplierOrdersNothingWhileItsInventoryIsFull() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);
        supplier.room = false;

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void supplierTakesWhatIsInStockWhenCraftingTheRestIsNotPossible() {
        connect();
        provider.stock("coal", 5);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("coal", 1));
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 5), SUPPLIER));
    }

    @Test
    void supplierNeverOrdersFromItsOwnInventory() {
        connect();
        provider.stock("coal", 64);
        provider.inventoryRefs.put(Side.UP, new InventoryRef(7));
        supplier.inventoryRefs.put(Side.UP, new InventoryRef(7));
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void twoProvidersOnOneInventoryCountItOnce() {
        connect();
        provider.stock("log", 20);
        provider.inventoryRefs.put(Side.UP, new InventoryRef(7));
        FakeNode second = new FakeNode(PipeKind.PROVIDER).stock("log", 20);
        second.inventoryRefs.put(Side.UP, new InventoryRef(7));
        network.addRouter(ISLAND, second, BUILT);
        network.updateLinks(ISLAND, List.of(new Link(REQUEST, Side.EAST, 1)), BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 1)), BUILT);

        assertThat(network.available(REQUEST)).containsEntry(item("log"), 20);
        assertThat(network.request(REQUEST, batch("log", 30), 0).started()).isFalse();
    }

    @Test
    void orderedItemWaitsInThePipeWhileItsDestinationIsFull() {
        connect();
        supplier.room = false;
        TravellingItem item = new TravellingItem(batch("coal", 8), Side.WEST, SUPPLIER);
        item.waitForExit();

        assertThat(network.chooseExit(SUPPLIER, item, NOW)).isEmpty();
        assertThat(item.destination()).contains(SUPPLIER);
        assertThat(item.waited()).isZero();

        supplier.room = true;
        assertThat(network.chooseExit(SUPPLIER, item, NOW)).contains(Side.UP);
    }

    @Test
    void finishedItemsLeftInACraftingInventoryCanBeOrdered() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        crafting.stock("planks", 6);

        assertThat(network.available(REQUEST)).containsEntry(item("planks"), 6);
        assertThat(network.request(REQUEST, batch("planks", 5), 0).started()).isTrue();

        assertThat(crafting.shipments).containsExactly(new Shipment(batch("planks", 5), REQUEST));
        assertThat(provider.shipments).isEmpty();
        assertThat(crafting.outputOrders.isEmpty()).isTrue();
    }

    @Test
    void itemsOwedToAnEarlierOrderAreNotOfferedAgain() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.request(REQUEST, batch("planks", 8), 0);
        crafting.stock("planks", 8);

        assertThat(network.available(REQUEST)).containsEntry(item("planks"), 0);
    }

    @Test
    void theCrafterWithTheLeastToDoGetsTheNextOrder() {
        connect();
        provider.stock("log", 64);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        FakeNode second = new FakeNode(PipeKind.CRAFTING);
        second.recipe = crafting.recipe;
        network.addRouter(ISLAND, second, BUILT);
        network.updateLinks(ISLAND, List.of(new Link(REQUEST, Side.EAST, 9)), BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 9)), BUILT);

        network.request(REQUEST, batch("planks", 8), 0);
        network.request(REQUEST, batch("planks", 4), 0);

        assertThat(crafting.outputOrders.owedInTotal()).isEqualTo(8);
        assertThat(second.outputOrders.owedInTotal()).isEqualTo(4);
    }

    @Test
    void craftingOrderThatNeverGetsItsOutputIsGivenUp() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.request(REQUEST, batch("planks", 8), 0);

        network.tick(CRAFTING, LogisticsNetwork.CRAFT_TIMEOUT - 1);
        assertThat(crafting.outputOrders.all()).hasSize(1);

        network.tick(CRAFTING, LogisticsNetwork.CRAFT_TIMEOUT);
        assertThat(crafting.outputOrders.isEmpty()).isTrue();
    }

    @Test
    void supplierReordersItemsThatGotLost() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);
        network.tick(SUPPLIER, 0);

        network.tick(SUPPLIER, LogisticsNetwork.PROVISION_TIMEOUT);

        assertThat(provider.shipments).hasSize(2);
    }

    @Test
    void supplierSettlesForWhatIsThere() {
        connect();
        provider.stock("coal", 5);
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 5), SUPPLIER));
    }

    @Test
    void supplierDoesNothingWhenStocked() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);
        supplier.stock("coal", 16);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).isEmpty();
    }

    private void noPower() {
        for (FakeNode node : List.of(request, provider, crafting, satellite, supplier)) {
            node.power = 0;
        }
    }

    @Test
    void requestPaysForEveryItemItMoves() {
        connect();
        noPower();
        crafting.power = 500;
        provider.stock("log", 20);

        assertThat(network.request(REQUEST, batch("log", 8), 0).started()).isTrue();

        assertThat(crafting.power).isEqualTo(500 - 8 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM);
    }

    @Test
    void requestWithoutEnoughPowerMovesNothingAndTakesNoEnergy() {
        connect();
        noPower();
        provider.power = 8 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM - 1;
        provider.stock("log", 20);

        RequestOutcome outcome = network.request(REQUEST, batch("log", 8), 0);

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.powered()).isFalse();
        assertThat(outcome.missing()).isEmpty();
        assertThat(provider.shipments).isEmpty();
        assertThat(provider.power).isEqualTo(8 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM - 1);
    }

    @Test
    void powerIsPooledAcrossTheJunctionsOfTheNetwork() {
        connect();
        noPower();
        request.power = 30;
        supplier.power = 50;
        provider.stock("log", 20);

        assertThat(network.request(REQUEST, batch("log", 8), 0).started()).isTrue();

        assertThat(request.power + supplier.power).isEqualTo(80 - 8 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM);
    }

    @Test
    void junctionsOfUnreachableRoutersDoNotHelp() {
        connect();
        noPower();
        FakeNode island = new FakeNode(PipeKind.BASIC);
        network.addRouter(ISLAND, island, BUILT);
        provider.stock("log", 20);

        assertThat(network.request(REQUEST, batch("log", 8), 0).started()).isFalse();
        assertThat(island.power).isEqualTo(1_000_000);
    }

    @Test
    void missingItemsAreReportedEvenWithoutPower() {
        connect();
        noPower();

        RequestOutcome outcome = network.request(REQUEST, batch("log", 8), 0);

        assertThat(outcome.powered()).isTrue();
        assertThat(outcome.missing()).containsOnlyKeys(item("log"));
    }

    @Test
    void craftingPipePaysOnlyForOutputThatIsReady() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.request(REQUEST, batch("planks", 8), 0);
        noPower();
        crafting.power = 100;

        network.tick(CRAFTING, 20);
        assertThat(crafting.power).isEqualTo(100);

        crafting.stock("planks", 3);
        network.tick(CRAFTING, 40);
        assertThat(crafting.shipments).containsExactly(new Shipment(batch("planks", 3), REQUEST));
        assertThat(crafting.power).isEqualTo(100 - 3 * LogisticsNetwork.ENERGY_PER_CRAFTED_ITEM);
    }

    @Test
    void craftingPipeKeepsOutputWhenThereIsNoPower() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.request(REQUEST, batch("planks", 8), 0);
        noPower();
        crafting.stock("planks", 8);

        network.tick(CRAFTING, 40);

        assertThat(crafting.shipments).isEmpty();
        assertThat(crafting.outputOrders.all()).hasSize(1);
    }

    @Test
    void supplierWaitsForPower() {
        connect();
        noPower();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(SUPPLIER, 0);
        assertThat(provider.shipments).isEmpty();

        request.power = 1000;
        network.tick(SUPPLIER, 20);
        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 16), SUPPLIER));
    }

    @Test
    void sortingNeedsPowerAndChargesTheSinkPriceForTheStack() {
        connect();
        noPower();
        FakeNode sink = new FakeNode(PipeKind.BASIC);
        sink.power = 0;
        sink.inventories.add(Side.UP);
        network.addRouter(ISLAND, sink, BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 1)), BUILT);
        TravellingItem item = new TravellingItem(batch("log", 8), Side.DOWN, null);

        assertThat(network.chooseExit(REQUEST, item, NOW)).isEmpty();
        assertThat(item.destination()).isEmpty();

        provider.power = 10;
        assertThat(network.chooseExit(REQUEST, item, NOW)).contains(Side.WEST);
        assertThat(provider.power).isEqualTo(8);
    }

    @Test
    void supplierPaysForTheOrderOnTopOfTheItems() {
        connect();
        noPower();
        request.power = 1000;
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(SUPPLIER, 0);

        assertThat(request.power).isEqualTo(1000 - LogisticsNetwork.ENERGY_PER_SUPPLY_ORDER - 16 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM);
    }

    @Test
    void passingAnItemOnToADestinationItAlreadyHasCostsNothing() {
        connect();
        noPower();
        TravellingItem item = new TravellingItem(batch("log", 8), Side.WEST, SUPPLIER);

        assertThat(network.chooseExit(CRAFTING, item, NOW)).contains(Side.EAST);
    }

    @Test
    void upkeepRunsAtItsOwnPace() {
        connect();
        provider.stock("log", 20).stock("coal", 64);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.request(REQUEST, batch("planks", 8), 0);
        crafting.stock("planks", 8);
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(CRAFTING, LogisticsNetwork.CRAFT_INTERVAL + 1);
        network.tick(SUPPLIER, LogisticsNetwork.SUPPLY_INTERVAL + 1);
        assertThat(crafting.shipments).isEmpty();
        assertThat(provider.shipments).hasSize(1);

        network.tick(CRAFTING, 2 * LogisticsNetwork.CRAFT_INTERVAL);
        network.tick(SUPPLIER, 2 * LogisticsNetwork.SUPPLY_INTERVAL);
        assertThat(crafting.shipments).hasSize(1);
        assertThat(provider.shipments).hasSize(2);
    }

    @Test
    void statusCountsReachableRoutersAndTheirPooledEnergy() {
        connect();
        noPower();
        request.power = 30;
        supplier.power = 50;
        FakeNode island = new FakeNode(PipeKind.BASIC);
        network.addRouter(ISLAND, island, BUILT);

        assertThat(network.status(REQUEST)).isEqualTo(new RouterStatus(4, 80));
        assertThat(network.status(ISLAND)).isEqualTo(new RouterStatus(0, 1_000_000));
        assertThat(request.power + supplier.power).isEqualTo(80);
    }

    @Test
    void onlyWhatAProviderHandsOutCanBeOrdered() {
        connect();
        provider.stock("coal", 64);
        provider.provided = Map.of(item("coal"), 60);

        assertThat(network.available(REQUEST)).containsEntry(item("coal"), 60);
        assertThat(network.request(REQUEST, batch("coal", 64), NOW).plan().complete()).isFalse();
        assertThat(network.request(REQUEST, batch("coal", 60), NOW).plan().complete()).isTrue();
        assertThat(provider.contents).containsEntry(item("coal"), 4);
    }

    @Test
    void fullSupplierOrdersOnlyWhenAllOfItCanBeHad() {
        connect();
        provider.stock("coal", 4);
        supplier.supplyMode = SupplyMode.FULL;
        supplier.supplyTargets.put(item("coal"), 16);

        network.tick(SUPPLIER, 0);
        assertThat(provider.shipments).isEmpty();

        provider.stock("coal", 16);
        network.tick(SUPPLIER, 20);
        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 16), SUPPLIER));
    }

    @Test
    void partialSupplierTopsUpAsSoonAsOneIsMissing() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyMode = SupplyMode.PARTIAL;
        supplier.supplyTargets.put(item("coal"), 16);
        supplier.stock("coal", 15);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 1), SUPPLIER));
    }

    @Test
    void bulk100SupplierWaitsUntilItIsEmpty() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyMode = SupplyMode.BULK100;
        supplier.supplyTargets.put(item("coal"), 16);
        supplier.stock("coal", 1);

        network.tick(SUPPLIER, 0);
        assertThat(provider.shipments).isEmpty();

        supplier.contents.clear();
        network.tick(SUPPLIER, 20);
        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 16), SUPPLIER));
    }

    @Test
    void infiniteSupplierFillsTheRoomItHas() {
        connect();
        provider.stock("coal", 200);
        supplier.supplyMode = SupplyMode.INFINITE;
        supplier.supplyTargets.put(item("coal"), 1);
        supplier.stock("coal", 30);
        supplier.roomLeft = 40;

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 40), SUPPLIER));
    }

    @Test
    void theCrafterWithTheHigherPriorityIsUsedEvenWhenItIsBusier() {
        connect();
        provider.stock("log", 64);
        Recipe recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        crafting.crafts = List.of(new CraftingSetup(recipe, List.of(), 0, 0));
        FakeNode preferred = new FakeNode(PipeKind.CRAFTING);
        preferred.crafts = List.of(new CraftingSetup(recipe, List.of(), 0, 5));
        network.addRouter(ISLAND, preferred, BUILT);
        network.updateLinks(ISLAND, List.of(new Link(REQUEST, Side.EAST, 9)), BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 9)), BUILT);

        network.request(REQUEST, batch("planks", 8), 0);
        network.request(REQUEST, batch("planks", 4), 0);

        assertThat(preferred.outputOrders.owedInTotal()).isEqualTo(12);
        assertThat(crafting.outputOrders.isEmpty()).isTrue();
    }

    @Test
    void aPipeWithSeveralRecipesCraftsAnyOfThem() {
        connect();
        provider.stock("log", 64);
        crafting.crafts = List.of(
                new CraftingSetup(new Recipe(List.of(batch("log", 1)), batch("planks", 4)), List.of(), 0, 0),
                new CraftingSetup(new Recipe(List.of(batch("planks", 2)), batch("stick", 4)), List.of(), 0, 0));

        assertThat(network.available(REQUEST)).containsKeys(item("planks"), item("stick"));
        assertThat(network.request(REQUEST, batch("stick", 4), 0).plan().complete()).isTrue();
        assertThat(crafting.outputOrders.owed(item("stick"))).isEqualTo(4);
    }

    @Test
    void aPipeKeepsUpEveryStockItHas() {
        connect();
        provider.stock("coal", 64);
        provider.stock("iron", 64);
        request.inventories.add(Side.UP);
        request.supplies = List.of(new Supply(Map.of(item("coal"), 8), SupplyMode.PARTIAL),
                new Supply(Map.of(item("iron"), 4), SupplyMode.PARTIAL));

        network.tick(REQUEST, 0);

        assertThat(provider.shipments).containsExactlyInAnyOrder(new Shipment(batch("coal", 8), REQUEST),
                new Shipment(batch("iron", 4), REQUEST));
    }

    @Test
    void aCrafterOrdersALostIngredientAgain() {
        connect();
        provider.stock("log", 64);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.request(REQUEST, batch("planks", 8), 0);

        network.itemLost(CRAFTING, batch("log", 2), 5);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 2), CRAFTING), new Shipment(batch("log", 2), CRAFTING));
    }

    @Test
    void aLostItemForARequestPipeIsNotOrderedAgain() {
        connect();
        provider.stock("log", 64);
        network.request(REQUEST, batch("log", 4), 0);

        network.itemLost(REQUEST, batch("log", 4), 5);

        assertThat(provider.shipments).hasSize(1);
    }

    @Test
    void supplierTakesTheRunsThatCanBeCraftedWhenNotAllCan() {
        connect();
        provider.stock("log", 2);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        supplier.supplyMode = SupplyMode.PARTIAL;
        supplier.supplyTargets.put(item("planks"), 20);

        network.tick(SUPPLIER, 0);

        assertThat(crafting.outputOrders.owed(item("planks"))).isEqualTo(8);
    }

    @Test
    void whatElseComesOutOfTheMachineIsSentOffWithoutADestination() {
        connect();
        provider.stock("ore", 64);
        Recipe smelting = new Recipe(List.of(batch("ore", 1)), batch("ingot", 2));
        crafting.crafts = List.of(new CraftingSetup(smelting, List.of(), 0, 0).withByproduct(Optional.of(batch("slag", 1))));
        crafting.inventories.add(Side.UP);

        network.request(REQUEST, batch("ingot", 6), 0);
        assertThat(crafting.outputOrders.owed(item("slag"))).isEqualTo(3);

        crafting.contents.put(item("slag"), 3);
        network.tick(CRAFTING, 5);

        assertThat(crafting.shipments).containsExactly(new Shipment(batch("slag", 3), null));
        assertThat(crafting.lastMode).isEqualTo(TransportMode.UNCLAIMED);
    }

    @Test
    void anIdleCrafterClearsItsMachine() {
        connect();
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        crafting.cleanup = new Cleanup(Set.of(item("planks")), true);
        crafting.stock("planks", 4);
        crafting.stock("dirt", 100);

        network.tick(CRAFTING, 5);

        assertThat(crafting.shipments).containsExactly(new Shipment(batch("dirt", 64), null));
    }

    @Test
    void aBusyCrafterLeavesItsMachineAlone() {
        connect();
        provider.stock("log", 64);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        crafting.cleanup = new Cleanup(Set.of(), true);
        crafting.stock("dirt", 100);
        network.request(REQUEST, batch("planks", 4), 0);

        network.tick(CRAFTING, 5);

        assertThat(crafting.shipments).isEmpty();
    }

    @Test
    void cleanupListSaysWhatStaysOrWhatGoes() {
        assertThat(new Cleanup(Set.of(item("planks")), true).removes(item("planks"))).isFalse();
        assertThat(new Cleanup(Set.of(item("planks")), true).removes(item("dirt"))).isTrue();
        assertThat(new Cleanup(Set.of(item("planks")), false).removes(item("planks"))).isTrue();
        assertThat(new Cleanup(Set.of(item("planks")), false).removes(item("dirt"))).isFalse();
    }

    @Test
    void aCrafterWithSeveralSatellitesNeedsAllOfThem() {
        connect();
        provider.stock("ore", 8);
        provider.stock("coal", 8);
        satellite.satelliteName = "3";
        Recipe smelting = new Recipe(List.of(batch("ore", 1), batch("coal", 1)), batch("ingot", 1));
        crafting.crafts = List.of(new CraftingSetup(smelting, Map.of("3", List.of(batch("ore", 1)), "4", List.of(batch("coal", 1))),
                0, Optional.empty()));

        assertThat(network.available(REQUEST)).doesNotContainKey(item("ingot"));

        crafting.crafts = List.of(new CraftingSetup(smelting, Map.of("3", List.of(batch("ore", 1))), 0, Optional.empty()));
        assertThat(network.request(REQUEST, batch("ingot", 2), 0).started()).isTrue();
        assertThat(provider.shipments).containsExactlyInAnyOrder(new Shipment(batch("ore", 2), SATELLITE),
                new Shipment(batch("coal", 2), CRAFTING));
    }

    @Test
    void aSatelliteIsFoundByTheNameAPlayerGaveIt() {
        connect();
        provider.stock("ore", 8);
        satellite.satelliteName = "Furnace top";
        crafting.recipe = new Recipe(List.of(batch("ore", 1)), batch("ingot", 1));
        crafting.satelliteInputs.add(batch("ore", 1));
        crafting.satelliteName = "Furnace top";

        network.request(REQUEST, batch("ingot", 2), 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("ore", 2), SATELLITE));
    }

    @Test
    void listsTheNamedSatellitesOfAKindWithinReachNearestFirst() {
        connect();
        satellite.satelliteName = "Far";
        FakeNode near = new FakeNode(PipeKind.SATELLITE);
        near.satelliteName = "Near";
        FakeNode unnamed = new FakeNode(PipeKind.SATELLITE);
        FakeNode tank = new FakeNode(PipeKind.FLUID_SATELLITE);
        tank.satelliteName = "Tank";
        FakeNode island = new FakeNode(PipeKind.SATELLITE);
        island.satelliteName = "Island";
        network.addRouter(ISLAND, island, BUILT);
        List<FakeNode> extra = List.of(near, unnamed, tank);
        for (int i = 0; i < extra.size(); i++) {
            // all hang off the provider, which is right next to the request pipe
            network.addRouter(router(20 + i), extra.get(i), BUILT);
            network.updateLinks(router(20 + i), List.of(new Link(PROVIDER, Side.DOWN, 1)), BUILT);
        }
        network.updateLinks(PROVIDER, List.of(new Link(REQUEST, Side.WEST, 1), new Link(CRAFTING, Side.EAST, 1),
                new Link(router(20), Side.UP, 1), new Link(router(21), Side.NORTH, 1), new Link(router(22), Side.SOUTH, 1)), BUILT);

        assertThat(network.satelliteNames(REQUEST, PipeKind.SATELLITE)).containsExactly("Near", "Far");
        assertThat(network.satelliteNames(REQUEST, PipeKind.FLUID_SATELLITE)).containsExactly("Tank");
        assertThat(network.satelliteNames(SATELLITE, PipeKind.SATELLITE)).containsExactly("Near");
    }

    @Test
    void whatAProviderCannotHandOutAfterAllIsOrderedFromAnotherOne() {
        connect();
        provider.stock("coal", 20);
        // the plan sees 20 but only 8 can really be taken out
        provider.provided = Map.of(item("coal"), 20);
        provider.contents.put(item("coal"), 8);
        FakeNode second = new FakeNode(PipeKind.PROVIDER);
        second.stock("coal", 64);
        network.addRouter(ISLAND, second, BUILT);
        network.updateLinks(ISLAND, List.of(new Link(REQUEST, Side.WEST, 9)), BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 9)), BUILT);

        assertThat(network.request(REQUEST, batch("coal", 20), 0).started()).isTrue();

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 8), REQUEST));
        assertThat(second.shipments).containsExactly(new Shipment(batch("coal", 12), REQUEST));
    }

    @Test
    void whatIsOrderedFromAnotherProviderIsPaidForOnce() {
        connect();
        provider.provided = Map.of(item("coal"), 20);
        provider.contents.put(item("coal"), 8);
        FakeNode second = new FakeNode(PipeKind.PROVIDER);
        second.stock("coal", 64);
        network.addRouter(ISLAND, second, BUILT);
        network.updateLinks(ISLAND, List.of(new Link(REQUEST, Side.WEST, 9)), BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 9)), BUILT);
        List<FakeNode> all = List.of(request, provider, crafting, satellite, supplier, second);
        int before = all.stream().mapToInt(node -> node.power).sum();

        assertThat(network.request(REQUEST, batch("coal", 20), 0).started()).isTrue();

        assertThat(before - all.stream().mapToInt(node -> node.power).sum()).isEqualTo(20 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM);
    }

    @Test
    void aSupplierCountsWhatItsInventoryHoldsEvenWhereNothingCanBeTakenOut() {
        connect();
        provider.stock("ore", 64);
        supplier.supplyMode = SupplyMode.PARTIAL;
        supplier.supplyTargets.put(item("ore"), 16);
        supplier.held = Map.of(item("ore"), 32);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).isEmpty();
    }
}
