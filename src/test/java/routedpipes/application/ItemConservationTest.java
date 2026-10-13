package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.PassiveSupplier;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.testing.FakeNode;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class ItemConservationTest {

    private static final RouterId SUPPLIER = router(1);
    private static final RouterId OTHER = router(2);
    private static final long BUILT = -100;
    private static final long NOW = 120;

    // FINDING: a sorted item cut at its destination and cut again for a new sink loses the first cut-off part
    @Test
    void itemCutTwiceInOneChoiceKeepsEverything() {
        LogisticsNetwork network = new LogisticsNetwork();
        FakeNode supplier = new FakeNode(PipeKind.CHASSIS);
        FakeNode other = new FakeNode(PipeKind.CHASSIS);
        // wants 3 more coal but its chest has no room right now
        supplier.inventories.add(Side.UP);
        supplier.sink = new PassiveSupplier(Map.of(new TestItem("coal"), 16));
        supplier.contents.put(new TestItem("coal"), 13);
        supplier.room = false;
        // wants a single coal and has room for it
        other.inventories.add(Side.UP);
        other.sink = new PassiveSupplier(Map.of(new TestItem("coal"), 1));
        network.addRouter(SUPPLIER, supplier, BUILT);
        network.addRouter(OTHER, other, BUILT);
        network.updateLinks(SUPPLIER, List.of(new Link(OTHER, Side.EAST, 1)), BUILT);
        network.updateLinks(OTHER, List.of(new Link(SUPPLIER, Side.WEST, 1)), BUILT);
        TravellingItem arriving = new TravellingItem(batch("coal", 6), Side.WEST, SUPPLIER, TransportMode.SORTED);

        network.chooseExit(SUPPLIER, arriving, NOW);

        int kept = arriving.batch().count();
        int cut = arriving.takeCutOff().map(ItemBatch::count).orElse(0);
        assertThat(kept + cut).as("coal kept %d plus cut off %d", kept, cut).isEqualTo(6);
    }
}
