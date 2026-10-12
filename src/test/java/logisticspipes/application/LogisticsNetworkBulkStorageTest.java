package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.BulkStorageSink;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.PolymorphicItemSink;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkBulkStorageTest {

    private static final RouterId MODULE = router(1);
    private static final RouterId ENTRY = router(2);
    private static final int FIRST_STORE = 10;
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode module = new FakeNode(PipeKind.CHASSIS);
    private final FakeNode entry = new FakeNode(PipeKind.CHASSIS);
    private final List<FakeNode> stores = new ArrayList<>();
    private long now = 100;

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    private static RouterId store(int index) {
        return router(FIRST_STORE + index);
    }

    // module - entry - store 0 - store 1 - ... in a row, every store a basic pipe as it comes with an empty inventory
    // items come in at the entry, so the stores are further away the higher their number
    private void build(int count, boolean withModule) {
        module.sink = withModule ? new BulkStorageSink() : null;
        entry.sink = null;
        network.addRouter(MODULE, module, BUILT);
        network.addRouter(ENTRY, entry, BUILT);
        for (int index = 0; index < count; index++) {
            FakeNode store = new FakeNode(PipeKind.BASIC);
            store.inventories.add(Side.UP);
            stores.add(store);
            network.addRouter(store(index), store, BUILT);
        }
        network.updateLinks(MODULE, List.of(new Link(ENTRY, Side.EAST, 1)), BUILT);
        List<Link> entryLinks = new ArrayList<>(List.of(new Link(MODULE, Side.WEST, 1)));
        if (count > 0) {
            entryLinks.add(new Link(store(0), Side.EAST, 1));
        }
        network.updateLinks(ENTRY, entryLinks, BUILT);
        for (int index = 0; index < count; index++) {
            List<Link> links = new ArrayList<>();
            links.add(new Link(index == 0 ? ENTRY : store(index - 1), Side.WEST, 1));
            if (index + 1 < count) {
                links.add(new Link(store(index + 1), Side.EAST, 1));
            }
            network.updateLinks(store(index), links, BUILT);
        }
    }

    // long enough for the module to have looked into every store
    private void lookEverywhere() {
        for (int tick = 0; tick <= stores.size() / BulkStorage.LOOKS_PER_TICK; tick++) {
            network.tick(MODULE, ++now);
        }
    }

    // where an item that comes in at the entry is sent
    private Optional<RouterId> destinationOf(String name) {
        TravellingItem item = new TravellingItem(batch(name, 4), Side.UP, null);
        network.chooseExit(ENTRY, item, ++now);
        return item.destination();
    }

    private int looks() {
        return stores.stream().mapToInt(store -> store.looks).sum();
    }

    private int probes() {
        return stores.stream().mapToInt(store -> store.probes).sum();
    }

    @Test
    void aPipeIsAStoreWhenItIsADefaultRouteWithNothingButLists() {
        assertThat(BulkStorageSink.stores(List.of(new ItemSink(Set.of(), true)))).isTrue();
        assertThat(BulkStorageSink.stores(List.of(new ItemSink(Set.of(item("stone")), true)))).isTrue();
        assertThat(BulkStorageSink.stores(List.of(new ItemSink(Set.of(item("stone")), false)))).isFalse();
        assertThat(BulkStorageSink.stores(List.of(new ItemSink(Set.of(), true), new PolymorphicItemSink()))).isFalse();
        assertThat(BulkStorageSink.stores(List.of(new BulkStorageSink()))).isFalse();
        assertThat(BulkStorageSink.stores(List.of())).isFalse();
    }

    @Test
    void theModuleItselfTakesNothing() {
        assertThat(new BulkStorageSink().bid(item("stone"), Map.of(item("stone"), 5))).isEmpty();
        assertThat(new BulkStorageSink().takesUnlisted()).isFalse();
    }

    @Test
    void withoutTheModuleTheNearestDefaultRouteTakesTheItem() {
        build(3, false);
        stores.get(2).stock("stone", 10);
        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(0));
    }

    @Test
    void withTheModuleTheItemGoesWhereItIsAlreadyStored() {
        build(3, true);
        stores.get(2).stock("stone", 10);
        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(2));
    }

    @Test
    void anItemThatIsStoredNowhereStillGoesToTheNearestDefaultRoute() {
        build(3, true);
        stores.get(2).stock("stone", 10);
        lookEverywhere();

        assertThat(destinationOf("dirt")).contains(store(0));
    }

    @Test
    void ofTwoStoresThatHoldTheItemTheNearerOneTakesIt() {
        build(4, true);
        stores.get(1).stock("stone", 1);
        stores.get(3).stock("stone", 500);
        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(1));
    }

    @Test
    void whenTheStoreThatHoldsTheItemIsFullTheNextOneTakesIt() {
        build(4, true);
        stores.get(1).stock("stone", 1);
        stores.get(1).room = false;
        stores.get(3).stock("stone", 1);
        lookEverywhere();
        assertThat(destinationOf("stone")).contains(store(3));

        stores.get(3).room = false;
        assertThat(destinationOf("stone")).contains(store(0));
    }

    @Test
    void aPipeWithItsDefaultRouteSwitchedOffIsNoStore() {
        build(3, true);
        stores.get(2).stock("stone", 10);
        stores.get(2).sink = new ItemSink(Set.of(), false);
        network.sinksChanged(store(2));
        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(0));
    }

    @Test
    void aStoreStillTakesWhatIsOnItsList() {
        build(3, true);
        stores.get(1).sink = new ItemSink(Set.of(item("dirt")), true);
        network.sinksChanged(store(1));
        stores.get(2).stock("stone", 10);
        lookEverywhere();

        assertThat(destinationOf("dirt")).contains(store(1));
        assertThat(destinationOf("stone")).contains(store(2));
    }

    @Test
    void aNearerSinkThatListsTheItemBeatsTheStoreThatHoldsIt() {
        build(3, true);
        stores.get(0).sink = new ItemSink(Set.of(item("stone")), false);
        network.sinksChanged(store(0));
        stores.get(2).stock("stone", 10);
        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(0));
    }

    @Test
    void takingTheModuleOutEndsIt() {
        build(3, true);
        stores.get(2).stock("stone", 10);
        lookEverywhere();
        assertThat(destinationOf("stone")).contains(store(2));

        module.sink = null;
        network.sinksChanged(MODULE);

        assertThat(destinationOf("stone")).contains(store(0));
    }

    @Test
    void aModuleInAnotherNetworkDoesNothing() {
        build(3, true);
        network.updateLinks(MODULE, List.of(), BUILT);
        network.updateLinks(ENTRY, List.of(new Link(store(0), Side.EAST, 1)), BUILT);
        stores.get(2).stock("stone", 10);
        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(0));
    }

    @Test
    void anItemThatArrivesAtTheStoreThatHoldsItIsDelivered() {
        build(3, true);
        stores.get(2).stock("stone", 10);
        lookEverywhere();
        TravellingItem arriving = new TravellingItem(batch("stone", 4), Side.WEST, store(2), TransportMode.SORTED);

        assertThat(network.chooseExit(store(2), arriving, ++now)).contains(Side.UP);
    }

    @Test
    void whatWasPutIntoAStoreByHandIsNoticedAfterAWhile() {
        build(20, true);
        lookEverywhere();
        assertThat(destinationOf("stone")).contains(store(0));

        stores.get(15).stock("stone", 1);
        assertThat(destinationOf("stone")).contains(store(0));
        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(15));
    }

    @Test
    void whatWasTakenOutOfAStoreIsNotSentThereOnceTheModuleHasLookedAgain() {
        build(3, true);
        stores.get(2).stock("stone", 10);
        lookEverywhere();
        stores.get(2).contents.clear();
        // until then the store still gets it, it takes anything anyway
        assertThat(destinationOf("stone")).contains(store(2));

        lookEverywhere();

        assertThat(destinationOf("stone")).contains(store(0));
    }

    @Test
    void anExtractorSendsWhatItHoldsToTheStoreThatHoldsItToo() {
        build(3, true);
        stores.get(2).stock("stone", 10);
        lookEverywhere();
        entry.extractions.add(ExtractionMode.EXTRACTOR);
        entry.stock("stone", 40);
        now = 119;

        network.tick(ENTRY, ++now);

        assertThat(entry.shipments).hasSize(1);
        assertThat(entry.shipments.get(0).destination()).isEqualTo(store(2));
    }

    @Test
    void theModuleLooksIntoAFewStoresPerTickHoweverManyThereAre() {
        build(200, true);
        stores.forEach(store -> store.stock("stone", 1));
        network.tick(MODULE, ++now);
        stores.forEach(store -> store.looks = 0);

        for (int tick = 0; tick < 10; tick++) {
            network.tick(MODULE, ++now);
        }

        assertThat(looks()).isEqualTo(10 * BulkStorage.LOOKS_PER_TICK);
        assertThat(stores.stream().filter(store -> store.looks > 1)).isEmpty();
    }

    @Test
    void sortingAStoredItemAsksOnlyTheStoreThatHoldsItHoweverManyThereAre() {
        build(500, true);
        for (int index = 0; index < stores.size(); index++) {
            stores.get(index).stock("item " + index, 1);
        }
        lookEverywhere();
        stores.forEach(store -> {
            store.looks = 0;
            store.probes = 0;
        });

        assertThat(destinationOf("item 400")).contains(store(400));

        assertThat(looks()).isZero();
        assertThat(probes()).isEqualTo(1);
        assertThat(stores.get(400).probes).isEqualTo(1);
    }

    @Test
    void sortingCostsTheSameInALargeStoreAsInASmallOne() {
        build(500, true);
        for (int index = 0; index < stores.size(); index++) {
            stores.get(index).stock("item " + index % 5, 1);
        }
        lookEverywhere();
        stores.forEach(store -> {
            store.looks = 0;
            store.probes = 0;
        });

        // a hundred stores hold it, the nearest one has room
        assertThat(destinationOf("item 3")).contains(store(3));

        assertThat(probes()).isEqualTo(1);
        assertThat(looks()).isZero();
    }
}
