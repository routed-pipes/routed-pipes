package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeNode;
import routedpipes.testing.FakeNode.Shipment;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkShortfallTest {

    private static final RouterId TABLE = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId CRAFTING = router(3);

    private static final long NOW = 100;
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    // the pipe next to the crafting table that asks for its ingredients
    private final FakeNode table = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode crafting = new FakeNode(PipeKind.CRAFTING);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    // all three in a row, linked both ways
    private void connect() {
        network.addRouter(TABLE, table, BUILT);
        network.addRouter(PROVIDER, provider, BUILT);
        network.addRouter(CRAFTING, crafting, BUILT);
        network.updateLinks(TABLE, List.of(new Link(PROVIDER, Side.EAST, 1)), BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(TABLE, Side.WEST, 1), new Link(CRAFTING, Side.EAST, 1)), BUILT);
        network.updateLinks(CRAFTING, List.of(new Link(PROVIDER, Side.WEST, 1)), BUILT);
    }

    @Test
    void ordersOnlyWhatIsNotThereAlready() {
        connect();
        provider.stock("planks", 20).stock("stick", 20);

        RequestOutcome outcome = network.requestShortfall(TABLE, Map.of(item("planks"), 3, item("stick"), 2),
                Map.of(item("planks"), 1, item("stick"), 5), NOW, false);

        assertThat(outcome.started()).isTrue();
        assertThat(provider.shipments).containsExactly(new Shipment(batch("planks", 2), TABLE));
    }

    @Test
    void ordersNothingWhenEverythingIsThere() {
        connect();
        provider.stock("planks", 20);
        int power = provider.power + table.power + crafting.power;

        RequestOutcome outcome = network.requestShortfall(TABLE, Map.of(item("planks"), 3), Map.of(item("planks"), 3), NOW, false);

        assertThat(outcome.started()).isTrue();
        assertThat(provider.shipments).isEmpty();
        assertThat(provider.power + table.power + crafting.power).isEqualTo(power);
    }

    @Test
    void whatIsOnItsWayIsNotOrderedAgain() {
        connect();
        provider.stock("planks", 20);

        network.requestShortfall(TABLE, Map.of(item("planks"), 3), Map.of(), NOW, false);
        network.requestShortfall(TABLE, Map.of(item("planks"), 3), Map.of(), NOW + 1, false);
        network.requestShortfall(TABLE, Map.of(item("planks"), 5), Map.of(), NOW + 2, false);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("planks", 3), TABLE), new Shipment(batch("planks", 2), TABLE));
    }

    @Test
    void ordersNothingAtAllWhenOneIngredientCannotBeHad() {
        connect();
        provider.stock("planks", 20).stock("stick", 1);

        RequestOutcome outcome = network.requestShortfall(TABLE, Map.of(item("planks"), 3, item("stick"), 2), Map.of(), NOW, false);

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.missing()).containsOnly(Map.entry(item("stick"), 1));
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void simulatingTellsWhatIsMissingAndMovesNothing() {
        connect();
        provider.stock("planks", 20);
        int power = provider.power + table.power + crafting.power;

        RequestOutcome possible = network.requestShortfall(TABLE, Map.of(item("planks"), 3), Map.of(), NOW, true);
        RequestOutcome impossible = network.requestShortfall(TABLE, Map.of(item("planks"), 30), Map.of(), NOW, true);

        assertThat(possible.missing()).isEmpty();
        assertThat(impossible.missing()).containsOnly(Map.entry(item("planks"), 10));
        assertThat(provider.shipments).isEmpty();
        assertThat(provider.power + table.power + crafting.power).isEqualTo(power);
        // nothing counts as being on its way either
        network.requestShortfall(TABLE, Map.of(item("planks"), 3), Map.of(), NOW, false);
        assertThat(provider.shipments).containsExactly(new Shipment(batch("planks", 3), TABLE));
    }

    @Test
    void missingIngredientsAreCraftedByOtherPipes() {
        connect();
        provider.stock("log", 5);
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));

        RequestOutcome outcome = network.requestShortfall(TABLE, Map.of(item("planks"), 2), Map.of(), NOW, false);

        assertThat(outcome.started()).isTrue();
        assertThat(provider.shipments).containsExactly(new Shipment(batch("log", 1), CRAFTING));
        assertThat(crafting.outputOrders.owed(item("planks"))).isEqualTo(2);
    }

    @Test
    void withoutPowerNothingIsOrdered() {
        connect();
        provider.stock("planks", 20);
        provider.power = 0;
        table.power = 0;
        crafting.power = 0;

        RequestOutcome outcome = network.requestShortfall(TABLE, Map.of(item("planks"), 3), Map.of(), NOW, false);

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.powered()).isFalse();
        assertThat(provider.shipments).isEmpty();
    }
}
