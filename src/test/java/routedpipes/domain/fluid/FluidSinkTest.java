package routedpipes.domain.fluid;

import static routedpipes.testing.Fixtures.fluid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class FluidSinkTest {

    private final FluidSink sink = new FluidSink(Set.of(fluid("water", 1).type()), FluidSink.Priority.FLUID_SINK);

    @Test
    void takesAllOfAListedFluidThatFits() {
        assertThat(sink.amount(fluid("water", 3000), 8000, 0)).isEqualTo(3000);
    }

    @Test
    void takesOnlyWhatThereIsRoomFor() {
        assertThat(sink.amount(fluid("water", 3000), 1200, 0)).isEqualTo(1200);
    }

    @Test
    void whatIsAlreadyOnItsWayNeedsItsRoomToo() {
        assertThat(sink.amount(fluid("water", 3000), 4000, 2500)).isEqualTo(1500);
    }

    @Test
    void takesNothingWhenMoreIsOnItsWayThanFits() {
        assertThat(sink.amount(fluid("water", 3000), 1000, 2500)).isZero();
    }

    @Test
    void takesNothingOfAFluidThatIsNotListed() {
        assertThat(sink.amount(fluid("lava", 3000), 8000, 0)).isZero();
    }
}
