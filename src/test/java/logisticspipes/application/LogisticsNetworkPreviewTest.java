package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.request.RequestSummary;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkPreviewTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId CRAFTING = router(3);
    private static final long BUILT = -100;
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode crafting = new FakeNode(PipeKind.CRAFTING);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    // request - provider - crafting in a row, the crafting pipe makes four planks out of a log
    private void connect() {
        crafting.recipe = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        network.addRouter(REQUEST, request, BUILT);
        network.addRouter(PROVIDER, provider, BUILT);
        network.addRouter(CRAFTING, crafting, BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1)), BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(REQUEST, Side.WEST, 1), new Link(CRAFTING, Side.EAST, 1)), BUILT);
        network.updateLinks(CRAFTING, List.of(new Link(PROVIDER, Side.WEST, 1)), BUILT);
    }

    private int power() {
        return request.power + provider.power + crafting.power;
    }

    @Test
    void tellsWhatComesFromStockWhatIsCraftedAndTheEnergy() {
        connect();
        provider.stock("log", 10);
        provider.stock("planks", 3);

        RequestSummary summary = network.preview(REQUEST, batch("planks", 11), NOW);

        // three planks are there, the other eight take two logs
        assertThat(summary.fromStock()).containsOnly(Map.entry(item("planks"), 3), Map.entry(item("log"), 2));
        assertThat(summary.crafted()).containsExactly(Map.entry(item("planks"), 8));
        assertThat(summary.missing()).isEmpty();
        assertThat(summary.energy()).isEqualTo(5 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM
                + 8 * LogisticsNetwork.ENERGY_PER_CRAFTED_ITEM);
    }

    @Test
    void tellsWhatIsMissing() {
        connect();
        provider.stock("log", 1);

        RequestSummary summary = network.preview(REQUEST, batch("planks", 12), NOW);

        assertThat(summary.missing()).containsExactly(Map.entry(item("log"), 2));
        assertThat(summary.complete()).isFalse();
    }

    @Test
    void nothingIsOrderedAndNoPowerIsUsed() {
        connect();
        provider.stock("log", 10);
        int before = power();

        network.preview(REQUEST, batch("planks", 8), NOW);

        assertThat(provider.shipments).isEmpty();
        assertThat(provider.contents).containsEntry(item("log"), 10);
        assertThat(crafting.outputOrders.isEmpty()).isTrue();
        assertThat(power()).isEqualTo(before);
    }

    @Test
    void theRequestStillGoesThroughAfterwardsJustAsPreviewed() {
        connect();
        provider.stock("log", 10);

        RequestSummary summary = network.preview(REQUEST, batch("planks", 8), NOW);
        // nothing of the preview is remembered as being on its way
        RequestSummary again = network.preview(REQUEST, batch("planks", 8), NOW);
        assertThat(again).isEqualTo(summary);

        assertThat(network.request(REQUEST, batch("planks", 8), NOW).started()).isTrue();
        assertThat(provider.shipments).containsExactly(new FakeNode.Shipment(batch("log", 2), CRAFTING));
        assertThat(crafting.outputOrders.owed(item("planks"))).isEqualTo(8);
    }

    @Test
    void aNetworkWithoutPowerCanStillBePreviewed() {
        connect();
        provider.stock("log", 10);
        request.power = 0;
        provider.power = 0;
        crafting.power = 0;

        RequestSummary summary = network.preview(REQUEST, batch("planks", 4), NOW);

        assertThat(summary.complete()).isTrue();
        assertThat(summary.energy()).isEqualTo(LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM
                + 4 * LogisticsNetwork.ENERGY_PER_CRAFTED_ITEM);
    }
}
