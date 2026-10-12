package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SupplyMode;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkCraftingReviewTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId CRAFTING = router(3);
    private static final RouterId SECOND = router(4);
    private static final RouterId SUPPLIER = router(5);
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode crafting = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode second = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode supplier = new FakeNode(PipeKind.SUPPLIER);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    private void connect() {
        List<RouterId> ids = List.of(REQUEST, PROVIDER, CRAFTING, SECOND, SUPPLIER);
        List<FakeNode> nodes = List.of(request, provider, crafting, second, supplier);
        supplier.inventories.add(Side.UP);
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

    // FINDING: a pipe whose one recipe makes the ingredient of its other recipe ships that ingredient to itself, and it gets stuck in the pipe
    @Test
    void anIngredientAPipeCraftsForItsOwnOtherRecipeReachesTheMachine() {
        connect();
        provider.stock("log", 64);
        crafting.inventories.add(Side.UP);
        crafting.crafts = List.of(
                new CraftingSetup(new Recipe(List.of(batch("log", 1)), batch("planks", 4)), List.of(), 0, 0),
                new CraftingSetup(new Recipe(List.of(batch("planks", 2)), batch("stick", 4)), List.of(), 0, 0));

        assertThat(network.request(REQUEST, batch("stick", 4), 0).started()).isTrue();
        // the machine made the planks
        crafting.contents.put(item("planks"), 4);
        network.tick(CRAFTING, 5);

        for (Shipment shipment : crafting.shipments) {
            if (CRAFTING.equals(shipment.destination())) {
                // what the pipe takes out of its machine comes in from the machine side
                TravellingItem planks = new TravellingItem(shipment.batch(), Side.UP, CRAFTING);
                planks.waitForExit();
                assertThat(network.chooseExit(CRAFTING, planks, 10)).as("planks sent from the pipe to itself get back into the machine")
                        .isPresent();
            }
        }
    }

    // FINDING: finished items owed by one crafting pipe are offered as spare by a second crafting pipe on the same machine
    @Test
    void itemsOwedByOneCraftingPipeAreNotSpareForASecondOneOnTheSameMachine() {
        connect();
        Recipe planks = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        crafting.recipe = planks;
        second.recipe = planks;
        // both look at the same machine, which holds the 4 planks the first one owes the request pipe
        crafting.stock("planks", 4).inventoryRefs.put(Side.UP, new InventoryRef(7));
        second.stock("planks", 4).inventoryRefs.put(Side.UP, new InventoryRef(7));
        crafting.outputOrders.add(new OutputOrder(REQUEST, batch("planks", 4), 0));

        assertThat(network.available(SUPPLIER)).as("nothing in the machine is spare").containsEntry(item("planks"), 0);
        assertThat(network.request(SUPPLIER, batch("planks", 4), 0).started()).as("no logs, so no planks for the supplier").isFalse();
    }

    // FINDING: an order queued behind a long one at the same crafter is dropped as stale though the crafter is busy with the first
    @Test
    void anOrderWaitingBehindALongOneIsNotDroppedWhileTheCrafterKeepsDelivering() {
        connect();
        provider.stock("log", 80);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 1));
        crafting.inventories.add(Side.UP);
        network.request(REQUEST, batch("planks", 64), 0);
        network.request(SUPPLIER, batch("planks", 4), 0);
        assertThat(crafting.outputOrders.all()).as("both orders are placed").hasSize(2);

        // a slow machine, one plank every 400 ticks, and every one goes out right away
        for (long now = 5; now <= LogisticsNetwork.CRAFT_TIMEOUT; now += 5) {
            if (now % 400 == 0) {
                crafting.contents.merge(item("planks"), 1, Integer::sum);
            }
            network.tick(CRAFTING, now);
        }

        assertThat(crafting.outputOrders.all()).as("the supplier's order is still owed")
                .anyMatch(order -> SUPPLIER.equals(order.destination()));
    }

    // FINDING: after a crafter made progress the rest of its order still runs, but the supplier stops expecting it and orders it again
    @Test
    void aSupplierDoesNotOrderAgainWhatACrafterStillOwesIt() {
        connect();
        provider.stock("log", 64);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 1));
        crafting.inventories.add(Side.UP);
        supplier.supplyMode = SupplyMode.PARTIAL;
        supplier.supplyTargets.put(item("planks"), 32);

        network.tick(SUPPLIER, 0);
        assertThat(crafting.outputOrders.owedInTotal()).isEqualTo(32);

        // the machine finished half of it, which arrives at the supplier
        long half = LogisticsNetwork.CRAFT_TIMEOUT - 1000;
        crafting.contents.put(item("planks"), 16);
        network.tick(CRAFTING, half);
        TravellingItem arrived = new TravellingItem(batch("planks", 16), Side.WEST, SUPPLIER);
        assertThat(network.chooseExit(SUPPLIER, arrived, half + 1)).contains(Side.UP);
        supplier.contents.put(item("planks"), 16);

        long later = LogisticsNetwork.CRAFT_TIMEOUT + 20;
        network.tick(SUPPLIER, later);

        assertThat(crafting.outputOrders.all()).as("the crafter still owes the other half")
                .containsExactly(new OutputOrder(SUPPLIER, batch("planks", 16), half));
        assertThat(provider.shipments).as("the logs for it went out once").containsExactly(new Shipment(batch("log", 32), CRAFTING));
    }
}
