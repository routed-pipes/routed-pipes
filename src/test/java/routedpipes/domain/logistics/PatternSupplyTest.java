package routedpipes.domain.logistics;

import static routedpipes.testing.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.PatternSupply.Entry;
import org.junit.jupiter.api.Test;

class PatternSupplyTest {

    private static List<ItemBatch> slots(ItemBatch... held) {
        return new ArrayList<>(Arrays.asList(held));
    }

    private static PatternSupply pattern(SupplyMode mode, Entry... entries) {
        return new PatternSupply(List.of(entries), mode, true);
    }

    @Test
    void ordersWhatEverySlotIsShortOf() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)), new Entry(2, batch("iron", 4)));

        List<ItemBatch> orders = pattern.orders(slots(batch("coal", 3), null, null), type -> 0);

        assertThat(orders).containsExactly(batch("coal", 5), batch("iron", 4));
    }

    @Test
    void onlyTheNamedSlotCountsNotWhatLiesElsewhere() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(1, batch("coal", 8)));

        assertThat(pattern.orders(slots(batch("coal", 64), null), type -> 0)).containsExactly(batch("coal", 8));
    }

    @Test
    void aSlotHoldingSomethingElseIsLeftAlone() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)));

        assertThat(pattern.orders(slots(batch("dirt", 1)), type -> 0)).isEmpty();
        assertThat(pattern.placement(batch("coal", 8), slots(batch("dirt", 1)))).isEmpty();
    }

    @Test
    void aSlotTheInventoryDoesNotHaveIsSkipped() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(5, batch("coal", 8)));

        assertThat(pattern.orders(slots(null, null), type -> 0)).isEmpty();
        assertThat(pattern.placement(batch("coal", 8), slots(null, null))).isEmpty();
    }

    @Test
    void whatIsOnItsWayIsNotOrderedAgain() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)));

        assertThat(pattern.orders(slots(batch("coal", 2)), type -> 4)).containsExactly(batch("coal", 2));
        assertThat(pattern.orders(slots(batch("coal", 2)), type -> 6)).isEmpty();
    }

    @Test
    void oneDeliveryOnItsWayIsNotWaitedForByTwoSlots() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 8)), new Entry(1, batch("coal", 8)));

        assertThat(pattern.orders(slots(null, null), type -> 8)).containsExactly(batch("coal", 8));
        assertThat(pattern.orders(slots(null, null), type -> 10)).containsExactly(batch("coal", 6));
    }

    @Test
    void bulk50WaitsUntilTheSlotIsDownToHalf() {
        PatternSupply pattern = pattern(SupplyMode.BULK50, new Entry(0, batch("coal", 16)));

        assertThat(pattern.orders(slots(batch("coal", 9)), type -> 0)).isEmpty();
        assertThat(pattern.orders(slots(batch("coal", 8)), type -> 0)).containsExactly(batch("coal", 8));
    }

    @Test
    void bulk100WaitsUntilTheSlotIsEmpty() {
        PatternSupply pattern = pattern(SupplyMode.BULK100, new Entry(0, batch("coal", 16)));

        assertThat(pattern.orders(slots(batch("coal", 1)), type -> 0)).isEmpty();
        assertThat(pattern.orders(slots((ItemBatch) null), type -> 0)).containsExactly(batch("coal", 16));
    }

    @Test
    void aPatternNeverFillsAllTheRoomThereIs() {
        assertThat(pattern(SupplyMode.INFINITE).mode()).isEqualTo(SupplyMode.PARTIAL);
        assertThat(pattern(SupplyMode.FULL).mode()).isEqualTo(SupplyMode.FULL);
    }

    @Test
    void arrivingItemsFillTheirSlotUpToTheTarget() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(3, batch("coal", 8)));

        assertThat(pattern.placement(batch("coal", 5), slots(null, null, null, batch("coal", 2)))).isEqualTo(Map.of(3, 5));
        assertThat(pattern.placement(batch("coal", 20), slots(null, null, null, batch("coal", 2)))).isEqualTo(Map.of(3, 6));
        assertThat(pattern.placement(batch("coal", 5), slots(null, null, null, batch("coal", 8)))).isEmpty();
    }

    @Test
    void arrivingItemsFillSeveralSlotsInTheOrderTheyAreListed() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(2, batch("coal", 4)), new Entry(0, batch("coal", 4)));

        Map<Integer, Integer> placement = pattern.placement(batch("coal", 6), slots(null, null, null));

        assertThat(placement).containsExactly(Map.entry(2, 4), Map.entry(0, 2));
    }

    @Test
    void twoEntriesForOneSlotShareItsRoom() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 4)), new Entry(0, batch("coal", 4)));

        assertThat(pattern.placement(batch("coal", 10), slots((ItemBatch) null))).isEqualTo(Map.of(0, 4));
    }

    @Test
    void itemsThePatternDoesNotListAreNotPlaced() {
        PatternSupply pattern = pattern(SupplyMode.PARTIAL, new Entry(0, batch("coal", 4)));

        assertThat(pattern.placement(batch("iron", 4), slots((ItemBatch) null))).isEmpty();
        assertThat(pattern.lists(batch("coal", 1).type())).isTrue();
        assertThat(pattern.lists(batch("iron", 1).type())).isFalse();
    }
}
