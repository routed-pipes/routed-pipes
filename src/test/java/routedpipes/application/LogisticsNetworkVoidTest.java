package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import routedpipes.domain.logistics.ExtractionMode;
import routedpipes.domain.logistics.ItemSink;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.VoidSink;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.statistics.PipeStatistics;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class LogisticsNetworkVoidTest {

    private static final RouterId ENTRY = router(1);
    private static final RouterId VOID = router(2);
    private static final RouterId STORE = router(3);
    // a tick on which every kind of upkeep is due
    private static final long NOW = 120;
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode entry = new FakeNode(PipeKind.CHASSIS);
    private final FakeNode voidPipe = new FakeNode(PipeKind.VOID);
    private final FakeNode store = new FakeNode(PipeKind.BASIC);

    // entry - void - store in a row, the void pipe is the nearer one
    private void build() {
        entry.sink = null;
        voidPipe.sink = new VoidSink(Set.of());
        // a void pipe takes on every side, it needs no inventory
        voidPipe.inventories.addAll(EnumSet.allOf(Side.class));
        network.addRouter(ENTRY, entry, BUILT);
        network.addRouter(VOID, voidPipe, BUILT);
        network.addRouter(STORE, store, BUILT);
        network.updateLinks(ENTRY, List.of(new Link(VOID, Side.EAST, 1)), BUILT);
        network.updateLinks(VOID, List.of(new Link(ENTRY, Side.WEST, 1), new Link(STORE, Side.EAST, 1)), BUILT);
        network.updateLinks(STORE, List.of(new Link(VOID, Side.WEST, 1)), BUILT);
    }

    private static TravellingItem stone() {
        return new TravellingItem(batch("stone", 8), Side.WEST, null);
    }

    @Test
    void anItemWithNowhereElseToGoIsSentToTheVoidPipe() {
        build();
        TravellingItem item = stone();

        assertThat(network.chooseExit(ENTRY, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(VOID);
        assertThat(item.mode()).isEqualTo(TransportMode.SORTED);
    }

    @Test
    void aDefaultRouteWithRoomWinsOverANearerVoidPipe() {
        build();
        store.inventories.add(Side.UP);
        TravellingItem item = stone();

        assertThat(network.chooseExit(ENTRY, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(STORE);
    }

    @Test
    void aSinkThatListsTheItemWinsToo() {
        build();
        store.sink = new ItemSink(Set.of(batch("stone", 1).type()), false);
        network.sinksChanged(STORE);
        store.inventories.add(Side.UP);
        TravellingItem item = stone();

        network.chooseExit(ENTRY, item, NOW);

        assertThat(item.destination()).contains(STORE);
    }

    @Test
    void aDefaultRouteWithoutRoomLeavesTheItemToTheVoidPipe() {
        build();
        store.inventories.add(Side.UP);
        store.room = false;
        TravellingItem item = stone();

        network.chooseExit(ENTRY, item, NOW);

        assertThat(item.destination()).contains(VOID);
    }

    @Test
    void theItemLeavesTheVoidPipeThroughASideItDidNotComeInByAndCountsAsReceived() {
        build();
        TravellingItem item = stone();
        network.chooseExit(ENTRY, item, NOW);

        assertThat(network.chooseExit(VOID, item, NOW)).isPresent().get().isNotEqualTo(Side.WEST);
        assertThat(item.destination()).contains(VOID);
        assertThat(voidPipe.statistics.session(PipeStatistics.Kind.RECEIVED)).isEqualTo(8);
    }

    @Test
    void aVoidPipeWithAListTakesOnlyWhatIsListed() {
        build();
        voidPipe.sink = new VoidSink(Set.of(batch("dirt", 1).type()));
        network.sinksChanged(VOID);
        TravellingItem stone = stone();
        TravellingItem dirt = new TravellingItem(batch("dirt", 8), Side.WEST, null);

        assertThat(network.chooseExit(ENTRY, stone, NOW)).isEmpty();
        assertThat(stone.destination()).isEmpty();
        network.chooseExit(ENTRY, dirt, NOW);
        assertThat(dirt.destination()).contains(VOID);
    }

    @Test
    void anExtractorDoesNotFeedAVoidPipe() {
        build();
        entry.extractions.add(ExtractionMode.EXTRACTOR);
        entry.stock("stone", 40);

        network.tick(ENTRY, NOW);

        assertThat(entry.shipments).isEmpty();
        assertThat(entry.contents).containsEntry(batch("stone", 1).type(), 40);
    }

    @Test
    void nothingIsPutIntoThePipesJustToBeDestroyed() {
        build();

        assertThat(network.wanted(ENTRY, batch("stone", 8), NOW)).isFalse();
    }

    @Test
    void anItemInThePipesStillFindsTheVoidPipeAfterAnExtractorFoundNoRoom() {
        build();
        store.inventories.add(Side.UP);
        store.room = false;
        entry.extractions.add(ExtractionMode.EXTRACTOR);
        entry.stock("stone", 40);
        // the extractor finds no sink and that is remembered for a while
        network.tick(ENTRY, NOW);
        TravellingItem item = stone();

        network.chooseExit(ENTRY, item, NOW + 1);

        assertThat(item.destination()).contains(VOID);
        // and the full default route the extractor asked is not asked again for it
        assertThat(store.probes).isEqualTo(1);
    }

    @Test
    void whatIsKnownAboutFullSinksIsNotBelievedForLongerBecauseAnItemAskedTheVoidPipeToo() {
        build();
        voidPipe.sink = new VoidSink(Set.of(batch("dirt", 1).type()));
        network.sinksChanged(VOID);
        store.inventories.add(Side.UP);
        store.room = false;
        entry.extractions.add(ExtractionMode.EXTRACTOR);
        entry.stock("stone", 40);
        network.tick(ENTRY, NOW);
        // just before the extractor's answer runs out an item asks, the void pipe does not list it
        network.chooseExit(ENTRY, stone(), NOW + LogisticsNetwork.NO_ROOM_TICKS - 1);
        store.room = true;

        network.tick(ENTRY, NOW + LogisticsNetwork.NO_ROOM_TICKS);

        assertThat(entry.shipments).hasSize(1);
        assertThat(entry.shipments.get(0).destination()).isEqualTo(STORE);
    }

    @Test
    void anExtractorStillFindsNoSinkAfterAnItemWentToTheVoidPipe() {
        build();
        entry.extractions.add(ExtractionMode.EXTRACTOR);
        entry.stock("stone", 40);
        network.chooseExit(ENTRY, stone(), NOW - 1);

        network.tick(ENTRY, NOW);

        assertThat(entry.shipments).isEmpty();
    }

    @Test
    void takingAStackCostsTheNetwork() {
        build();
        entry.power = 0;
        store.power = 0;
        voidPipe.power = 10;

        network.chooseExit(ENTRY, stone(), NOW);

        assertThat(voidPipe.power).isEqualTo(10 - new VoidSink(Set.of()).energy());
    }
}
