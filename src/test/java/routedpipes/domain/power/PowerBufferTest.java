package routedpipes.domain.power;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PowerBufferTest {

    private final PowerBuffer buffer = new PowerBuffer();

    @Test
    void anEmptyBufferMissesAllOfItsCapacity() {
        assertThat(buffer.missing()).isEqualTo(10_000);

        buffer.add(2500);

        assertThat(buffer.stored()).isEqualTo(2500);
        assertThat(buffer.missing()).isEqualTo(7500);
    }

    @Test
    void machinesGetAllTheyNeedWhileTheBufferHoldsEnough() {
        buffer.add(5000);

        assertThat(buffer.offers(List.of(1000, 0, 3000))).containsExactly(1000, 0, 3000);
    }

    @Test
    void machinesGetTheSameShareWhenTheBufferDoesNotHoldEnough() {
        buffer.add(1000);

        assertThat(buffer.offers(List.of(1000, 3000))).containsExactly(250, 750);
    }

    @Test
    void sharesNeverAddUpToMoreThanTheBufferHolds() {
        buffer.add(100);

        int[] offers = buffer.offers(List.of(333, 333, 333));

        assertThat(offers).containsExactly(33, 33, 33);
    }

    @Test
    void anEmptyBufferOffersNothing() {
        assertThat(buffer.offers(List.of(500, 800))).containsExactly(0, 0);
    }

    @Test
    void hugeNeedsDoNotOverflow() {
        buffer.add(10_000);

        assertThat(buffer.offers(List.of(Integer.MAX_VALUE, Integer.MAX_VALUE))).containsExactly(5000, 5000);
    }

    @Test
    void whatMachinesTakeLeavesTheBuffer() {
        buffer.add(1000);

        buffer.take(400);
        buffer.take(5000);

        assertThat(buffer.stored()).isZero();
    }

    @Test
    void orderingCostsOneEnergyForEveryHundred() {
        assertThat(PowerBuffer.orderCost(10_000)).isEqualTo(100);
        assertThat(PowerBuffer.orderCost(250)).isEqualTo(2);
        assertThat(PowerBuffer.orderCost(99)).isZero();
    }

    @Test
    void providersAreAskedInProportionToWhatTheyHold() {
        assertThat(PowerBuffer.share(10_000, 3_000_000, 4_000_000)).isEqualTo(7500);
        assertThat(PowerBuffer.share(10_000, 1_000_000, 4_000_000)).isEqualTo(2500);
        assertThat(PowerBuffer.share(10_000, 10_000_000, 30_000_000)).isEqualTo(3333);
        assertThat(PowerBuffer.share(10_000, 0, 0)).isZero();
    }

    @Test
    void loadingNeverGoesBelowZero() {
        buffer.load(-5);
        assertThat(buffer.stored()).isZero();

        buffer.load(4200);
        assertThat(buffer.stored()).isEqualTo(4200);
    }
}
