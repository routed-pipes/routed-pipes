package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.fluid;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import logisticspipes.application.FluidLogistics.Shipment;
import logisticspipes.domain.fluid.FluidSink;
import logisticspipes.domain.fluid.PackedFluid;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeFluidNode;
import logisticspipes.testing.FakeNode;
import org.junit.jupiter.api.Test;

class FluidLogisticsTest {

    private static final RouterId A = router(1);
    private static final RouterId B = router(2);
    private static final RouterId C = router(3);
    private static final long NOW = 100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FluidLogistics fluids = network.fluids();
    private final FakeNode a = new FakeNode(PipeKind.FLUID_INSERTION);
    private final FakeNode b = new FakeNode(PipeKind.FLUID_BASIC);
    private final FakeNode c = new FakeNode(PipeKind.FLUID_BASIC);
    private final FakeFluidNode fluidA = new FakeFluidNode();
    private final FakeFluidNode fluidB = new FakeFluidNode();
    private final FakeFluidNode fluidC = new FakeFluidNode();

    // A - B - C in a line running west to east, B is 2 from A and C is 5 from B
    private void line() {
        a.fluid = fluidA;
        b.fluid = fluidB;
        c.fluid = fluidC;
        network.addRouter(A, a, 0);
        network.addRouter(B, b, 0);
        network.addRouter(C, c, 0);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2)), 0);
        network.updateLinks(B, List.of(new Link(A, Side.WEST, 2), new Link(C, Side.EAST, 5)), 0);
        network.updateLinks(C, List.of(new Link(B, Side.WEST, 5)), 0);
    }

    private static void sinks(FakeFluidNode node, String name, FluidSink.Priority priority, int room) {
        node.sink = new FluidSink(Set.of(fluid(name, 1).type()), priority);
        node.room = room;
    }

    @Test
    void pumpedInFluidGoesToTheSinkThatListsIt() {
        line();
        sinks(fluidC, "water", FluidSink.Priority.FLUID_SINK, 8000);

        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(C, 3000));
    }

    @Test
    void nobodyTakesAFluidThatIsNotListedAnywhere() {
        line();
        sinks(fluidC, "water", FluidSink.Priority.FLUID_SINK, 8000);

        assertThat(fluids.insert(A, fluid("lava", 3000), NOW)).isEmpty();
    }

    @Test
    void theNearestSinkWins() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 8000);
        sinks(fluidC, "water", FluidSink.Priority.FLUID_SINK, 8000);

        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(B, 3000));
    }

    @Test
    void aFluidSinkWinsOverANearerTerminus() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.TERMINUS, 8000);
        sinks(fluidC, "water", FluidSink.Priority.FLUID_SINK, 8000);

        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(C, 3000));
    }

    @Test
    void aSinkOnlyGetsWhatItHasRoomFor() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 1000);

        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(B, 1000));
    }

    @Test
    void whatIsOnItsWayToASinkCountsAgainstItsRoom() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 4000);
        sinks(fluidC, "water", FluidSink.Priority.FLUID_SINK, 8000);

        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(B, 3000));
        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(B, 1000));
        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(C, 3000));
    }

    @Test
    void arrivedFluidNoLongerCountsAsOnItsWay() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 4000);
        fluids.insert(A, fluid("water", 4000), NOW);

        fluids.arrived(B, fluid("water", 4000), 0);

        assertThat(fluids.insert(A, fluid("water", 4000), NOW)).contains(new Shipment(B, 4000));
    }

    @Test
    void lostFluidNoLongerCountsAsOnItsWay() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 4000);
        fluids.insert(A, fluid("water", 4000), NOW);

        fluids.lost(B, fluid("water", 4000));

        assertThat(fluids.insert(A, fluid("water", 4000), NOW)).contains(new Shipment(B, 4000));
    }

    @Test
    void aSinkOnTheTankTheFluidCameOutOfIsLeftOut() {
        line();
        fluidA.tanks.add(new InventoryRef(7));
        fluidB.tanks.add(new InventoryRef(7));
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 8000);
        sinks(fluidC, "water", FluidSink.Priority.FLUID_SINK, 8000);

        assertThat(fluids.insert(A, fluid("water", 3000), NOW)).contains(new Shipment(C, 3000));
    }

    @Test
    void sendingCostsOneEnergyForEveryHundredMillibuckets() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 8000);
        a.power = 100;
        b.power = 0;
        c.power = 0;

        fluids.insert(A, fluid("water", 3099), NOW);

        assertThat(a.power).isEqualTo(100 - 30);
    }

    @Test
    void withoutPowerNothingIsSentAndNothingCountsAsOnItsWay() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 4000);
        a.power = 0;
        b.power = 0;
        c.power = 0;

        assertThat(fluids.insert(A, fluid("water", 4000), NOW)).isEmpty();

        a.power = 1000;
        assertThat(fluids.insert(A, fluid("water", 4000), NOW)).contains(new Shipment(B, 4000));
    }

    @Test
    void lessThanAHundredMillibucketsTravelForFree() {
        line();
        sinks(fluidB, "water", FluidSink.Priority.FLUID_SINK, 4000);
        a.power = 0;
        b.power = 0;
        c.power = 0;

        assertThat(fluids.insert(A, fluid("water", 99), NOW)).contains(new Shipment(B, 99));
    }

    @Test
    void fluidThatDidNotFitIsSentOnForFree() {
        line();
        sinks(fluidC, "water", FluidSink.Priority.FLUID_SINK, 8000);
        a.power = 0;
        b.power = 0;
        c.power = 0;

        assertThat(fluids.reroute(B, fluid("water", 2000), NOW)).contains(new Shipment(C, 2000));
    }

    @Test
    void noItemSinkEverTakesPackedFluid() {
        line();
        FakeNode basic = new FakeNode(PipeKind.BASIC);
        basic.inventories.add(Side.UP);
        RouterId d = router(4);
        network.addRouter(d, basic, 0);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2), new Link(d, Side.UP, 1)), 0);
        network.updateLinks(d, List.of(new Link(A, Side.DOWN, 1)), 0);
        TravellingItem packed = new TravellingItem(new ItemBatch(new PackedFluid(fluid("water", 1).type()), 1), Side.WEST, null);
        TravellingItem item = new TravellingItem(batch("stone", 1), Side.WEST, null);

        assertThat(network.chooseExit(A, item, NOW)).contains(Side.UP);
        assertThat(network.chooseExit(A, packed, NOW)).isEmpty();
        assertThat(packed.mode()).isEqualTo(TransportMode.UNCLAIMED);
    }
}
