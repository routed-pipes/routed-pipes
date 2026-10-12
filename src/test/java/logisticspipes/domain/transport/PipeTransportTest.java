package logisticspipes.domain.transport;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class PipeTransportTest {

    private final PipeTransport transport = new PipeTransport();

    private static TravellingItem item() {
        return new TravellingItem(batch("stone", 1), Side.WEST, null);
    }

    private List<Departure> tick(int times, ExitChooser chooser) {
        List<Departure> departures = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            departures.addAll(transport.tick(chooser));
        }
        return departures;
    }

    @Test
    void leavesThroughChosenExitAfterCrossingThePipe() {
        TravellingItem item = item();
        transport.insert(item);

        assertThat(tick(PipeTransport.TICKS_PER_PIPE - 1, travelling -> Optional.of(Side.EAST))).isEmpty();
        assertThat(transport.tick(travelling -> Optional.of(Side.EAST)))
                .containsExactly(new Departure(item, Optional.of(Side.EAST)));
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void asksForTheExitOnceOnEntry() {
        transport.insert(item());
        List<Integer> askedAt = new ArrayList<>();

        tick(PipeTransport.TICKS_PER_PIPE, travelling -> {
            askedAt.add(travelling.progress());
            return Optional.of(Side.UP);
        });

        assertThat(askedAt).containsExactly(0);
    }

    @Test
    void reportsChangesOnlyWhenSomethingUnpredictableHappened() {
        transport.insert(item());
        assertThat(transport.pollChanged()).isTrue();
        assertThat(transport.pollChanged()).isFalse();

        transport.tick(travelling -> Optional.of(Side.EAST));
        assertThat(transport.pollChanged()).isTrue();

        tick(PipeTransport.TICKS_PER_PIPE - 1, travelling -> Optional.of(Side.EAST));
        assertThat(transport.pollChanged()).isFalse();
    }

    @Test
    void viewerMovesItemsAlongAndHandsThemBackAtTheEnd() {
        TravellingItem item = TravellingItem.restore(1, batch("stone", 1), Side.WEST, null, null, Side.EAST, 1);
        transport.insert(item);

        for (int i = 0; i < PipeTransport.TICKS_PER_PIPE - 2; i++) {
            assertThat(transport.advance()).isEmpty();
        }
        assertThat(transport.items()).extracting(TravellingItem::progress).containsExactly(PipeTransport.TICKS_PER_PIPE - 1);

        assertThat(transport.advance()).containsExactly(new Departure(item, Optional.of(Side.EAST)));
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void viewerLetsUnconfirmedItemsFadeOutAtTheCentre() {
        transport.expect(item());

        for (int i = 0; i < PipeTransport.CENTRE - 1; i++) {
            assertThat(transport.advance()).isEmpty();
        }
        assertThat(transport.isEmpty()).isFalse();

        assertThat(transport.advance()).isEmpty();
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void viewerDoesNotExpectAnItemItAlreadyHas() {
        TravellingItem item = TravellingItem.restore(7, batch("stone", 1), Side.WEST, null, null, Side.EAST, 3);
        transport.insert(item);

        transport.expect(item.enteringFrom(Side.WEST));
        transport.expect(TravellingItem.restore(8, batch("stone", 1), Side.WEST, null, null, null, 0));

        assertThat(transport.items()).extracting(TravellingItem::id).containsExactly(7L, 8L);
        assertThat(transport.progressOf(7)).hasValue(3);
        assertThat(transport.progressOf(99)).isEmpty();
    }

    @Test
    void viewerKeepsRealItemsWithoutExitWhereTheyAre() {
        transport.insert(TravellingItem.restore(1, batch("stone", 1), Side.WEST, null, null, null, 2));

        for (int i = 0; i < PipeTransport.TICKS_PER_PIPE; i++) {
            assertThat(transport.advance()).isEmpty();
        }

        assertThat(transport.items()).extracting(TravellingItem::progress).containsExactly(2);
    }

    @Test
    void waitsForAnExitBeforeGivingUpAtTheCentre() {
        TravellingItem item = item();
        transport.insert(item);

        assertThat(tick(PipeTransport.MAX_WAIT + PipeTransport.CENTRE - 1, travelling -> Optional.empty())).isEmpty();
        assertThat(transport.tick(travelling -> Optional.empty())).containsExactly(new Departure(item, Optional.empty()));
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void movesOnAsSoonAsAnExitTurnsUp() {
        TravellingItem item = item();
        transport.insert(item);
        tick(7, travelling -> Optional.empty());
        assertThat(item.progress()).isZero();

        List<Departure> departures = tick(PipeTransport.TICKS_PER_PIPE, travelling -> Optional.of(Side.EAST));

        assertThat(departures).containsExactly(new Departure(item, Optional.of(Side.EAST)));
    }

    @Test
    void movesItemsIndependently() {
        TravellingItem first = item();
        TravellingItem second = item();
        transport.insert(first);
        tick(3, travelling -> Optional.of(Side.EAST));
        transport.insert(second);

        List<Departure> departures = tick(PipeTransport.TICKS_PER_PIPE - 3, travelling -> Optional.of(Side.EAST));

        assertThat(departures).extracting(Departure::item).containsExactly(first);
        assertThat(transport.items()).containsExactly(second);
    }

    @Test
    void restoredItemKeepsItsExitAndProgress() {
        TravellingItem item = TravellingItem.restore(1, batch("stone", 1), Side.WEST, router(7), null, Side.UP, 8);
        transport.insert(item);

        List<Departure> departures = tick(2, travelling -> Optional.of(Side.DOWN));

        assertThat(departures).containsExactly(new Departure(item, Optional.of(Side.UP)));
    }

    @Test
    void clearHandsBackEverythingInside() {
        TravellingItem item = item();
        transport.insert(item);

        assertThat(transport.clear()).containsExactly(item);
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void enteringTheNextPipeKeepsBatchAndDestination() {
        TravellingItem item = TravellingItem.restore(1, batch("stone", 4), Side.WEST, router(7), null, Side.EAST, 10);

        TravellingItem next = item.enteringFrom(Side.WEST);

        assertThat(next.id()).isEqualTo(item.id());
        assertThat(next.batch()).isEqualTo(batch("stone", 4));
        assertThat(next.destination()).contains(router(7));
        assertThat(next.origin()).isEqualTo(Side.WEST);
        assertThat(next.exit()).isEmpty();
        assertThat(next.progress()).isZero();
    }

    @Test
    void unroutedPipePassesItemsStraightOn() {
        assertThat(UnroutedPipe.chooseExit(Set.of(Side.WEST, Side.EAST), item(), router -> Optional.empty())).contains(Side.EAST);
    }

    @Test
    void unroutedPipeNeverSendsItemsBack() {
        assertThat(UnroutedPipe.chooseExit(Set.of(Side.WEST), item(), router -> Optional.of(Side.WEST))).isEmpty();
    }

    @Test
    void forkSendsItemTowardsItsNextRouter() {
        TravellingItem item = item();
        item.passTo(router(5));

        assertThat(UnroutedPipe.chooseExit(Set.of(Side.EAST, Side.UP, Side.WEST), item,
                router -> router.equals(router(5)) ? Optional.of(Side.EAST) : Optional.empty())).contains(Side.EAST);
    }

    @Test
    void forkFallsBackToTheSameBranchWhenTheRouterIsUnknown() {
        TravellingItem item = item();
        item.passTo(router(5));

        assertThat(UnroutedPipe.chooseExit(Set.of(Side.EAST, Side.UP, Side.WEST), item, router -> Optional.empty()))
                .contains(Side.UP);
        assertThat(UnroutedPipe.chooseExit(Set.of(Side.EAST, Side.UP, Side.WEST), item(), router -> Optional.of(Side.EAST)))
                .contains(Side.UP);
    }

    @Test
    void straightPipeDoesNotAskWhereTheRouterIs() {
        TravellingItem item = item();
        item.passTo(router(5));

        assertThat(UnroutedPipe.chooseExit(Set.of(Side.WEST, Side.EAST), item, router -> {
            throw new AssertionError("no fork here");
        })).contains(Side.EAST);
    }

    @Test
    void nextRouterTravelsWithTheItem() {
        TravellingItem item = item();
        item.passTo(router(5));

        assertThat(item.enteringFrom(Side.WEST).nextHop()).contains(router(5));
    }
}
