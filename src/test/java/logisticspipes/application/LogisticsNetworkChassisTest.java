package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PassiveSupplier;
import logisticspipes.domain.logistics.PolymorphicItemSink;
import logisticspipes.domain.logistics.Terminus;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkChassisTest {

    private static final RouterId CHASSIS = router(1);
    private static final RouterId STORE = router(2);
    private static final RouterId REQUEST = router(3);
    // a tick on which every kind of upkeep is due
    private static final long NOW = 120;

    // the network was put together well before the tests start ticking it
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode chassis = new FakeNode(PipeKind.CHASSIS);
    private final FakeNode store = new FakeNode(PipeKind.BASIC);
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    // chassis - store - request in a row
    private void connect() {
        chassis.sink = new ItemSink(Set.of(), false);
        network.sinksChanged(CHASSIS);
        network.addRouter(CHASSIS, chassis, BUILT);
        network.addRouter(STORE, store, BUILT);
        network.addRouter(REQUEST, request, BUILT);
        network.updateLinks(CHASSIS, List.of(new Link(STORE, Side.EAST, 1)), BUILT);
        network.updateLinks(STORE, List.of(new Link(CHASSIS, Side.WEST, 1), new Link(REQUEST, Side.EAST, 1)), BUILT);
        network.updateLinks(REQUEST, List.of(new Link(STORE, Side.WEST, 1)), BUILT);
    }

    @Test
    void extractorPushesItemsToWhoeverTakesThem() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("stone", 40);
        store.inventories.add(Side.UP);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).containsExactly(new Shipment(batch("stone", ExtractionMode.EXTRACTOR.amount()), STORE));
    }

    @Test
    void extractorLeavesItemsNobodyWants() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("stone", 40);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).isEmpty();
    }

    @Test
    void extractorSkipsToAnItemSomebodyWants() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("stone", 40).stock("dirt", 3);
        store.inventories.add(Side.UP);
        store.sink = new ItemSink(Set.of(item("dirt")), false);
        network.sinksChanged(STORE);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).containsExactly(new Shipment(batch("dirt", 3), STORE));
    }

    @Test
    void extractorNeverSendsItemsToItsOwnChassis() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.sink = new ItemSink(Set.of(), true);
        network.sinksChanged(CHASSIS);
        chassis.stock("stone", 40);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).isEmpty();
    }

    @Test
    void extractorWaitsWhileTheNetworkIsSettling() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("stone", 40);
        store.inventories.add(Side.UP);
        network.updateLinks(CHASSIS, List.of(new Link(STORE, Side.EAST, 2)), NOW - 5);

        network.tick(CHASSIS, NOW);
        assertThat(chassis.shipments).isEmpty();

        network.tick(CHASSIS, NOW + 20);
        assertThat(chassis.shipments).hasSize(1);
    }

    @Test
    void noUpkeepInTheTickTheNetworkChanges() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("stone", 40);
        store.inventories.add(Side.UP);
        network.addRouter(router(7), new FakeNode(PipeKind.BASIC), NOW);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).isEmpty();
    }

    @Test
    void chassisWithoutExtractorKeepsItsItems() {
        connect();
        chassis.stock("stone", 40);
        store.inventories.add(Side.UP);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).isEmpty();
    }

    @Test
    void providerModuleMakesStockOrderable() {
        connect();
        chassis.stock("stone", 40);
        assertThat(network.available(REQUEST)).isEmpty();

        chassis.provides = true;

        assertThat(network.available(REQUEST)).containsOnly(Map.entry(item("stone"), 40));
        assertThat(network.request(REQUEST, batch("stone", 5), NOW).started()).isTrue();
        assertThat(chassis.shipments).containsExactly(new Shipment(batch("stone", 5), REQUEST));
    }

    @Test
    void chassisTakesItemsItsSinkModuleAsksFor() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new ItemSink(Set.of(item("stone")), false);
        network.sinksChanged(CHASSIS);
        var stone = new logisticspipes.domain.transport.TravellingItem(batch("stone", 4), Side.UP, null);
        var dirt = new logisticspipes.domain.transport.TravellingItem(batch("dirt", 4), Side.UP, null);

        assertThat(network.chooseExit(STORE, stone, NOW)).contains(Side.WEST);
        assertThat(network.chooseExit(STORE, dirt, NOW)).isEmpty();
    }

    @Test
    void quickSortMovesWholeStacksToPipesThatAskedByName() {
        connect();
        chassis.extractions.add(ExtractionMode.QUICK_SORT);
        chassis.stock("stone", 200);
        store.inventories.add(Side.UP);
        store.sink = new ItemSink(Set.of(item("stone")), false);
        network.sinksChanged(STORE);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).containsExactly(new Shipment(batch("stone", 64), STORE));
    }

    @Test
    void quickSortIgnoresDefaultRoutes() {
        connect();
        chassis.extractions.add(ExtractionMode.QUICK_SORT);
        chassis.stock("stone", 200);
        store.inventories.add(Side.UP);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).isEmpty();
    }

    @Test
    void passiveSupplierBeatsItemSinkBeatsTerminusBeatsDefaultRoute() {
        assertThat(PassiveSupplier.PRIORITY).isGreaterThan(ItemSink.FILTER_PRIORITY);
        assertThat(ItemSink.FILTER_PRIORITY).isGreaterThan(Terminus.PRIORITY);
        assertThat(Terminus.PRIORITY).isGreaterThan(ItemSink.DEFAULT_ROUTE_PRIORITY);
    }

    @Test
    void lowStockOnAPassiveSupplierWinsOverANearerItemSink() {
        connect();
        store.inventories.add(Side.UP);
        store.sink = new ItemSink(Set.of(item("coal")), false);
        network.sinksChanged(STORE);
        chassis.inventories.add(Side.UP);
        chassis.sink = new PassiveSupplier(Map.of(item("coal"), 16));
        network.sinksChanged(CHASSIS);

        assertThat(network.chooseExit(STORE, new TravellingItem(batch("coal", 4), Side.DOWN, null), NOW)).contains(Side.WEST);
    }

    @Test
    void extractorDoesNotFeedAnotherPipeOnTheSameInventory() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("stone", 40);
        chassis.inventoryRefs.put(Side.UP, new InventoryRef(7));
        store.inventories.add(Side.UP);
        store.inventoryRefs.put(Side.UP, new InventoryRef(7));

        network.tick(CHASSIS, NOW);
        assertThat(chassis.shipments).isEmpty();
        assertThat(chassis.power).isEqualTo(1_000_000);

        store.inventoryRefs.put(Side.UP, new InventoryRef(8));
        network.tick(CHASSIS, NOW + 20);
        assertThat(chassis.shipments).hasSize(1);
    }

    @Test
    void itemComingOutOfAnInventoryIsNotSunkBackIntoItThroughAnotherPipe() {
        connect();
        store.inventories.add(Side.UP);
        store.inventoryRefs.put(Side.UP, new InventoryRef(7));
        request.inventories.add(Side.UP);
        request.inventoryRefs.put(Side.UP, new InventoryRef(7));
        TravellingItem fromTheSharedChest = new TravellingItem(batch("stone", 4), Side.UP, null);

        assertThat(network.chooseExit(REQUEST, fromTheSharedChest, NOW)).isEmpty();

        TravellingItem fromElsewhere = new TravellingItem(batch("stone", 4), Side.DOWN, null);
        assertThat(network.chooseExit(REQUEST, fromElsewhere, NOW)).contains(Side.WEST);
    }

    @Test
    void terminusTakesListedItemsBeforeANearerDefaultRoute() {
        connect();
        request.inventories.add(Side.UP);
        FakeNode far = new FakeNode(PipeKind.CHASSIS);
        far.inventories.add(Side.UP);
        far.sink = new Terminus(Set.of(item("stone")));
        network.sinksChanged(router(4));
        network.addRouter(router(4), far, BUILT);
        network.updateLinks(REQUEST, List.of(new Link(STORE, Side.WEST, 1), new Link(router(4), Side.EAST, 5)), BUILT);
        store.inventories.add(Side.UP);

        TravellingItem stone = new TravellingItem(batch("stone", 4), Side.DOWN, null);
        TravellingItem dirt = new TravellingItem(batch("dirt", 4), Side.DOWN, null);

        assertThat(network.chooseExit(REQUEST, stone, NOW)).contains(Side.EAST);
        assertThat(network.chooseExit(REQUEST, dirt, NOW)).contains(Side.WEST);
    }

    @Test
    void passiveSupplierStopsTakingItemsOnceStocked() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new PassiveSupplier(Map.of(item("coal"), 16));
        network.sinksChanged(CHASSIS);
        chassis.contents.put(item("coal"), 10);

        assertThat(network.chooseExit(STORE, new TravellingItem(batch("coal", 4), Side.UP, null), NOW)).contains(Side.WEST);

        chassis.contents.put(item("coal"), 16);
        assertThat(network.chooseExit(STORE, new TravellingItem(batch("coal", 4), Side.UP, null), NOW)).isEmpty();
        assertThat(network.chooseExit(STORE, new TravellingItem(batch("dirt", 4), Side.UP, null), NOW)).isEmpty();
    }

    @Test
    void polymorphicSinkTakesWhatItAlreadyHolds() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new PolymorphicItemSink();
        network.sinksChanged(CHASSIS);
        chassis.contents.put(item("stone"), 1);

        assertThat(network.chooseExit(STORE, new TravellingItem(batch("stone", 4), Side.UP, null), NOW)).contains(Side.WEST);
        assertThat(network.chooseExit(STORE, new TravellingItem(batch("dirt", 4), Side.UP, null), NOW)).isEmpty();
    }

    @Test
    void extractorNeedsPower() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("stone", 40);
        store.inventories.add(Side.UP);
        chassis.power = 0;
        store.power = 0;
        request.power = 0;

        network.tick(CHASSIS, NOW);
        assertThat(chassis.shipments).isEmpty();

        store.power = ExtractionMode.EXTRACTOR.energyFor(ExtractionMode.EXTRACTOR.amount()) + new ItemSink(Set.of(), true).energy();
        network.tick(CHASSIS, NOW + 20);
        assertThat(chassis.shipments).hasSize(1);
        assertThat(store.power).isZero();
    }

    @Test
    void quickSortRunsEverySixTicksAndPaysPerStack() {
        connect();
        chassis.extractions.add(ExtractionMode.QUICK_SORT);
        chassis.stock("stone", 200);
        store.inventories.add(Side.UP);
        store.sink = new ItemSink(Set.of(item("stone")), false);
        network.sinksChanged(STORE);
        int before = chassis.power;

        network.tick(CHASSIS, NOW + 1);
        assertThat(chassis.shipments).isEmpty();

        network.tick(CHASSIS, NOW + 6);
        assertThat(chassis.shipments).containsExactly(new Shipment(batch("stone", 64), STORE));
        assertThat(before - chassis.power).isEqualTo(1000 + 2);
    }

    @Test
    void itemExtractionUpgradesAddToWhatAnExtractorMoves() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.itemExtraction = 3;
        chassis.stock("stone", 200);
        store.inventories.add(Side.UP);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).containsExactly(new Shipment(batch("stone", 64), STORE));
    }

    @Test
    void stackExtractionUpgradesMoveMoreKindsOfItemsAtATime() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stackExtraction = 1;
        chassis.stock("stone", 64);
        chassis.stock("coal", 64);
        chassis.stock("iron", 64);
        store.inventories.add(Side.UP);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).containsExactly(new Shipment(batch("stone", 64), STORE), new Shipment(batch("coal", 16), STORE));
    }

    @Test
    void actionSpeedUpgradesMakeAnExtractorWorkMoreOften() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.actionSpeed = 2;
        chassis.stock("stone", 200);
        store.inventories.add(Side.UP);

        for (long tick = NOW; tick < NOW + 20; tick++) {
            network.tick(CHASSIS, tick);
        }

        assertThat(chassis.shipments).hasSize(4);
    }

    @Test
    void sinksChargeWhatTheOriginalDidAtTwoEnergyPerUnit() {
        assertThat(new ItemSink(Set.of(), true).energy()).isEqualTo(2);
        assertThat(new Terminus(Set.of()).energy()).isEqualTo(4);
        assertThat(new PassiveSupplier(Map.of()).energy()).isEqualTo(4);
        assertThat(new PolymorphicItemSink().energy()).isEqualTo(6);
        assertThat(ExtractionMode.EXTRACTOR.energyFor(16)).isEqualTo(160);
        assertThat(ExtractionMode.QUICK_SORT.energyFor(64)).isEqualTo(1000);
        assertThat(LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM).isEqualTo(2);
        assertThat(LogisticsNetwork.ENERGY_PER_CRAFTED_ITEM).isEqualTo(20);
    }

    @Test
    void passiveSupplierTakesOnlyWhatIsMissingAndLeavesTheRest() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new PassiveSupplier(Map.of(item("coal"), 16));
        network.sinksChanged(CHASSIS);
        chassis.contents.put(item("coal"), 10);
        TravellingItem item = new TravellingItem(batch("coal", 20), Side.UP, null);

        assertThat(network.chooseExit(STORE, item, NOW)).contains(Side.WEST);

        assertThat(item.batch()).isEqualTo(batch("coal", 6));
        assertThat(item.takeCutOff()).contains(batch("coal", 14));
    }

    @Test
    void sinkIsAskedAgainWhenTheItemArrives() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new PassiveSupplier(Map.of(item("coal"), 16));
        network.sinksChanged(CHASSIS);
        chassis.contents.put(item("coal"), 13);
        TravellingItem arriving = new TravellingItem(batch("coal", 6), Side.EAST, CHASSIS, TransportMode.SORTED);

        assertThat(network.chooseExit(CHASSIS, arriving, NOW)).contains(Side.UP);
        assertThat(arriving.batch()).isEqualTo(batch("coal", 3));
        assertThat(arriving.takeCutOff()).contains(batch("coal", 3));

        chassis.contents.put(item("coal"), 16);
        TravellingItem tooLate = new TravellingItem(batch("coal", 6), Side.EAST, CHASSIS, TransportMode.SORTED);
        assertThat(network.chooseExit(CHASSIS, tooLate, NOW)).isEmpty();
        assertThat(tooLate.destination()).isEmpty();
    }

    @Test
    void extractorSendsNoMoreThanTheSinkWantsAndAsSortedItems() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.stock("coal", 40);
        store.inventories.add(Side.UP);
        store.sink = new PassiveSupplier(Map.of(item("coal"), 5));
        network.sinksChanged(STORE);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).containsExactly(new Shipment(batch("coal", 5), STORE));
        assertThat(chassis.lastMode).isEqualTo(TransportMode.SORTED);
    }

    @Test
    void sortedItemAtAFullSinkLooksForAnotherOne() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new ItemSink(Set.of(item("stone")), false);
        network.sinksChanged(CHASSIS);
        chassis.room = false;
        store.inventories.add(Side.UP);
        TravellingItem item = new TravellingItem(batch("stone", 4), Side.EAST, CHASSIS, TransportMode.SORTED);

        assertThat(network.chooseExit(CHASSIS, item, NOW)).contains(Side.EAST);
        assertThat(item.destination()).contains(STORE);
    }

    @Test
    void passiveSupplierCountsWhatIsAlreadyOnItsWay() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new PassiveSupplier(Map.of(item("coal"), 16));
        network.sinksChanged(CHASSIS);
        chassis.contents.put(item("coal"), 10);
        network.chooseExit(STORE, new TravellingItem(batch("coal", 4), Side.UP, null), NOW);
        TravellingItem second = new TravellingItem(batch("coal", 20), Side.UP, null);

        assertThat(network.chooseExit(STORE, second, NOW)).contains(Side.WEST);
        assertThat(second.batch()).isEqualTo(batch("coal", 2));

        TravellingItem third = new TravellingItem(batch("coal", 20), Side.UP, null);
        network.chooseExit(STORE, third, NOW);
        assertThat(third.destination()).isNotEqualTo(Optional.of(CHASSIS));
    }

    @Test
    void advancedExtractorSkipsWhatItsListKeepsBack() {
        connect();
        chassis.extractions.add(ExtractionMode.EXTRACTOR);
        chassis.extractionList = Set.of(item("stone"));
        chassis.extractionExcludes = false;
        chassis.stock("coal", 8);
        chassis.stock("stone", 8);
        store.inventories.add(Side.UP);

        network.tick(CHASSIS, NOW);

        assertThat(chassis.shipments).containsExactly(new Shipment(batch("stone", 8), STORE));
    }

    @Test
    void passiveSupplierCountsWhatItsInventoryHoldsEvenWhereNothingCanBeTakenOut() {
        connect();
        chassis.inventories.add(Side.UP);
        chassis.sink = new PassiveSupplier(Map.of(item("coal"), 16));
        network.sinksChanged(CHASSIS);
        chassis.held = Map.of(item("coal"), 16);
        TravellingItem coal = new TravellingItem(batch("coal", 4), Side.UP, null);

        network.chooseExit(STORE, coal, NOW);

        assertThat(coal.destination()).isNotEqualTo(Optional.of(CHASSIS));
    }
}
