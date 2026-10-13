package routedpipes.application;

import static routedpipes.testing.Fixtures.fluid;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import routedpipes.domain.fluid.FluidSupply;
import routedpipes.domain.fluid.FluidSupply.Minimum;
import routedpipes.domain.fluid.FluidType;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeFluidNode;
import routedpipes.testing.FakeFluidNode.Sent;
import routedpipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

// what suppliers and satellites do on their own
class FluidUpkeepTest {

    private static final RouterId TARGET = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final FluidType WATER = fluid("water", 1).type();

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FluidLogistics fluids = network.fluids();
    private final FakeFluidNode targetTanks = new FakeFluidNode();
    private final FakeFluidNode provider = new FakeFluidNode();
    private FakeNode target;

    private void network(PipeKind targetKind) {
        target = new FakeNode(targetKind);
        target.fluid = targetTanks;
        FakeNode providerNode = new FakeNode(PipeKind.FLUID_PROVIDER);
        providerNode.fluid = provider;
        providerNode.power = 0;
        network.addRouter(TARGET, target, 0);
        network.addRouter(PROVIDER, providerNode, 0);
        network.updateLinks(TARGET, List.of(new Link(PROVIDER, Side.EAST, 2)), 0);
        network.updateLinks(PROVIDER, List.of(new Link(TARGET, Side.WEST, 2)), 0);
    }

    private void supplier(int target, boolean partial, Minimum minimum, int stocked) {
        network(PipeKind.FLUID_SUPPLIER);
        targetTanks.supply = new FluidSupply(WATER, target, partial, minimum);
        targetTanks.stocked.add(stocked);
    }

    private void run(long from, long to) {
        for (long now = from; now <= to; now++) {
            fluids.tick(TARGET, now);
            fluids.tick(PROVIDER, now);
        }
    }

    @Test
    void aSupplierOrdersWhatItsTankIsMissing() {
        supplier(8000, false, Minimum.NONE, 5000);
        provider.stock(fluid("water", 16000));

        run(1, 120);

        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 3000), TARGET));
    }

    @Test
    void aSupplierDoesNotOrderAgainWhatIsOnItsWay() {
        supplier(8000, false, Minimum.NONE, 5000);
        provider.stock(fluid("water", 16000));

        run(1, 320);

        assertThat(provider.sent).hasSize(1);
    }

    @Test
    void aSupplierOrdersAgainOnceTheFluidGotLost() {
        supplier(8000, false, Minimum.NONE, 5000);
        provider.stock(fluid("water", 16000));
        run(1, 120);

        fluids.lost(TARGET, fluid("water", 3000));
        run(121, 220);

        assertThat(provider.sent).hasSize(2);
    }

    @Test
    void aSupplierStopsOrderingOnceTheFluidIsInItsTank() {
        supplier(8000, false, Minimum.NONE, 5000);
        provider.stock(fluid("water", 16000));
        run(1, 120);

        fluids.arrived(TARGET, fluid("water", 3000), 0);
        targetTanks.stocked.set(0, 8000);
        run(121, 320);

        assertThat(provider.sent).hasSize(1);
    }

    @Test
    void aSupplierWaitsUntilItsMinimumIsMissing() {
        supplier(8000, false, Minimum.FIVE_BUCKETS, 5000);
        provider.stock(fluid("water", 16000));

        run(1, 120);
        assertThat(provider.sent).isEmpty();

        targetTanks.stocked.set(0, 3000);
        run(121, 220);
        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 5000), TARGET));
    }

    @Test
    void aSupplierThatWantsItAllOrdersNothingWhenThereIsNotEnough() {
        supplier(8000, false, Minimum.NONE, 0);
        provider.stock(fluid("water", 2000));

        run(1, 120);

        assertThat(provider.sent).isEmpty();
    }

    @Test
    void aSupplierThatTakesPartsOrdersWhatThereIs() {
        supplier(8000, true, Minimum.NONE, 0);
        provider.stock(fluid("water", 2000));

        run(1, 120);

        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 2000), TARGET));
    }

    @Test
    void aSupplyOrderCostsEnergyAndIsNotPlacedWithoutIt() {
        supplier(8000, false, Minimum.NONE, 5000);
        provider.stock(fluid("water", 16000));
        target.power = LogisticsNetwork.ENERGY_PER_SUPPLY_ORDER - 1;

        run(1, 120);
        assertThat(provider.sent).isEmpty();

        target.power = LogisticsNetwork.ENERGY_PER_SUPPLY_ORDER;
        run(121, 220);
        assertThat(provider.sent).hasSize(1);
        assertThat(target.power).isZero();
    }

    @Test
    void everyTankNextToASupplierIsFilledUp() {
        supplier(4000, false, Minimum.NONE, 1000);
        targetTanks.stocked.add(4000);
        targetTanks.stocked.add(0);
        provider.stock(fluid("water", 16000));

        run(1, 120);

        // 3000 for the first tank, and the third still misses 1000 with those on their way
        assertThat(provider.sent.stream().mapToInt(sent -> sent.batch().amount()).sum()).isEqualTo(4000);
    }

    @Test
    void aSatelliteAsksAgainForFluidThatGotLost() {
        network(PipeKind.FLUID_SATELLITE);
        provider.stock(fluid("water", 16000));

        fluids.lost(TARGET, fluid("water", 1500));
        run(1, 60);

        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 1500), TARGET));
    }

    @Test
    void aSatelliteAsksAgainForFluidThatDidNotFitIntoItsTanks() {
        network(PipeKind.FLUID_SATELLITE);
        provider.stock(fluid("water", 16000));

        fluids.arrived(TARGET, fluid("water", 4000), 700);
        run(1, 60);

        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 700), TARGET));
    }

    @Test
    void aSatelliteAsksAgainForAnOrderItsProviderDropped() {
        network(PipeKind.FLUID_SATELLITE);
        provider.stock(fluid("water", 2000));
        fluids.request(TARGET, fluid("water", 2000), 0);
        provider.stock.clear();
        run(1, 6);

        provider.stock(fluid("water", 2000));
        run(7, 60);

        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 2000), TARGET));
    }

    @Test
    void aSatelliteTakesWhatPartOfItsLostFluidThereIsAndKeepsAskingForTheRest() {
        network(PipeKind.FLUID_SATELLITE);
        provider.stock(fluid("water", 500));
        fluids.lost(TARGET, fluid("water", 1500));
        run(1, 60);
        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 500), TARGET));

        provider.stock(fluid("water", 5000));
        run(61, 120);

        assertThat(provider.sent).containsExactly(new Sent(fluid("water", 500), TARGET), new Sent(fluid("water", 1000), TARGET));
    }

    @Test
    void aSatelliteReordersForFree() {
        network(PipeKind.FLUID_SATELLITE);
        provider.stock(fluid("water", 16000));
        target.power = 0;

        fluids.lost(TARGET, fluid("water", 1500));
        run(1, 60);

        assertThat(provider.sent).hasSize(1);
    }

    @Test
    void aRequestPipeDoesNotAskAgainForLostFluid() {
        network(PipeKind.FLUID_REQUEST);
        provider.stock(fluid("water", 16000));

        fluids.lost(TARGET, fluid("water", 1500));
        run(1, 60);

        assertThat(provider.sent).isEmpty();
    }
}
