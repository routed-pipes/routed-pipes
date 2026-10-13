package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.logistics.ExtractionMode;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.SupplyMode;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

// pipes have their rounds of upkeep in different ticks, a base that did all of it in one tick would stutter
class LogisticsNetworkPhaseTest {

    private static final RouterId WORKER = router(1);
    private static final RouterId OTHER = router(2);
    private static final long BUILT = -100;
    // a tick in which a pipe without a phase has all its rounds
    private static final long NOW = 120;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode other = new FakeNode(PipeKind.PROVIDER);

    private void connect(FakeNode worker) {
        network.addRouter(WORKER, worker, BUILT);
        network.addRouter(OTHER, other, BUILT);
        network.updateLinks(WORKER, List.of(new Link(OTHER, Side.EAST, 1)), BUILT);
        network.updateLinks(OTHER, List.of(new Link(WORKER, Side.WEST, 1)), BUILT);
    }

    @Test
    void aSupplierOrdersInTheTickItsPhasePutsItIn() {
        FakeNode supplier = new FakeNode(PipeKind.SUPPLIER);
        supplier.inventories.add(Side.UP);
        supplier.supplyMode = SupplyMode.PARTIAL;
        supplier.supplyTargets.put(batch("stone", 1).type(), 8);
        supplier.phase = 3;
        other.stock("stone", 64);
        connect(supplier);

        network.tick(WORKER, NOW);
        assertThat(other.shipments).isEmpty();
        network.tick(WORKER, NOW + LogisticsNetwork.SUPPLY_INTERVAL - 3);

        assertThat(other.shipments).hasSize(1);
    }

    @Test
    void anExtractorPushesInTheTickItsPhasePutsItIn() {
        FakeNode extractor = new FakeNode(PipeKind.CHASSIS);
        extractor.sink = null;
        extractor.stock("junk", 64);
        extractor.extractions.add(ExtractionMode.EXTRACTOR);
        extractor.phase = 7;
        FakeNode sink = new FakeNode(PipeKind.BASIC);
        sink.inventories.add(Side.UP);
        network.addRouter(WORKER, extractor, BUILT);
        network.addRouter(OTHER, sink, BUILT);
        network.updateLinks(WORKER, List.of(new Link(OTHER, Side.EAST, 1)), BUILT);
        network.updateLinks(OTHER, List.of(new Link(WORKER, Side.WEST, 1)), BUILT);

        network.tick(WORKER, NOW);
        assertThat(extractor.shipments).isEmpty();
        network.tick(WORKER, NOW + ExtractionMode.EXTRACTOR.interval() - 7);

        assertThat(extractor.shipments).hasSize(1);
    }

    @Test
    void aCrafterShipsInTheTickItsPhasePutsItIn() {
        FakeNode crafter = new FakeNode(PipeKind.CRAFTING);
        crafter.stock("plank", 4);
        crafter.outputOrders.add(new OutputOrder(OTHER, batch("plank", 4), NOW));
        crafter.phase = 2;
        connect(crafter);

        network.tick(WORKER, NOW);
        assertThat(crafter.shipments).isEmpty();
        network.tick(WORKER, NOW + LogisticsNetwork.CRAFT_INTERVAL - 2);

        assertThat(crafter.shipments).hasSize(1);
    }
}
