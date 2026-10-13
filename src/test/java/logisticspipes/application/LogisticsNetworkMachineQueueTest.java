package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

// a machine that several recipes share works on one of them at a time, the one that was ordered first
class LogisticsNetworkMachineQueueTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId SAWMILL = router(3);
    private static final RouterId KILN = router(4);
    private static final RouterId SATELLITE = router(5);
    private static final InventoryRef MACHINE = new InventoryRef(77);
    private static final Recipe PLANKS = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
    private static final Recipe BRICK = new Recipe(List.of(batch("clay", 1)), batch("brick", 1));
    private static final long BUILT = -100;
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode sawmill = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode kiln = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode satellite = new FakeNode(PipeKind.SATELLITE);

    private static void onTheMachine(FakeNode node) {
        node.inventories.add(Side.UP);
        node.inventoryRefs.put(Side.UP, MACHINE);
    }

    // all five in a row, one apart, linked both ways, both crafting pipes on the same machine
    private void connect() {
        List<RouterId> ids = List.of(REQUEST, PROVIDER, SAWMILL, KILN, SATELLITE);
        List<FakeNode> nodes = List.of(request, provider, sawmill, kiln, satellite);
        request.inventories.add(Side.UP);
        provider.stock("log", 64);
        provider.stock("clay", 64);
        provider.stock("coal", 64);
        onTheMachine(sawmill);
        onTheMachine(kiln);
        sawmill.recipe = PLANKS;
        kiln.recipe = BRICK;
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

    private static TravellingItem ordered(String name, RouterId destination) {
        return new TravellingItem(batch(name, 1), Side.WEST, destination);
    }

    // the machine finished the planks and the pipe sends them off
    private void sawmillDelivers(long at) {
        sawmill.contents.put(batch("planks", 1).type(), 4);
        network.tick(SAWMILL, at);
    }

    @Test
    void whatIsForTheRecipeOrderedSecondWaitsInThePipe() {
        connect();
        network.request(REQUEST, batch("planks", 4), NOW);
        network.request(REQUEST, batch("brick", 1), NOW);
        TravellingItem clay = ordered("clay", KILN);
        clay.waitForExit();

        assertThat(network.chooseExit(KILN, clay, NOW + 5)).isEmpty();
        assertThat(clay.waited()).as("it is not given up on").isZero();
        assertThat(network.chooseExit(SAWMILL, ordered("log", SAWMILL), NOW + 5)).contains(Side.UP);
    }

    @Test
    void whenTheFirstOrderIsOutTheNextRecipeGetsItsTurn() {
        connect();
        network.request(REQUEST, batch("planks", 4), NOW);
        network.request(REQUEST, batch("brick", 1), NOW);
        assertThat(network.chooseExit(KILN, ordered("clay", KILN), NOW + 5)).isEmpty();

        sawmillDelivers(NOW + 10);

        assertThat(sawmill.outputOrders.isEmpty()).isTrue();
        assertThat(network.chooseExit(KILN, ordered("clay", KILN), NOW + 11)).contains(Side.UP);
    }

    @Test
    void theOrderOfTheRequestsCountsNotTheOrderOfThePipes() {
        connect();
        network.request(REQUEST, batch("brick", 1), NOW);
        network.request(REQUEST, batch("planks", 4), NOW + 1);

        assertThat(network.chooseExit(SAWMILL, ordered("log", SAWMILL), NOW + 5)).isEmpty();
        assertThat(network.chooseExit(KILN, ordered("clay", KILN), NOW + 5)).contains(Side.UP);
    }

    @Test
    void aRecipeWithItsMachineToItselfIsNeverHeldUp() {
        connect();
        kiln.inventoryRefs.put(Side.UP, new InventoryRef(78));
        network.inventoriesChanged();
        network.request(REQUEST, batch("planks", 4), NOW);
        network.request(REQUEST, batch("brick", 1), NOW);

        assertThat(network.chooseExit(KILN, ordered("clay", KILN), NOW + 5)).contains(Side.UP);
        assertThat(network.chooseExit(SAWMILL, ordered("log", SAWMILL), NOW + 5)).contains(Side.UP);
    }

    @Test
    void anOrderThatIsGivenUpOnNoLongerBlocksTheMachine() {
        connect();
        network.request(REQUEST, batch("planks", 4), NOW);
        network.request(REQUEST, batch("brick", 1), NOW);
        long late = NOW + LogisticsNetwork.CRAFT_TIMEOUT;
        // the kiln keeps waiting for its clay, which is still at the door, so its order is not stale
        network.tick(SAWMILL, late);

        assertThat(sawmill.outputOrders.isEmpty()).isTrue();
        assertThat(network.chooseExit(KILN, ordered("clay", KILN), late + 1)).contains(Side.UP);
    }

    @Test
    void whatIsNoIngredientIsLetInWhateverTheMachineIsDoing() {
        connect();
        network.request(REQUEST, batch("planks", 4), NOW);
        network.request(REQUEST, batch("brick", 1), NOW);

        assertThat(network.chooseExit(KILN, ordered("coal", KILN), NOW + 5)).contains(Side.UP);
    }

    @Test
    void whatGoesToTheSatelliteOfARecipeWaitsThereToo() {
        connect();
        satellite.satelliteName = "fuel";
        onTheMachine(satellite);
        kiln.recipe = null;
        Recipe fired = new Recipe(List.of(batch("clay", 1), batch("coal", 1)), batch("brick", 1));
        kiln.crafts = List.of(new CraftingSetup(fired, List.of(batch("coal", 1)), "fuel", 0));
        network.sinksChanged(KILN);
        network.request(REQUEST, batch("planks", 4), NOW);
        network.request(REQUEST, batch("brick", 1), NOW);

        assertThat(network.chooseExit(SATELLITE, ordered("coal", SATELLITE), NOW + 5)).isEmpty();
        sawmillDelivers(NOW + 10);
        assertThat(network.chooseExit(SATELLITE, ordered("coal", SATELLITE), NOW + 11)).contains(Side.UP);
    }

    @Test
    void twoRecipesOfOneChassisTakeTurnsAsWell() {
        connect();
        sawmill.recipe = null;
        sawmill.crafts = List.of(new CraftingSetup(PLANKS, List.of(), "", 0), new CraftingSetup(BRICK, List.of(), "", 0));
        kiln.inventoryRefs.clear();
        kiln.inventories.clear();
        kiln.recipe = null;
        network.sinksChanged(SAWMILL);
        network.sinksChanged(KILN);
        network.request(REQUEST, batch("brick", 1), NOW);
        network.request(REQUEST, batch("planks", 4), NOW);

        assertThat(network.chooseExit(SAWMILL, ordered("log", SAWMILL), NOW + 5)).isEmpty();
        assertThat(network.chooseExit(SAWMILL, ordered("clay", SAWMILL), NOW + 5)).contains(Side.UP);
    }

    @Test
    void aPartIsMadeBeforeTheThingItGoesIntoWhenBothShareAMachine() {
        connect();
        kiln.recipe = new Recipe(List.of(batch("planks", 2)), batch("slab", 1));

        assertThat(network.request(REQUEST, batch("slab", 1), NOW).plan().complete()).isTrue();

        // the planks the slab is made of are not let into the machine while it still saws them
        assertThat(network.chooseExit(SAWMILL, ordered("log", SAWMILL), NOW + 5)).contains(Side.UP);
        assertThat(network.chooseExit(KILN, ordered("planks", KILN), NOW + 5)).isEmpty();
    }
}
