package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import org.junit.jupiter.api.Test;

class LogisticsNetworkListRequestTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);

    private static final long NOW = 100;
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST_MK2);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);

    private void connect() {
        network.addRouter(REQUEST, request, BUILT);
        network.addRouter(PROVIDER, provider, BUILT);
        network.updateLinks(REQUEST, List.of(new Link(PROVIDER, Side.EAST, 1)), BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(REQUEST, Side.WEST, 1)), BUILT);
    }

    @Test
    void ordersEveryItemOfTheList() {
        connect();
        provider.stock("planks", 20).stock("stick", 20);

        RequestOutcome outcome = network.requestList(REQUEST, List.of(batch("planks", 3), batch("stick", 2)), NOW);

        assertThat(outcome.started()).isTrue();
        assertThat(provider.shipments).containsExactlyInAnyOrder(new Shipment(batch("planks", 3), REQUEST),
                new Shipment(batch("stick", 2), REQUEST));
    }

    @Test
    void ordersNothingAtAllWhenOneItemCannotBeHad() {
        connect();
        provider.stock("planks", 20).stock("stick", 1);

        RequestOutcome outcome = network.requestList(REQUEST, List.of(batch("planks", 3), batch("stick", 2)), NOW);

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.missing()).containsOnly(Map.entry(batch("stick", 1).type(), 1));
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void orderingTheSameListAgainOrdersItAgain() {
        connect();
        provider.stock("planks", 20);

        network.requestList(REQUEST, List.of(batch("planks", 3)), NOW);
        network.requestList(REQUEST, List.of(batch("planks", 3)), NOW + 1);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("planks", 3), REQUEST),
                new Shipment(batch("planks", 3), REQUEST));
    }

    @Test
    void withoutPowerNothingIsOrdered() {
        connect();
        provider.stock("planks", 20);
        provider.power = 0;
        request.power = 0;

        RequestOutcome outcome = network.requestList(REQUEST, List.of(batch("planks", 3)), NOW);

        assertThat(outcome.powered()).isFalse();
        assertThat(provider.shipments).isEmpty();
    }
}
