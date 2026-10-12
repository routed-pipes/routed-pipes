package logisticspipes.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GraphViewTest {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 80;

    private final GraphView view = new GraphView(WIDTH, HEIGHT);

    @Test
    void theNewestSampleStartsAtTheRightEdge() {
        assertThat(view.x(0)).isEqualTo(WIDTH);
        assertThat(view.x(1)).isLessThan(WIDTH);
    }

    @Test
    void olderSamplesAreFurtherLeftByTheWidthOfASample() {
        double step = view.x(3) - view.x(4);

        assertThat(view.x(0) - view.x(1)).isEqualTo(step);
        assertThat(step).isPositive();
    }

    @Test
    void largerAmountsAreHigherUp() {
        assertThat(view.y(0)).isZero();
        assertThat(view.y(2)).isGreaterThan(view.y(1));
    }

    @Test
    void panningMovesTheSamplesWithTheMouse() {
        double x = view.x(5);
        double y = view.y(5);

        view.pan(30, 10);

        assertThat(view.x(5)).isCloseTo(x + 30, within(1e-9));
        assertThat(view.y(5)).isCloseTo(y + 10, within(1e-9));
    }

    @Test
    void panningStopsAtTheNewestSample() {
        view.pan(-1000, 0);

        assertThat(view.x(0)).isEqualTo(WIDTH);
    }

    @Test
    void panningStopsAtTheOldestSampleADayCanHold() {
        view.pan(1e9, 0);

        assertThat(view.x(AmountHistory.SAMPLES - 1)).isCloseTo(WIDTH, within(1e-6));
    }

    @Test
    void scalingTheTimeKeepsTheRightEdgeAndSpreadsTheSamples() {
        double before = WIDTH - view.x(4);

        view.scaleTime(2);

        assertThat(view.x(0)).isEqualTo(WIDTH);
        assertThat(WIDTH - view.x(4)).isCloseTo(2 * before, within(1e-9));
    }

    @Test
    void scalingTheAmountKeepsTheBottomAndStretchesTheRest() {
        double before = view.y(4);

        view.scaleAmount(0.5);

        assertThat(view.y(0)).isZero();
        assertThat(view.y(4)).isCloseTo(before / 2, within(1e-9));
    }

    @Test
    void scalingHasLimits() {
        for (int step = 0; step < 1000; step++) {
            view.scaleTime(0.5);
            view.scaleAmount(2);
        }

        assertThat(WIDTH - view.x(1)).isPositive();
        assertThat(view.y(1)).isFinite();
    }

    @Test
    void fittingPutsTheSamplesInViewInsideTheHeight() {
        long[] samples = {900, 1000, 1100, 1300};

        view.fit(samples);

        for (long sample : samples) {
            assertThat(view.y(sample)).isBetween(0.0, (double) HEIGHT);
        }
        assertThat(view.y(1300) - view.y(900)).isGreaterThan(HEIGHT / 2.0);
    }

    @Test
    void fittingAFlatLineKeepsItInside() {
        view.fit(new long[] {64, 64, 64});

        assertThat(view.y(64)).isBetween(0.0, (double) HEIGHT);
    }

    @Test
    void fittingIgnoresSamplesThatAreOutOfView() {
        long[] samples = new long[100];
        samples[0] = 1_000_000;
        for (int index = 1; index < samples.length; index++) {
            samples[index] = 10 + index % 3;
        }

        view.fit(samples);

        assertThat(view.y(12) - view.y(10)).isGreaterThan(HEIGHT / 2.0);
    }

    @Test
    void fittingNothingChangesNothing() {
        view.fit(new long[0]);

        assertThat(view.y(0)).isZero();
    }

    @Test
    void theViewKnowsItsEdges() {
        assertThat(view.rightAge()).isZero();
        assertThat(view.x(view.leftAge())).isCloseTo(0, within(1e-9));
        assertThat(view.y(view.top())).isCloseTo(HEIGHT, within(1e-9));
        assertThat(view.y(view.bottom())).isZero();
        assertThat(view.shows(10, 10)).isTrue();
        assertThat(view.shows(-1, 10)).isFalse();
        assertThat(view.shows(10, HEIGHT + 1)).isFalse();
    }

    @Test
    void timeLabelsKeepTheirDistance() {
        // a sample is 15 pixels wide to begin with
        assertThat(view.ageStep(40)).isEqualTo(5);

        view.scaleTime(0.1);

        assertThat(view.ageStep(40)).isEqualTo(30);
    }

    @Test
    void amountLabelsStepByOneTwoOrFiveTimesAPowerOfTen() {
        assertThat(view.amountStep(16)).isEqualTo(2);

        view.scaleAmount(0.01);

        assertThat(view.amountStep(16)).isEqualTo(200);
    }

    @Test
    void agesReadAsTimeAgo() {
        assertThat(GraphView.ago(0)).isEqualTo("Now");
        assertThat(GraphView.ago(5)).isEqualTo("5min");
        assertThat(GraphView.ago(60)).isEqualTo("1h");
        assertThat(GraphView.ago(90)).isEqualTo("1h30min");
    }
}
