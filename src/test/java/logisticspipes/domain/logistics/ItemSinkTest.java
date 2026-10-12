package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import logisticspipes.domain.item.ItemType;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class ItemSinkTest {

    private static final ItemType STONE = new TestItem("stone");
    private static final ItemType DIRT = new TestItem("dirt");

    @Test
    void listedItemsAreWantedMost() {
        ItemSink sink = new ItemSink(Set.of(STONE), true);

        assertThat(sink.priority(STONE)).hasValue(ItemSink.FILTER_PRIORITY);
        assertThat(sink.priority(DIRT)).hasValue(ItemSink.DEFAULT_ROUTE_PRIORITY);
        assertThat(ItemSink.FILTER_PRIORITY).isGreaterThan(ItemSink.DEFAULT_ROUTE_PRIORITY);
    }

    @Test
    void withoutDefaultRouteOnlyListedItemsAreTaken() {
        ItemSink sink = new ItemSink(Set.of(STONE), false);

        assertThat(sink.priority(STONE)).hasValue(ItemSink.FILTER_PRIORITY);
        assertThat(sink.priority(DIRT)).isEmpty();
    }

    @Test
    void emptySinkWithoutDefaultRouteTakesNothing() {
        assertThat(new ItemSink(Set.of(), false).priority(STONE)).isEmpty();
    }
}
