package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import org.junit.jupiter.api.Test;

// when no sink has room for an item that is remembered for a while and not asked again by everyone who has one
class LogisticsNetworkNoRoomTest {

    private static final RouterId EXTRACTOR = router(1);
    private static final RouterId NEAR = router(2);
    private static final RouterId FAR = router(3);
    private static final RouterId ISLAND = router(9);
    private static final long BUILT = -100;
    private static final long NOW = 100;
    private static final int ROUND = ExtractionMode.EXTRACTOR.interval();

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode extractor = new FakeNode(PipeKind.CHASSIS);
    private final FakeNode near = new FakeNode(PipeKind.BASIC);
    private final FakeNode far = new FakeNode(PipeKind.BASIC);

    // all three in a row, one apart, linked both ways, the sinks take anything and are full
    private void connect() {
        List<RouterId> ids = List.of(EXTRACTOR, NEAR, FAR);
        List<FakeNode> nodes = List.of(extractor, near, far);
        extractor.sink = null;
        extractor.stock("junk", 64);
        extractor.extractions.add(ExtractionMode.EXTRACTOR);
        for (FakeNode sink : List.of(near, far)) {
            sink.inventories.add(Side.UP);
            sink.room = false;
        }
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
    void fullSinksAreNotAskedAgainRightAway() {
        connect();

        network.tick(EXTRACTOR, NOW);
        network.tick(EXTRACTOR, NOW + ROUND);

        assertThat(near.probes).isEqualTo(1);
        assertThat(far.probes).isEqualTo(1);
    }

    @Test
    void afterAWhileTheyAreAskedAgainAndRoomIsFound() {
        connect();
        network.tick(EXTRACTOR, NOW);
        far.room = true;

        network.tick(EXTRACTOR, NOW + ROUND);
        assertThat(extractor.shipments).isEmpty();
        network.tick(EXTRACTOR, NOW + LogisticsNetwork.NO_ROOM_TICKS);

        assertThat(extractor.shipments).containsExactly(new Shipment(batch("junk", 16), FAR));
    }

    @Test
    void anItemWaitingElsewhereDoesNotAskThemEither() {
        connect();
        network.tick(EXTRACTOR, NOW);
        TravellingItem waiting = new TravellingItem(batch("junk", 8), Side.UP, null);

        Optional<Side> exit = network.chooseExit(FAR, waiting, NOW + 1);

        assertThat(exit).isEmpty();
        assertThat(near.probes).isEqualTo(1);
    }

    @Test
    void aSinkThatWasLeftOutForOneRouterIsStillAskedForAnother() {
        connect();
        // the extractor takes junk itself, which does not count for what it takes out of its own inventory
        extractor.sink = new logisticspipes.domain.logistics.ItemSink(java.util.Set.of(), true);
        network.sinksChanged(EXTRACTOR);
        network.tick(EXTRACTOR, NOW);
        assertThat(extractor.shipments).isEmpty();
        TravellingItem waiting = new TravellingItem(batch("junk", 8), Side.UP, null);

        Optional<Side> exit = network.chooseExit(FAR, waiting, NOW + 1);

        assertThat(exit).contains(Side.WEST);
        assertThat(waiting.destination()).contains(EXTRACTOR);
    }

    @Test
    void aSinkWhoseSettingsChangedIsAskedAtOnce() {
        connect();
        network.tick(EXTRACTOR, NOW);
        far.room = true;
        network.sinksChanged(FAR);

        network.tick(EXTRACTOR, NOW + ROUND);

        assertThat(extractor.shipments).containsExactly(new Shipment(batch("junk", 16), FAR));
    }

    @Test
    void aSinkThatCountsOnlyForKeenerRequestsDoesNotHideTheDefaultRoutes() {
        connect();
        // quick sort only goes to sinks that list the item, nobody does
        extractor.extractions.clear();
        extractor.extractions.add(ExtractionMode.QUICK_SORT);
        network.tick(EXTRACTOR, NOW + 2);
        far.room = true;
        TravellingItem waiting = new TravellingItem(batch("junk", 8), Side.UP, null);

        Optional<Side> exit = network.chooseExit(NEAR, waiting, NOW + 3);

        assertThat(exit).contains(Side.EAST);
    }

    @Test
    void sinksOfAnotherBaseAreNotAsked() {
        connect();
        FakeNode island = new FakeNode(PipeKind.BASIC);
        island.inventories.add(Side.UP);
        network.addRouter(ISLAND, island, BUILT);

        network.tick(EXTRACTOR, NOW);

        assertThat(island.probes).isZero();
        assertThat(island.looks).isZero();
        assertThat(extractor.shipments).isEmpty();
    }
}
