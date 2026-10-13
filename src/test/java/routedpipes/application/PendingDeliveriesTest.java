package routedpipes.application;

import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import routedpipes.domain.item.ItemType;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class PendingDeliveriesTest {

    private static final ItemType STONE = new TestItem("stone");

    private final PendingDeliveries pending = new PendingDeliveries();

    @Test
    void countsWhatIsOnItsWay() {
        pending.expect(router(1), STONE, 10, 100);
        pending.expect(router(1), STONE, 5, 200);

        assertThat(pending.expected(router(1), STONE, 0)).isEqualTo(15);
        assertThat(pending.expected(router(2), STONE, 0)).isZero();
    }

    @Test
    void arrivalsUseUpTheOldestEntriesFirst() {
        pending.expect(router(1), STONE, 10, 100);
        pending.expect(router(1), STONE, 5, 200);

        pending.arrived(router(1), STONE, 12);

        assertThat(pending.expected(router(1), STONE, 150)).isEqualTo(3);
    }

    @Test
    void forgetsItemsThatNeverArrived() {
        pending.expect(router(1), STONE, 10, 100);

        assertThat(pending.expected(router(1), STONE, 99)).isEqualTo(10);
        assertThat(pending.expected(router(1), STONE, 100)).isZero();
    }

    @Test
    void ignoresArrivalsNobodyWaitedFor() {
        pending.arrived(router(1), STONE, 4);
        pending.expect(router(1), STONE, 0, 100);

        assertThat(pending.expected(router(1), STONE, 0)).isZero();
    }

    @Test
    void forgetsEverythingForARemovedRouter() {
        pending.expect(router(1), STONE, 10, 100);

        pending.forget(router(1));

        assertThat(pending.expected(router(1), STONE, 0)).isZero();
    }

    @Test
    void listsWhatIsStillOnItsWayToOneRouter() {
        ItemType coal = new TestItem("coal");
        pending.expect(router(1), STONE, 10, 100);
        pending.expect(router(1), STONE, 5, 200);
        pending.expect(router(1), coal, 3, 100);
        pending.expect(router(2), coal, 7, 200);

        assertThat(pending.expectedAt(router(1), 0)).containsExactly(java.util.Map.entry(STONE, 15), java.util.Map.entry(coal, 3));
        assertThat(pending.expectedAt(router(1), 150)).containsExactly(java.util.Map.entry(STONE, 5));
        assertThat(pending.expectedAt(router(3), 0)).isEmpty();
    }
}
