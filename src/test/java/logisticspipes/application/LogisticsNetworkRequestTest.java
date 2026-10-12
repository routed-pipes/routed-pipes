package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import logisticspipes.testing.Fixtures.TestItem;
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
            network.addRouter(ids.get(i), nodes.get(i), 0);
        }
        for (int i = 0; i < ids.size(); i++) {
            List<Link> links = new java.util.ArrayList<>();
            if (i > 0) {
                links.add(new Link(ids.get(i - 1), Side.WEST, 1));
            }
            if (i < ids.size() - 1) {
                links.add(new Link(ids.get(i + 1), Side.EAST, 1));
            }
            network.updateLinks(ids.get(i), links, 0);
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
        network.addRouter(ISLAND, new FakeNode(PipeKind.PROVIDER).stock("gold", 5), 0);

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
        assertThat(crafting.outputOrders.all()).containsExactly(new OutputOrder(REQUEST, batch("planks", 8)));
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
        assertThat(crafting.outputOrders.all()).containsExactly(new OutputOrder(REQUEST, batch("planks", 2), 40));

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
        crafting.satelliteId = 7;
        satellite.satelliteId = 7;

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
        crafting.satelliteId = 7;

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
        network.addRouter(ISLAND, second, 0);
        network.updateLinks(ISLAND, List.of(new Link(REQUEST, Side.EAST, 1)), 0);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 1)), 0);

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
        network.addRouter(ISLAND, second, 0);
        network.updateLinks(ISLAND, List.of(new Link(REQUEST, Side.EAST, 9)), 0);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 9)), 0);

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
        network.addRouter(ISLAND, island, 0);
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
        network.addRouter(ISLAND, sink, 0);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(ISLAND, Side.WEST, 1)), 0);
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
        network.addRouter(ISLAND, island, 0);

        assertThat(network.status(REQUEST)).isEqualTo(new RouterStatus(4, 80));
        assertThat(network.status(ISLAND)).isEqualTo(new RouterStatus(0, 1_000_000));
        assertThat(request.power + supplier.power).isEqualTo(80);
    }
}
