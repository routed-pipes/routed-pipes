package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.logistics.InventoryRef;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

// a pipe can give up on what is still being crafted for it
class LogisticsNetworkCancelTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId SAWMILL = router(3);
    private static final RouterId JOINER = router(4);
    private static final RouterId OTHER_REQUEST = router(5);
    private static final long BUILT = -100;
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode sawmill = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode joiner = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode otherRequest = new FakeNode(PipeKind.REQUEST);

    // all five in a row, one apart, linked both ways. the sawmill makes planks out of logs, the joiner slabs out of planks
    private void connect() {
        List<RouterId> ids = List.of(REQUEST, PROVIDER, SAWMILL, JOINER, OTHER_REQUEST);
        List<FakeNode> nodes = List.of(request, provider, sawmill, joiner, otherRequest);
        request.inventories.add(Side.UP);
        otherRequest.inventories.add(Side.UP);
        provider.stock("log", 64);
        sawmill.inventories.add(Side.UP);
        sawmill.inventoryRefs.put(Side.UP, new InventoryRef(31));
        sawmill.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        joiner.inventories.add(Side.UP);
        joiner.inventoryRefs.put(Side.UP, new InventoryRef(32));
        joiner.recipe = new Recipe(List.of(batch("planks", 2)), batch("slab", 1));
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

    @Test
    void countsWhatIsStillBeingCraftedForAPipe() {
        connect();
        assertThat(network.openOrders(REQUEST)).isZero();

        network.request(REQUEST, batch("slab", 6), NOW);

        assertThat(network.openOrders(REQUEST)).isEqualTo(6);
        assertThat(network.openOrders(OTHER_REQUEST)).isZero();
    }

    @Test
    void cancellingDropsTheOrderAndTheOrdersForItsParts() {
        connect();
        network.request(REQUEST, batch("slab", 6), NOW);
        assertThat(sawmill.outputOrders.isEmpty()).isFalse();

        assertThat(network.cancelOrders(REQUEST)).isEqualTo(6);

        assertThat(joiner.outputOrders.isEmpty()).isTrue();
        assertThat(sawmill.outputOrders.isEmpty()).as("the planks for the slabs are not sawn either").isTrue();
        assertThat(network.openOrders(REQUEST)).isZero();
    }

    @Test
    void whatSomeoneElseOrderedStaysOrdered() {
        connect();
        network.request(REQUEST, batch("slab", 6), NOW);
        network.request(OTHER_REQUEST, batch("slab", 2), NOW);
        network.request(OTHER_REQUEST, batch("planks", 4), NOW + 1);

        network.cancelOrders(REQUEST);

        assertThat(joiner.outputOrders.all()).extracting(OutputOrder::destination, order -> order.batch().count())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(OTHER_REQUEST, 2));
        assertThat(sawmill.outputOrders.owedInTotal()).as("planks for the two slabs, and the four that were asked for").isEqualTo(4 + 4);
    }

    @Test
    void nothingToCancelIsFine() {
        connect();

        assertThat(network.cancelOrders(REQUEST)).isZero();
    }

    @Test
    void whatWasMadeAlreadyIsThereForTheNextRequest() {
        connect();
        network.request(REQUEST, batch("slab", 6), NOW);
        network.cancelOrders(REQUEST);
        // the logs were on their way and the sawmill sawed them anyway
        sawmill.contents.put(batch("planks", 1).type(), 12);

        assertThat(network.available(OTHER_REQUEST)).containsEntry(batch("planks", 1).type(), 12);
        assertThat(network.request(OTHER_REQUEST, batch("planks", 12), NOW + 50).plan().crafts()).isEmpty();
    }

    @Test
    void whatWasCancelledIsNoLongerExpected() {
        connect();
        network.request(REQUEST, batch("slab", 6), NOW);
        assertThat(network.onTheWayTo(REQUEST, NOW + 1)).containsEntry(batch("slab", 1).type(), 6);

        network.cancelOrders(REQUEST);

        assertThat(network.onTheWayTo(REQUEST, NOW + 1)).isEmpty();
    }

    @Test
    void aCancelledRecipeNoLongerHoldsUpTheMachineItShares() {
        connect();
        joiner.inventoryRefs.put(Side.UP, new InventoryRef(31));
        joiner.recipe = new Recipe(List.of(batch("log", 2)), batch("slab", 1));
        network.inventoriesChanged();
        network.sinksChanged(JOINER);
        network.request(REQUEST, batch("planks", 4), NOW);
        network.request(OTHER_REQUEST, batch("slab", 1), NOW + 1);
        TravellingItem logs = new TravellingItem(batch("log", 2), Side.WEST, JOINER);
        assertThat(network.chooseExit(JOINER, logs, NOW + 5)).isEmpty();

        network.cancelOrders(REQUEST);

        assertThat(network.chooseExit(JOINER, logs, NOW + 6)).contains(Side.UP);
        // the log for the planks nobody wants any more only gets in when the machine has nothing else to do
        TravellingItem log = new TravellingItem(batch("log", 1), Side.WEST, SAWMILL);
        assertThat(network.chooseExit(SAWMILL, log, NOW + 6)).isEmpty();
    }
}
