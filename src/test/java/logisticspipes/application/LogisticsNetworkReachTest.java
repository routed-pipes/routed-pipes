package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SupplyMode;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import org.junit.jupiter.api.Test;

// what pipes order on their own in the same tick shares one look at the inventories of the network
class LogisticsNetworkReachTest {

    private static final RouterId SUPPLIER = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId OTHER_SUPPLIER = router(3);
    private static final long BUILT = -100;
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode supplier = new FakeNode(PipeKind.SUPPLIER);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode otherSupplier = new FakeNode(PipeKind.SUPPLIER);

    // all three in a row, one apart, linked both ways
    private void connect() {
        List<RouterId> ids = List.of(SUPPLIER, PROVIDER, OTHER_SUPPLIER);
        List<FakeNode> nodes = List.of(supplier, provider, otherSupplier);
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

    private void wants(FakeNode supplier, String... names) {
        supplier.inventories.add(Side.UP);
        supplier.supplyMode = SupplyMode.PARTIAL;
        for (String name : names) {
            supplier.supplyTargets.put(batch(name, 1).type(), 40);
        }
    }

    @Test
    void aSupplierShortOfSeveralItemsLooksIntoTheProviderOnce() {
        connect();
        provider.stock("stone", 64);
        wants(supplier, "iron", "gold", "coal", "sand");

        network.tick(SUPPLIER, NOW);

        assertThat(provider.looks).isEqualTo(1);
        assertThat(provider.shipments).isEmpty();
    }

    @Test
    void twoSuppliersAskingInOneTickShareTheLook() {
        connect();
        provider.stock("stone", 64);
        wants(supplier, "iron");
        wants(otherSupplier, "iron");

        network.tick(SUPPLIER, NOW);
        network.tick(OTHER_SUPPLIER, NOW);

        assertThat(provider.looks).isEqualTo(1);
    }

    @Test
    void theNextRoundLooksAgain() {
        connect();
        provider.stock("stone", 64);
        wants(supplier, "iron");
        network.tick(SUPPLIER, NOW);
        provider.stock("iron", 8);

        network.tick(SUPPLIER, NOW + LogisticsNetwork.SUPPLY_INTERVAL);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("iron", 8), SUPPLIER));
    }

    @Test
    void whatOneSupplierOrderedIsNotOfferedToTheNextInTheSameTick() {
        connect();
        provider.stock("stone", 64);
        wants(supplier, "stone");
        wants(otherSupplier, "stone");

        network.tick(SUPPLIER, NOW);
        network.tick(OTHER_SUPPLIER, NOW);

        assertThat(provider.shipments).containsExactly(new Shipment(batch("stone", 40), SUPPLIER),
                new Shipment(batch("stone", 24), OTHER_SUPPLIER));
    }

    @Test
    void aPlayerAskingTwiceInOneTickSeesWhatChangedInBetween() {
        connect();
        provider.stock("stone", 1);
        assertThat(network.preview(SUPPLIER, batch("stone", 5), NOW).missing()).isNotEmpty();

        provider.stock("stone", 5);

        assertThat(network.preview(SUPPLIER, batch("stone", 5), NOW).missing()).isEmpty();
    }
}
