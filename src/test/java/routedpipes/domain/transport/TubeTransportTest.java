package routedpipes.domain.transport;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import routedpipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class TubeTransportTest {

    // a speedup tube
    private static final int LENGTH = 240;

    private final TubeTransport transport = new TubeTransport();
    private long clock;

    private static TubeItem item(int entry, int progress) {
        return TubeItem.restore(7, batch("stone", 3), router(1), router(2), TransportMode.REQUESTED, entry, progress);
    }

    private List<TubeItem> tick(int times) {
        List<TubeItem> through = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            through.addAll(transport.tick(clock++, LENGTH));
        }
        return through;
    }

    @Test
    void itemsDoFourFifthsOfABlockPerTick() {
        TubeItem item = item(0, 0);
        transport.insert(item);

        tick(1);

        assertThat(TubeTransport.SPEED).isEqualTo(PipeTransport.PIPE_LENGTH * 4 / 5);
        assertThat(item.progress()).isEqualTo(48);
    }

    @Test
    void anItemIsThroughAFourBlockTubeOnTheFifthTick() {
        TubeItem item = item(1, 0);
        transport.insert(item);

        assertThat(tick(4)).isEmpty();
        assertThat(tick(1)).containsExactly(item);
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void theSlowestAndTheFastestItemAreEquallyFastInATube() {
        TravellingItem slow = new TravellingItem(batch("stone", 1), Side.WEST, null);
        TravellingItem fast = new TravellingItem(batch("stone", 1), Side.WEST, router(1));
        fast.setSpeed(60);
        TubeItem first = TubeItem.entering(slow, 0);
        TubeItem second = TubeItem.entering(fast, 0);
        transport.insert(first);
        transport.insert(second);

        tick(2);

        assertThat(first.progress()).isEqualTo(96);
        assertThat(second.progress()).isEqualTo(96);
    }

    @Test
    void anItemHandedOverByAPipeDoesNotMoveTwiceInThatTick() {
        PipeTransport pipe = new PipeTransport();
        pipe.insert(TravellingItem.restore(7, batch("stone", 1), Side.WEST, null, null, TransportMode.UNCLAIMED, Side.EAST,
                50, TransportMode.UNCLAIMED.speed()));
        TravellingItem leaving = pipe.tick(5, travelling -> Optional.of(Side.EAST)).get(0).item();
        TubeItem item = TubeItem.entering(leaving, 0);
        transport.insert(item);

        transport.tick(5, LENGTH);

        // 50 + 24 is 14 steps past the end of the pipe, and that is where it still is
        assertThat(item.progress()).isEqualTo(14);
        transport.tick(6, LENGTH);
        assertThat(item.progress()).isEqualTo(14 + 48);
    }

    @Test
    void whatAnItemOvershootsATubeByCarriesOverToTheNext() {
        TubeItem item = item(0, 0);
        transport.insert(item);
        TubeItem through = tick(5).get(0);

        TubeItem next = through.enteringNext(1, LENGTH);

        // five ticks make 240 steps, exactly the tube
        assertThat(next.progress()).isZero();
        assertThat(item(0, 250).enteringNext(1, LENGTH).progress()).isEqualTo(10);
        assertThat(next.entry()).isEqualTo(1);
        assertThat(next.id()).isEqualTo(item.id());
        assertThat(next.batch()).isEqualTo(item.batch());
    }

    @Test
    void anItemHandedOverByATubeDoesNotMoveTwiceInThatTick() {
        transport.insert(item(0, 200));
        TubeItem through = transport.tick(9, LENGTH).get(0);
        TubeTransport nextTube = new TubeTransport();
        TubeItem next = through.enteringNext(0, LENGTH);
        nextTube.insert(next);

        nextTube.tick(9, LENGTH);

        assertThat(next.progress()).isEqualTo(8);
    }

    @Test
    void backInAPipeAnItemIsAsSlowAsItsModeSays() {
        transport.insert(item(0, 200));
        TubeItem through = transport.tick(9, LENGTH).get(0);

        TravellingItem leaving = through.leaving(Side.WEST);

        assertThat(leaving.speed()).isEqualTo(TransportMode.REQUESTED.speed());
        assertThat(leaving.id()).isEqualTo(7);
        assertThat(leaving.batch()).isEqualTo(batch("stone", 3));
        assertThat(leaving.origin()).isEqualTo(Side.WEST);
        assertThat(leaving.destination()).contains(router(1));
        assertThat(leaving.nextHop()).contains(router(2));
        assertThat(leaving.mode()).isEqualTo(TransportMode.REQUESTED);
        assertThat(leaving.decided()).isFalse();
        // it already moved in this tick
        assertThat(leaving.movedIn(9)).isTrue();
    }

    @Test
    void anItemThatCannotLeaveYetTriesAgainOnTheNextTick() {
        transport.insert(item(0, 200));
        TubeItem through = transport.tick(9, LENGTH).get(0);

        through.holdBefore(LENGTH);
        transport.insert(through);

        assertThat(transport.tick(9, LENGTH)).isEmpty();
        assertThat(transport.tick(10, LENGTH)).containsExactly(through);
    }

    @Test
    void aGuessedItemIsNotAddedTwice() {
        transport.insert(item(0, 100));

        transport.expect(item(0, 0));

        assertThat(transport.items()).hasSize(1);
        assertThat(transport.progressOf(7)).hasValue(100);
    }

    @Test
    void reportsChangesAViewerCouldNotPredictOnce() {
        assertThat(transport.pollChanged()).isFalse();

        transport.insert(item(0, 0));

        assertThat(transport.pollChanged()).isTrue();
        assertThat(transport.pollChanged()).isFalse();
        tick(5);
        assertThat(transport.pollChanged()).isFalse();
    }

    @Test
    void clearingHandsBackWhatWasInside() {
        TubeItem item = item(0, 0);
        transport.insert(item);

        assertThat(transport.clear()).containsExactly(item);
        assertThat(transport.isEmpty()).isTrue();
    }
}
