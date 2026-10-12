package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.request.RequestPlan;
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

        RequestPlan plan = network.request(REQUEST, batch("log", 8), 0);

        assertThat(plan.complete()).isTrue();
        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 8), REQUEST));
        assertThat(provider.contents).containsEntry(item("log"), 12);
    }

    @Test
    void incompleteRequestTouchesNothing() {
        connect();
        provider.stock("log", 5);

        RequestPlan plan = network.request(REQUEST, batch("log", 8), 0);

        assertThat(plan.missing()).containsOnly(Map.entry(item("log"), 3));
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void craftingRequestShipsIngredientsAndQueuesTheOutput() {
        connect();
        provider.stock("log", 20);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));

        RequestPlan plan = network.request(REQUEST, batch("planks", 8), 0);

        assertThat(plan.complete()).isTrue();
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
        assertThat(crafting.outputOrders.all()).containsExactly(new OutputOrder(REQUEST, batch("planks", 2)));

        crafting.stock("planks", 6);
        network.tick(CRAFTING, 60);
        assertThat(crafting.shipments).hasSize(2).last().isEqualTo(new Shipment(batch("planks", 2), REQUEST));
        assertThat(crafting.outputOrders.isEmpty()).isTrue();
    }

    @Test
    void ingredientsGoToTheChosenSatellite() {
        connect();
        provider.stock("ore", 20);
        crafting.recipe = new Recipe(List.of(batch("ore", 1)), batch("ingot", 1));
        crafting.satelliteId = 7;
        satellite.satelliteId = 7;

        network.request(REQUEST, batch("ingot", 3), 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("ore", 3), SATELLITE));
    }

    @Test
    void crafterWithoutItsSatelliteIsNotUsed() {
        connect();
        provider.stock("ore", 20);
        crafting.recipe = new Recipe(List.of(batch("ore", 1)), batch("ingot", 1));
        crafting.satelliteId = 7;

        assertThat(network.available(REQUEST)).containsOnlyKeys(item("ore"));
        assertThat(network.request(REQUEST, batch("ingot", 3), 0).complete()).isFalse();
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
    void supplierTopsUpAfterDeliveryWhenItemsWereUsed() {
        connect();
        provider.stock("coal", 64);
        supplier.supplyTargets.put(item("coal"), 16);
        network.tick(SUPPLIER, 0);
        network.chooseExit(SUPPLIER, new TravellingItem(batch("coal", 16), Side.WEST, SUPPLIER), NOW);
        supplier.stock("coal", 12);

        network.tick(SUPPLIER, 20);

        assertThat(provider.shipments).hasSize(2).last().isEqualTo(new Shipment(batch("coal", 4), SUPPLIER));
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
}
