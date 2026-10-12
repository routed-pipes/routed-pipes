package logisticspipes.domain.transport;

import static logisticspipes.testing.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import logisticspipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class NearestItemTest {

    private static final int LENGTH = PipeTransport.PIPE_LENGTH;

    // an item that came in from the origin side, has got that far and leaves through the exit, null for none
    private static TravellingItem item(long id, Side origin, Side exit, int progress) {
        return TravellingItem.restore(id, batch("stone", 1), origin, null, null, TransportMode.UNCLAIMED, exit, progress, 1);
    }

    @Test
    void anEmptyPipeHasNoNearestItem() {
        assertThat(NearestItem.to(List.of(), 0.5, 0.5, 0.5)).isEmpty();
    }

    @Test
    void theOnlyItemIsTheNearestWhereverItIs() {
        TravellingItem only = item(1, Side.WEST, Side.EAST, 0);

        assertThat(NearestItem.to(List.of(only), 1, 1, 1)).containsSame(only);
    }

    @Test
    void anItemComesInFromItsOriginSide() {
        assertThat(NearestItem.position(item(1, Side.WEST, Side.EAST, 0))).containsExactly(0, 0.5, 0.5);
        assertThat(NearestItem.position(item(1, Side.WEST, Side.EAST, LENGTH / 4))).containsExactly(0.25, 0.5, 0.5);
        assertThat(NearestItem.position(item(1, Side.UP, Side.EAST, 0))).containsExactly(0.5, 1, 0.5);
        assertThat(NearestItem.position(item(1, Side.NORTH, Side.EAST, 0))).containsExactly(0.5, 0.5, 0);
    }

    @Test
    void pastTheCentreItHeadsForItsExit() {
        assertThat(NearestItem.position(item(1, Side.WEST, Side.EAST, LENGTH / 2))).containsExactly(0.5, 0.5, 0.5);
        assertThat(NearestItem.position(item(1, Side.WEST, Side.DOWN, LENGTH * 3 / 4))).containsExactly(0.5, 0.25, 0.5);
        assertThat(NearestItem.position(item(1, Side.WEST, Side.SOUTH, LENGTH))).containsExactly(0.5, 0.5, 1);
    }

    @Test
    void withoutAnExitItStaysAtTheCentre() {
        assertThat(NearestItem.position(item(1, Side.WEST, null, LENGTH * 3 / 4))).containsExactly(0.5, 0.5, 0.5);
    }

    @Test
    void picksTheItemClosestToThePoint() {
        TravellingItem comingIn = item(1, Side.WEST, Side.EAST, 0);
        TravellingItem atTheCentre = item(2, Side.WEST, Side.EAST, LENGTH / 2);
        TravellingItem goingUp = item(3, Side.WEST, Side.UP, LENGTH);
        List<TravellingItem> items = List.of(comingIn, atTheCentre, goingUp);

        assertThat(NearestItem.to(items, 0.1, 0.5, 0.5)).containsSame(comingIn);
        assertThat(NearestItem.to(items, 0.6, 0.5, 0.4)).containsSame(atTheCentre);
        assertThat(NearestItem.to(items, 0.5, 0.9, 0.5)).containsSame(goingUp);
    }

    @Test
    void ofTwoEquallyNearItemsTheFirstWins() {
        TravellingItem first = item(1, Side.WEST, Side.EAST, LENGTH / 2);
        TravellingItem second = item(2, Side.UP, Side.DOWN, LENGTH / 2);

        assertThat(NearestItem.to(List.of(first, second), 0.5, 0.5, 0.5)).containsSame(first);
    }
}
