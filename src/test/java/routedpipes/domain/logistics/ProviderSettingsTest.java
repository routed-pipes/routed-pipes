package routedpipes.domain.logistics;

import static routedpipes.testing.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import routedpipes.domain.item.ItemBatch;
import org.junit.jupiter.api.Test;

class ProviderSettingsTest {

    private final List<ItemBatch> slots = Arrays.asList(batch("coal", 10), null, batch("iron", 5), batch("coal", 1), batch("iron", 7));

    private static ProviderSettings mode(ProviderMode mode) {
        return new ProviderSettings(mode, Set.of(), false);
    }

    @Test
    void normalHandsOutEverything() {
        assertThat(mode(ProviderMode.NORMAL).takeable(slots)).containsExactly(10, 0, 5, 1, 7);
    }

    @Test
    void leavesTheFirstSlotAlone() {
        assertThat(mode(ProviderMode.LEAVE_FIRST).takeable(slots)).containsExactly(0, 0, 5, 1, 7);
    }

    @Test
    void leavesTheLastSlotAlone() {
        assertThat(mode(ProviderMode.LEAVE_LAST).takeable(slots)).containsExactly(10, 0, 5, 1, 0);
    }

    @Test
    void leavesFirstAndLastSlotAlone() {
        assertThat(mode(ProviderMode.LEAVE_FIRST_AND_LAST).takeable(slots)).containsExactly(0, 0, 5, 1, 0);
    }

    @Test
    void leavesOneItemInEveryStack() {
        assertThat(mode(ProviderMode.LEAVE_ONE_PER_STACK).takeable(slots)).containsExactly(9, 0, 4, 0, 6);
    }

    @Test
    void leavesOneItemOfEveryType() {
        assertThat(mode(ProviderMode.LEAVE_ONE_PER_TYPE).takeable(slots)).containsExactly(9, 0, 4, 1, 7);
    }

    @Test
    void stockAddsUpWhatMayBeTaken() {
        assertThat(mode(ProviderMode.LEAVE_ONE_PER_STACK).stock(slots))
                .containsOnly(entry("coal", 9), entry("iron", 10));
    }

    @Test
    void listedItemsAreAllItGives() {
        ProviderSettings settings = new ProviderSettings(ProviderMode.NORMAL, Set.of(batch("iron", 1).type()), false);

        assertThat(settings.stock(slots)).containsOnly(entry("iron", 12));
    }

    @Test
    void listedItemsAreKeptWhenExcluding() {
        ProviderSettings settings = new ProviderSettings(ProviderMode.NORMAL, Set.of(batch("iron", 1).type()), true);

        assertThat(settings.stock(slots)).containsOnly(entry("coal", 11));
    }

    @Test
    void anEmptyListHoldsNothingBackEitherWay() {
        assertThat(new ProviderSettings(ProviderMode.NORMAL, Set.of(), true).offers(batch("coal", 1).type())).isTrue();
        assertThat(new ProviderSettings(ProviderMode.NORMAL, Set.of(), false).offers(batch("coal", 1).type())).isTrue();
    }

    @Test
    void modesCycle() {
        assertThat(ProviderMode.NORMAL.next()).isEqualTo(ProviderMode.LEAVE_FIRST);
        assertThat(ProviderMode.LEAVE_ONE_PER_TYPE.next()).isEqualTo(ProviderMode.NORMAL);
    }

    private static java.util.Map.Entry<routedpipes.domain.item.ItemType, Integer> entry(String name, int count) {
        return java.util.Map.entry(batch(name, 1).type(), count);
    }
}
