package routedpipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import routedpipes.domain.item.ItemType;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class VoidSinkTest {

    private static final ItemType STONE = new TestItem("stone");
    private static final ItemType DIRT = new TestItem("dirt");

    @Test
    void withNothingListedItTakesEverything() {
        VoidSink sink = new VoidSink(Set.of());

        assertThat(sink.priority(STONE, Map.of())).hasValue(VoidSink.PRIORITY);
        assertThat(sink.priority(DIRT, Map.of())).hasValue(VoidSink.PRIORITY);
        assertThat(sink.takesUnlisted()).isTrue();
    }

    @Test
    void withAListItTakesOnlyWhatIsListed() {
        VoidSink sink = new VoidSink(Set.of(STONE));

        assertThat(sink.priority(STONE, Map.of())).hasValue(VoidSink.PRIORITY);
        assertThat(sink.priority(DIRT, Map.of())).isEmpty();
        assertThat(sink.takesUnlisted()).isFalse();
        assertThat(sink.wants()).containsExactly(STONE);
    }

    @Test
    void itIsWantedLessThanAnyDefaultRoute() {
        assertThat(VoidSink.PRIORITY).isLessThan(ItemSink.DEFAULT_ROUTE_PRIORITY);
    }

    @Test
    void aStackCostsWhatADefaultRouteCosts() {
        assertThat(new VoidSink(Set.of()).energy()).isEqualTo(new ItemSink(Set.of(), true).energy());
        assertThat(new VoidSink(Set.of()).bid(STONE, Map.of())).contains(new SinkBid(VoidSink.PRIORITY, 2, 0));
    }
}
