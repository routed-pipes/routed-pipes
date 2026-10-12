package logisticspipes.application;

import static logisticspipes.testing.Fixtures.fluid;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.PackedFluid;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeFluidNode;
import logisticspipes.testing.FakeFluidNode.Sent;
import logisticspipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class FluidRequestTest {

    private static final RouterId REQUESTER = router(1);
    private static final RouterId NEAR = router(2);
    private static final RouterId FAR = router(3);
    private static final FluidType WATER = fluid("water", 1).type();
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FluidLogistics fluids = network.fluids();
    private final FakeNode requester = new FakeNode(PipeKind.FLUID_REQUEST);
    private final FakeFluidNode requesterTanks = new FakeFluidNode();
    private final FakeFluidNode near = new FakeFluidNode();
    private final FakeFluidNode far = new FakeFluidNode();

    // requester - near - far in a line
    private void line() {
        requester.fluid = requesterTanks;
        FakeNode nearNode = new FakeNode(PipeKind.FLUID_PROVIDER);
        nearNode.fluid = near;
        FakeNode farNode = new FakeNode(PipeKind.FLUID_PROVIDER);
        farNode.fluid = far;
        network.addRouter(REQUESTER, requester, 0);
        network.addRouter(NEAR, nearNode, 0);
        network.addRouter(FAR, farNode, 0);
        network.updateLinks(REQUESTER, List.of(new Link(NEAR, Side.EAST, 2)), 0);
        network.updateLinks(NEAR, List.of(new Link(REQUESTER, Side.WEST, 2), new Link(FAR, Side.EAST, 5)), 0);
        network.updateLinks(FAR, List.of(new Link(NEAR, Side.WEST, 5)), 0);
    }

    // providers work every sixth tick
    private void tickAll(long from, long to) {
        for (long now = from; now <= to; now++) {
            fluids.tick(NEAR, now);
            fluids.tick(FAR, now);
        }
    }

    @Test
    void listsWhatAllProvidersHold() {
        line();
        near.stock(fluid("water", 3000));
        far.stock(fluid("water", 2000)).stock(fluid("lava", 500));

        assertThat(fluids.available(REQUESTER)).containsOnly(Map.entry(WATER, 5000), Map.entry(fluid("lava", 1).type(), 500));
    }

    @Test
    void aPipeThatDoesNotProvideOffersNothing() {
        line();
        near.stock(fluid("water", 3000));
        near.provides = false;

        assertThat(fluids.available(REQUESTER)).isEmpty();
        assertThat(fluids.request(REQUESTER, fluid("water", 1000), NOW).started()).isFalse();
    }

    @Test
    void anOrderIsSentInHelpingsOfFiveBuckets() {
        line();
        near.stock(fluid("water", 12000));

        assertThat(fluids.request(REQUESTER, fluid("water", 7000), NOW).started()).isTrue();
        tickAll(NOW + 1, NOW + 5);
        assertThat(near.sent).containsExactly(new Sent(fluid("water", 5000), REQUESTER));

        tickAll(NOW + 6, NOW + 30);
        assertThat(near.sent).containsExactly(new Sent(fluid("water", 5000), REQUESTER), new Sent(fluid("water", 2000), REQUESTER));
    }

    @Test
    void theNearestProviderIsAskedFirstAndTheNextOneForTheRest() {
        line();
        near.stock(fluid("water", 1000));
        far.stock(fluid("water", 4000));

        assertThat(fluids.request(REQUESTER, fluid("water", 3000), NOW).started()).isTrue();
        tickAll(NOW + 1, NOW + 12);

        assertThat(near.sent).containsExactly(new Sent(fluid("water", 1000), REQUESTER));
        assertThat(far.sent).containsExactly(new Sent(fluid("water", 2000), REQUESTER));
    }

    @Test
    void anOrderForMoreThanThereIsOrdersNothingAndTellsWhatIsMissing() {
        line();
        near.stock(fluid("water", 1000));

        RequestOutcome outcome = fluids.request(REQUESTER, fluid("water", 3000), NOW);
        tickAll(NOW + 1, NOW + 12);

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.powered()).isTrue();
        assertThat(outcome.missing()).containsOnly(Map.entry(new PackedFluid(WATER), 2000));
        assertThat(near.sent).isEmpty();
    }

    @Test
    void fluidThatIsPromisedIsNotPromisedAgain() {
        line();
        near.stock(fluid("water", 5000));
        fluids.request(REQUESTER, fluid("water", 4000), NOW);

        assertThat(fluids.available(REQUESTER)).containsOnly(Map.entry(WATER, 1000));
        assertThat(fluids.request(REQUESTER, fluid("water", 2000), NOW).started()).isFalse();
    }

    @Test
    void aRequestCostsEnergyOnlyWhenItStarts() {
        line();
        near.stock(fluid("water", 5000));
        requester.power = 100;

        fluids.request(REQUESTER, fluid("water", 9000), NOW);
        assertThat(requester.power).isEqualTo(100);

        fluids.request(REQUESTER, fluid("water", 4000), NOW);
        assertThat(requester.power).isEqualTo(100 - FluidLogistics.ENERGY_PER_REQUEST);
    }

    @Test
    void withoutPowerNothingIsOrdered() {
        line();
        near.stock(fluid("water", 5000));
        requester.power = 0;
        ((FakeNode) network.node(NEAR)).power = 0;
        ((FakeNode) network.node(FAR)).power = 0;

        RequestOutcome outcome = fluids.request(REQUESTER, fluid("water", 4000), NOW);
        tickAll(NOW + 1, NOW + 12);

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.powered()).isFalse();
        assertThat(near.sent).isEmpty();
    }

    @Test
    void aProviderOnTheTankOfTheRequesterIsLeftOut() {
        line();
        requesterTanks.tanks.add(new InventoryRef(7));
        near.tanks.add(new InventoryRef(7));
        near.stock(fluid("water", 5000));
        far.stock(fluid("water", 5000));

        assertThat(fluids.available(REQUESTER)).containsOnly(Map.entry(WATER, 5000));
        fluids.request(REQUESTER, fluid("water", 1000), NOW);
        tickAll(NOW + 1, NOW + 12);

        assertThat(near.sent).isEmpty();
        assertThat(far.sent).hasSize(1);
    }

    @Test
    void twoProvidersOnOneTankOfferItOnce() {
        line();
        near.tanks.add(new InventoryRef(7));
        far.tanks.add(new InventoryRef(7));
        near.stock(fluid("water", 5000));
        far.stock(fluid("water", 5000));

        assertThat(fluids.available(REQUESTER)).containsOnly(Map.entry(WATER, 5000));
    }

    @Test
    void anOrderTheProviderCanNoLongerServeIsDropped() {
        line();
        near.stock(fluid("water", 5000));
        fluids.request(REQUESTER, fluid("water", 4000), NOW);
        near.stock.clear();

        tickAll(NOW + 1, NOW + 12);
        near.stock(fluid("water", 5000));
        tickAll(NOW + 13, NOW + 30);

        assertThat(near.sent).isEmpty();
        assertThat(fluids.available(REQUESTER)).containsOnly(Map.entry(WATER, 5000));
    }
}
