package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.PatternSupply;
import routedpipes.domain.logistics.PatternSupply.Entry;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.SupplyMode;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeNode;
import routedpipes.testing.FakeNode.Shipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogisticsNetworkPatternTest {

    private static final RouterId PROVIDER = router(1);
    private static final RouterId SUPPLIER = router(2);
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode supplier = new FakeNode(PipeKind.SUPPLIER);

    @BeforeEach
    void connect() {
        supplier.inventories.add(Side.UP);
        network.addRouter(PROVIDER, provider, BUILT);
        network.addRouter(SUPPLIER, supplier, BUILT);
        network.updateLinks(PROVIDER, List.of(new Link(SUPPLIER, Side.EAST, 1)), BUILT);
        network.updateLinks(SUPPLIER, List.of(new Link(PROVIDER, Side.WEST, 1)), BUILT);
    }

    private void pattern(SupplyMode mode, Entry... entries) {
        supplier.patternSupplies = List.of(new PatternSupply(List.of(entries), mode, true));
    }

    private static List<ItemBatch> slots(ItemBatch... held) {
        return new ArrayList<>(Arrays.asList(held));
    }

    @Test
    void ordersWhatItsSlotsAreShortOf() {
        provider.stock("coal", 64).stock("iron", 64);
        pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)), new Entry(2, batch("iron", 4)));
        supplier.slots = slots(batch("coal", 3), null, null);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 5), SUPPLIER), new Shipment(batch("iron", 4), SUPPLIER));
    }

    @Test
    void doesNotOrderAgainWhileTheItemsAreOnTheirWay() {
        provider.stock("coal", 64);
        pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)));
        supplier.slots = slots((ItemBatch) null);

        network.tick(SUPPLIER, 0);
        network.tick(SUPPLIER, 20);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 8), SUPPLIER));
    }

    @Test
    void takesWhatThereIsUnlessItWantsAllOrNothing() {
        provider.stock("coal", 5);
        pattern(SupplyMode.FULL, new Entry(0, batch("coal", 8)));
        supplier.slots = slots((ItemBatch) null);

        network.tick(SUPPLIER, 0);
        assertThat(provider.shipments).isEmpty();

        pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)));
        network.tick(SUPPLIER, 20);
        assertThat(provider.shipments).containsExactly(new Shipment(batch("coal", 5), SUPPLIER));
    }

    @Test
    void anOrderCostsEnergyAndIsNotPlacedWithoutIt() {
        provider.stock("coal", 64);
        provider.power = 0;
        supplier.power = 0;
        pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)));
        supplier.slots = slots((ItemBatch) null);

        network.tick(SUPPLIER, 0);

        assertThat(provider.shipments).isEmpty();
    }
}
