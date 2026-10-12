package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class LogisticsNetworkAmountsTest {

    private static final RouterId A = router(1);
    private static final RouterId B = router(2);
    private static final RouterId FAR = router(3);
    private static final ItemType STONE = new TestItem("stone");
    private static final ItemType PLANKS = new TestItem("planks");

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode a = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode b = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode far = new FakeNode(PipeKind.PROVIDER);

    // A and B are linked, FAR is on its own
    private void build() {
        network.addRouter(A, a, 0);
        network.addRouter(B, b, 0);
        network.addRouter(FAR, far, 0);
        network.updateLinks(A, List.of(new Link(B, Side.EAST, 2)), 0);
        network.updateLinks(B, List.of(new Link(A, Side.WEST, 2)), 0);
    }

    @Test
    void amountsAddUpWhatEveryProviderInReachHas() {
        build();
        a.stock("stone", 10).inventoryRefs.put(Side.UP, new InventoryRef(1));
        b.stock("stone", 5).stock("planks", 3).inventoryRefs.put(Side.UP, new InventoryRef(2));

        assertThat(network.amounts(A)).containsEntry(STONE, 15).containsEntry(PLANKS, 3);
    }

    @Test
    void theOwnInventoryCountsUnlikeForARequest() {
        build();
        a.stock("stone", 10).inventoryRefs.put(Side.UP, new InventoryRef(1));

        assertThat(network.amounts(A)).containsEntry(STONE, 10);
        assertThat(network.available(A)).doesNotContainKey(STONE);
    }

    @Test
    void anInventoryWithTwoProvidersOnItCountsOnce() {
        build();
        a.stock("stone", 10).inventoryRefs.put(Side.UP, new InventoryRef(1));
        b.stock("stone", 10).inventoryRefs.put(Side.UP, new InventoryRef(1));

        assertThat(network.amounts(A)).containsEntry(STONE, 10);
    }

    @Test
    void whatCannotBeReachedDoesNotCount() {
        build();
        far.stock("stone", 99).inventoryRefs.put(Side.UP, new InventoryRef(3));

        assertThat(network.amounts(A)).isEmpty();
    }

    @Test
    void backlogAddsUpTheOpenOrdersOfEveryCrafterInReach() {
        build();
        a.outputOrders.add(new OutputOrder(B, batch("planks", 4)));
        b.outputOrders.add(new OutputOrder(A, batch("planks", 8)));
        b.outputOrders.add(new OutputOrder(A, batch("stone", 1)));
        far.outputOrders.add(new OutputOrder(A, batch("planks", 64)));

        assertThat(network.craftingBacklog(A)).containsEntry(PLANKS, 12).containsEntry(STONE, 1).hasSize(2);
    }

    @Test
    void aNetworkWithNothingToDoHasNoBacklog() {
        build();

        assertThat(network.craftingBacklog(A)).isEmpty();
    }
}
