package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.FuzzyFlags;
import logisticspipes.domain.item.FuzzyType;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.FuzzySink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import logisticspipes.testing.Fixtures.WornItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogisticsNetworkFuzzyTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId CRAFTING = router(3);
    private static final long BUILT = -100;
    private static final long NOW = 100;

    private static final FuzzyFlags TAG = new FuzzyFlags(true, false, false, false);
    private static final FuzzyFlags IGNORE_DAMAGE = new FuzzyFlags(false, true, false, false);
    private static final WornItem OAK = WornItem.fresh("oak_planks", "planks");
    private static final WornItem BIRCH = WornItem.fresh("birch_planks", "planks");
    private static final WornItem SWORD = WornItem.fresh("sword");

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode crafting = new FakeNode(PipeKind.CRAFTING);

    // request, provider and crafting pipe in a row
    @BeforeEach
    void connect() {
        network.addRouter(REQUEST, request, BUILT);
        network.addRouter(PROVIDER, provider, BUILT);
        network.addRouter(CRAFTING, crafting, BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1)), BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(REQUEST, Side.WEST, 1), new Link(CRAFTING, Side.EAST, 1)), BUILT);
        network.updateLinks(CRAFTING, List.of(new Link(PROVIDER, Side.WEST, 1)), BUILT);
        crafting.inventories.add(Side.UP);
    }

    @Test
    void anIngredientWithFlagsIsDeliveredAsWhateverMatchingItemTheProviderHolds() {
        provider.contents.put(BIRCH, 64);
        ItemType anyPlanks = new FuzzyType(OAK, TAG);
        crafting.crafts = List.of(new CraftingSetup(new Recipe(List.of(new ItemBatch(anyPlanks, 2)), batch("stick", 4)), List.of(), 0, 0));

        RequestOutcome outcome = network.request(REQUEST, batch("stick", 4), NOW);

        assertThat(outcome.started()).isTrue();
        assertThat(provider.shipments).containsExactly(new Shipment(new ItemBatch(BIRCH, 2), CRAFTING));
    }

    @Test
    void aResultWithFlagsIsShippedAsTheMatchingItemTheMachineHoldsMostOf() {
        provider.stock("iron", 8);
        ItemType anySword = new FuzzyType(SWORD, IGNORE_DAMAGE);
        crafting.crafts = List.of(new CraftingSetup(new Recipe(List.of(batch("iron", 1)), new ItemBatch(anySword, 1)), List.of(), 0, 0));

        assertThat(network.available(REQUEST)).containsEntry(SWORD, 0).doesNotContainKey(anySword);
        assertThat(network.request(REQUEST, new ItemBatch(SWORD, 2), NOW).started()).isTrue();
        assertThat(crafting.outputOrders.owed(anySword)).isEqualTo(2);
        assertThat(network.craftingBacklog(REQUEST)).containsEntry(SWORD, 2).doesNotContainKey(anySword);

        // the machine put out worn swords, they are what the order gets
        crafting.contents.put(SWORD.worn(3), 2);
        crafting.contents.put(OAK, 9);
        network.tick(CRAFTING, NOW + 5);

        assertThat(crafting.shipments).containsExactly(new Shipment(new ItemBatch(SWORD.worn(3), 2), REQUEST));
        assertThat(crafting.outputOrders.isEmpty()).isTrue();
    }

    @Test
    void aSinkWithFlagsTakesWhatMatchesBeforeADefaultRouteDoes() {
        FakeNode fuzzy = new FakeNode(PipeKind.BASIC);
        fuzzy.sink = new FuzzySink(List.of(new FuzzyType(SWORD, IGNORE_DAMAGE)));
        fuzzy.inventories.add(Side.UP);
        RouterId fuzzyId = router(4);
        network.addRouter(fuzzyId, fuzzy, BUILT);
        network.updateLinks(fuzzyId, List.of(new Link(CRAFTING, Side.WEST, 1)), BUILT);
        network.updateLinks(CRAFTING, List.of(new Link(PROVIDER, Side.WEST, 1), new Link(fuzzyId, Side.EAST, 1)), BUILT);
        FakeNode anything = new FakeNode(PipeKind.BASIC);
        anything.inventories.add(Side.UP);
        RouterId anythingId = router(5);
        network.addRouter(anythingId, anything, BUILT);
        network.updateLinks(anythingId, List.of(new Link(REQUEST, Side.EAST, 1)), BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1), new Link(anythingId, Side.WEST, 1)), BUILT);

        TravellingItem worn = new TravellingItem(new ItemBatch(SWORD.worn(7), 1), Side.DOWN, null);
        TravellingItem planks = new TravellingItem(new ItemBatch(OAK, 1), Side.DOWN, null);
        network.chooseExit(REQUEST, worn, NOW);
        network.chooseExit(REQUEST, planks, NOW);

        assertThat(worn.destination()).contains(fuzzyId);
        assertThat(planks.destination()).contains(anythingId);
    }
}
