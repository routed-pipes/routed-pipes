package logisticspipes.domain.transport;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class PipeTransportTest {

    // an unclaimed item does 24 of the 60 steps of a pipe per tick, so it is through on the third tick
    private static final int TICKS_UNCLAIMED = 3;

    private final PipeTransport transport = new PipeTransport();
    private long clock;

    private static TravellingItem item() {
        return new TravellingItem(batch("stone", 1), Side.WEST, null);
    }

    private static TravellingItem restored(long id, Side exit, int progress) {
        return TravellingItem.restore(id, batch("stone", 1), Side.WEST, null, null, TransportMode.UNCLAIMED, exit, progress,
                TransportMode.UNCLAIMED.speed());
    }

    private List<Departure> tick(int times, ExitChooser chooser) {
        List<Departure> departures = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            departures.addAll(transport.tick(clock++, chooser));
        }
        return departures;
    }

    @Test
    void leavesThroughChosenExitAfterCrossingThePipe() {
        TravellingItem item = item();
        transport.insert(item);

        assertThat(tick(TICKS_UNCLAIMED - 1, travelling -> Optional.of(Side.EAST))).isEmpty();
        assertThat(transport.tick(clock++, travelling -> Optional.of(Side.EAST)))
                .containsExactly(new Departure(item, Optional.of(Side.EAST)));
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void asksForTheExitOnceOnEntry() {
        transport.insert(item());
        List<Integer> askedAt = new ArrayList<>();

        tick(TICKS_UNCLAIMED, travelling -> {
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

        transport.tick(clock++, travelling -> Optional.of(Side.EAST));
        assertThat(transport.pollChanged()).isTrue();

        tick(TICKS_UNCLAIMED - 1, travelling -> Optional.of(Side.EAST));
        assertThat(transport.pollChanged()).isFalse();
    }

    @Test
    void viewerMovesItemsAlongAndHandsThemBackAtTheEnd() {
        TravellingItem item = restored(1, Side.EAST, 0);
        transport.insert(item);

        for (int i = 0; i < TICKS_UNCLAIMED - 1; i++) {
            assertThat(transport.advance(clock++)).isEmpty();
        }
        assertThat(transport.items()).extracting(TravellingItem::progress).containsExactly(48);

        assertThat(transport.advance(clock++)).containsExactly(new Departure(item, Optional.of(Side.EAST)));
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void viewerLetsUnconfirmedItemsFadeOutAtTheCentre() {
        transport.expect(item());

        assertThat(transport.advance(clock++)).isEmpty();
        assertThat(transport.isEmpty()).isFalse();

        assertThat(transport.advance(clock++)).isEmpty();
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void viewerDoesNotExpectAnItemItAlreadyHas() {
        TravellingItem item = restored(7, Side.EAST, 30);
        transport.insert(item);

        transport.expect(item.enteringFrom(Side.WEST));
        transport.expect(restored(8, null, 0));

        assertThat(transport.items()).extracting(TravellingItem::id).containsExactly(7L, 8L);
        assertThat(transport.progressOf(7)).hasValue(30);
        assertThat(transport.progressOf(99)).isEmpty();
    }

    @Test
    void viewerKeepsRealItemsWithoutExitWhereTheyAre() {
        transport.insert(restored(1, null, 20));

        for (int i = 0; i < 10; i++) {
            assertThat(transport.advance(clock++)).isEmpty();
        }

        assertThat(transport.items()).extracting(TravellingItem::progress).containsExactly(20);
    }

    @Test
    void waitsTenSecondsForAnExitBeforeGivingUpAtTheCentre() {
        TravellingItem item = item();
        transport.insert(item);

        // two more ticks to reach the centre once it has given up
        assertThat(tick(PipeTransport.MAX_WAIT + 1, travelling -> Optional.empty())).isEmpty();
        assertThat(transport.tick(clock++, travelling -> Optional.empty())).containsExactly(new Departure(item, Optional.empty()));
        assertThat(transport.isEmpty()).isTrue();
        assertThat(PipeTransport.MAX_WAIT).isEqualTo(200);
    }

    @Test
    void abandonedItemIsGivenUpOnWithoutWaiting() {
        TravellingItem item = item();
        transport.insert(item);

        List<Departure> departures = tick(2, travelling -> {
            travelling.abandon();
            return Optional.empty();
        });

        assertThat(departures).containsExactly(new Departure(item, Optional.empty()));
    }

    @Test
    void movesOnAsSoonAsAnExitTurnsUp() {
        TravellingItem item = item();
        transport.insert(item);
        tick(7, travelling -> Optional.empty());
        assertThat(item.progress()).isZero();

        List<Departure> departures = tick(TICKS_UNCLAIMED, travelling -> Optional.of(Side.EAST));

        assertThat(departures).containsExactly(new Departure(item, Optional.of(Side.EAST)));
    }

    @Test
    void patientItemsNeverRunOutOfTime() {
        TravellingItem item = item();
        transport.insert(item);

        tick(PipeTransport.MAX_WAIT * 3, travelling -> {
            travelling.stayPatient();
            return Optional.empty();
        });

        assertThat(transport.items()).containsExactly(item);
    }

    @Test
    void movesItemsIndependently() {
        TravellingItem first = item();
        TravellingItem second = item();
        transport.insert(first);
        tick(1, travelling -> Optional.of(Side.EAST));
        transport.insert(second);

        List<Departure> departures = tick(TICKS_UNCLAIMED - 1, travelling -> Optional.of(Side.EAST));

        assertThat(departures).extracting(Departure::item).containsExactly(first);
        assertThat(transport.items()).containsExactly(second);
    }

    @Test
    void restoredItemKeepsItsExitAndProgress() {
        TravellingItem item = TravellingItem.restore(1, batch("stone", 1), Side.WEST, router(7), null, TransportMode.SORTED,
                Side.UP, 40, 30);
        transport.insert(item);

        List<Departure> departures = tick(1, travelling -> Optional.of(Side.DOWN));

        assertThat(departures).containsExactly(new Departure(item, Optional.of(Side.UP)));
        assertThat(item.mode()).isEqualTo(TransportMode.SORTED);
    }

    @Test
    void clearHandsBackEverythingInside() {
        TravellingItem item = item();
        transport.insert(item);

        assertThat(transport.clear()).containsExactly(item);
        assertThat(transport.isEmpty()).isTrue();
    }

    @Test
    void enteringTheNextPipeKeepsBatchDestinationModeAndSpeed() {
        TravellingItem item = TravellingItem.restore(1, batch("stone", 4), Side.WEST, router(7), router(5),
                TransportMode.REQUESTED, Side.EAST, 60, 45);

        TravellingItem next = item.enteringFrom(Side.WEST);

        assertThat(next.id()).isEqualTo(item.id());
        assertThat(next.progress()).isZero();
        assertThat(next.batch()).isEqualTo(batch("stone", 4));
        assertThat(next.destination()).contains(router(7));
        assertThat(next.nextHop()).contains(router(5));
        assertThat(next.mode()).isEqualTo(TransportMode.REQUESTED);
        assertThat(next.speed()).isEqualTo(45);
        assertThat(next.origin()).isEqualTo(Side.WEST);
        assertThat(next.exit()).isEmpty();
        assertThat(next.progress()).isZero();
    }

    @Test
    void whyAnItemTravelsDecidesItsMode() {
        TravellingItem item = item();
        assertThat(item.mode()).isEqualTo(TransportMode.UNCLAIMED);

        item.routeTo(router(3));
        assertThat(item.mode()).isEqualTo(TransportMode.SORTED);

        item.clearDestination();
        assertThat(item.mode()).isEqualTo(TransportMode.UNCLAIMED);

        assertThat(new TravellingItem(batch("stone", 1), Side.WEST, router(3)).mode()).isEqualTo(TransportMode.REQUESTED);
    }

    @Test
    void requestedItemsAreFasterThanSortedOnesAndThoseFasterThanUnclaimed() {
        assertThat(TransportMode.REQUESTED.speed()).isGreaterThan(TransportMode.SORTED.speed());
        assertThat(TransportMode.SORTED.speed()).isGreaterThan(TransportMode.UNCLAIMED.speed());
        // twice the original's 0.2, 0.25 and 0.3 blocks per tick
        assertThat(TransportMode.UNCLAIMED.speed() * 10 / PipeTransport.PIPE_LENGTH).isEqualTo(4);
        assertThat(TransportMode.SORTED.speed() * 10 / PipeTransport.PIPE_LENGTH).isEqualTo(5);
        assertThat(TransportMode.REQUESTED.speed() * 10 / PipeTransport.PIPE_LENGTH).isEqualTo(6);
    }

    @Test
    void routedPipeSendsItemsOnAtTheSpeedOfTheirMode() {
        transport.setBoost(100);
        TravellingItem item = item();
        transport.insert(item);

        transport.tick(clock++, travelling -> {
            travelling.routeTo(router(3));
            return Optional.of(Side.EAST);
        });

        assertThat(item.speed()).isEqualTo(TransportMode.SORTED.speed());
    }

    @Test
    void boostedPipeSendsItemsOnFasterUpToAWholePipePerTick() {
        transport.setBoost(150);
        TravellingItem slow = item();
        transport.insert(slow);
        transport.tick(clock++, travelling -> Optional.of(Side.EAST));
        assertThat(slow.speed()).isEqualTo(36);

        transport.setBoost(200);
        TravellingItem fast = new TravellingItem(batch("stone", 1), Side.WEST, router(3));
        transport.insert(fast);
        List<Departure> departures = transport.tick(clock++, travelling -> Optional.of(Side.EAST));

        assertThat(fast.speed()).isEqualTo(PipeTransport.PIPE_LENGTH);
        assertThat(departures).extracting(Departure::item).contains(fast);
    }

    @Test
    void plainPipeKeepsTheSpeedTheItemArrivedWith() {
        TravellingItem fast = item();
        fast.setSpeed(60);
        transport.insert(fast.enteringFrom(Side.WEST));

        assertThat(transport.tick(clock++, travelling -> Optional.of(Side.EAST))).hasSize(1);
    }

    @Test
    void rejectsASpeedThatWouldNeverArrive() {
        assertThatThrownBy(() -> item().setSpeed(0)).isInstanceOf(IllegalArgumentException.class);
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

    @Test
    void itemHandedOverInATickDoesNotMoveAgainInThatTick() {
        PipeTransport next = new PipeTransport();
        TravellingItem item = restored(1, Side.EAST, 48);
        transport.insert(item);

        Departure departure = transport.tick(7, travelling -> Optional.of(Side.EAST)).get(0);
        TravellingItem entered = departure.item().enteringFrom(Side.WEST);
        next.insert(entered);

        next.tick(7, travelling -> Optional.of(Side.EAST));
        assertThat(entered.progress()).isEqualTo(12);
        assertThat(entered.decided()).isFalse();

        next.tick(8, travelling -> Optional.of(Side.EAST));
        assertThat(entered.progress()).isEqualTo(36);
    }

    @Test
    void whatAnItemOvershootsCarriesIntoTheNextPipe() {
        TravellingItem item = restored(1, Side.EAST, 48);
        transport.insert(item);

        Departure departure = transport.tick(clock++, travelling -> Optional.of(Side.EAST)).get(0);

        assertThat(departure.item().progress()).isEqualTo(72);
        assertThat(departure.item().enteringFrom(Side.WEST).progress()).isEqualTo(12);
    }

    @Test
    void viewerDoesNotMoveAHandedOverItemTwiceEither() {
        PipeTransport next = new PipeTransport();
        transport.insert(restored(1, Side.EAST, 48));

        Departure departure = transport.advance(7).get(0);
        TravellingItem entered = departure.item().enteringFrom(Side.WEST);
        next.expect(entered);
        next.advance(7);

        assertThat(entered.progress()).isEqualTo(12);
    }

    @Test
    void whatADestinationDoesNotWantStaysBehindAsItsOwnItem() {
        TravellingItem item = new TravellingItem(batch("stone", 20), Side.WEST, null);
        transport.insert(item);

        transport.tick(clock++, travelling -> {
            travelling.keepOnly(6);
            return Optional.of(Side.EAST);
        });

        assertThat(transport.items()).extracting(TravellingItem::batch).containsExactly(batch("stone", 6), batch("stone", 14));
        assertThat(transport.items().get(1).decided()).isFalse();
        assertThat(transport.items().get(1).origin()).isEqualTo(Side.WEST);
        assertThat(transport.pollChanged()).isTrue();
    }

    @Test
    void keepingAllOrMoreCutsNothingOff() {
        TravellingItem item = new TravellingItem(batch("stone", 20), Side.WEST, null);

        item.keepOnly(20);
        item.keepOnly(64);
        item.keepOnly(0);

        assertThat(item.batch()).isEqualTo(batch("stone", 20));
        assertThat(item.takeCutOff()).isEmpty();
    }
}
