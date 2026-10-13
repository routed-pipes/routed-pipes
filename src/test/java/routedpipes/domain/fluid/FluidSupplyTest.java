package routedpipes.domain.fluid;

import static routedpipes.testing.Fixtures.fluid;
import static org.assertj.core.api.Assertions.assertThat;

import routedpipes.domain.fluid.FluidSupply.Minimum;
import org.junit.jupiter.api.Test;

class FluidSupplyTest {

    private static FluidSupply keep(int target, Minimum minimum) {
        return new FluidSupply(fluid("water", 1).type(), target, false, minimum);
    }

    @Test
    void ordersWhatIsMissing() {
        assertThat(keep(8000, Minimum.NONE).order(3000, 0)).isEqualTo(5000);
    }

    @Test
    void whatIsOnItsWayIsNotOrderedAgain() {
        assertThat(keep(8000, Minimum.NONE).order(3000, 4000)).isEqualTo(1000);
        assertThat(keep(8000, Minimum.NONE).order(3000, 5000)).isZero();
    }

    @Test
    void aFullTankOrdersNothing() {
        assertThat(keep(8000, Minimum.NONE).order(9000, 0)).isZero();
    }

    @Test
    void waitsUntilTheMinimumIsMissing() {
        assertThat(keep(8000, Minimum.ONE_BUCKET).order(7001, 0)).isZero();
        assertThat(keep(8000, Minimum.ONE_BUCKET).order(7000, 0)).isEqualTo(1000);
        assertThat(keep(8000, Minimum.TWO_BUCKETS).order(6500, 0)).isZero();
        assertThat(keep(8000, Minimum.FIVE_BUCKETS).order(3000, 0)).isEqualTo(5000);
    }

    @Test
    void withoutAMinimumEvenASingleMillibucketIsOrdered() {
        assertThat(keep(8000, Minimum.NONE).order(7999, 0)).isEqualTo(1);
    }
}
