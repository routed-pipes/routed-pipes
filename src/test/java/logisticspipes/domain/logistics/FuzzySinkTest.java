package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.item.FuzzyFlags;
import logisticspipes.domain.item.FuzzyType;
import logisticspipes.testing.Fixtures.WornItem;
import org.junit.jupiter.api.Test;

class FuzzySinkTest {

    private static final WornItem PICKAXE = WornItem.fresh("pickaxe");
    private static final WornItem OAK = WornItem.fresh("oak_planks", "planks");
    private static final WornItem BIRCH = WornItem.fresh("birch_planks", "planks");
    private static final FuzzySink SINK = new FuzzySink(List.of(
            new FuzzyType(PICKAXE, new FuzzyFlags(false, true, false, false)),
            new FuzzyType(OAK, new FuzzyFlags(true, false, false, false))));

    @Test
    void whatMatchesAListedItemIsWantedLikeAListedItem() {
        assertThat(SINK.priority(PICKAXE.worn(9), Map.of())).hasValue(ItemSink.FILTER_PRIORITY);
        assertThat(SINK.priority(BIRCH, Map.of())).hasValue(ItemSink.FILTER_PRIORITY);
        assertThat(SINK.priority(OAK, Map.of())).hasValue(ItemSink.FILTER_PRIORITY);
    }

    @Test
    void everySlotGoesByItsOwnFlags() {
        assertThat(SINK.priority(PICKAXE.carrying("sharp"), Map.of())).isEmpty();
        assertThat(SINK.priority(BIRCH.worn(1), Map.of())).hasValue(ItemSink.FILTER_PRIORITY);
        assertThat(SINK.priority(WornItem.fresh("stone"), Map.of())).isEmpty();
    }

    @Test
    void itIsAskedAboutEveryItemBecauseItCannotListWhatMatches() {
        assertThat(SINK.takesUnlisted()).isTrue();
        assertThat(SINK.wants()).isEmpty();
        assertThat(new FuzzySink(List.of()).takesUnlisted()).isFalse();
    }
}
