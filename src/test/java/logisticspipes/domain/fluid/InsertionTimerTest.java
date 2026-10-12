package logisticspipes.domain.fluid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InsertionTimerTest {

    private final InsertionTimer timer = new InsertionTimer();

    private int ticksUntilDue(boolean full) {
        int ticks = 0;
        while (!timer.due(full)) {
            ticks++;
        }
        return ticks;
    }

    @Test
    void aFreshPipeSendsRightAway() {
        assertThat(timer.due(false)).isTrue();
    }

    @Test
    void aFullTankGoesOutAgainSoonAfterSending() {
        timer.sent();

        assertThat(ticksUntilDue(true)).isEqualTo(InsertionTimer.AFTER_SENDING);
    }

    @Test
    void aFullTankIsTriedAgainABitLaterAfterFailing() {
        timer.failed();

        assertThat(ticksUntilDue(true)).isEqualTo(InsertionTimer.AFTER_FAILING);
    }

    @Test
    void aPartlyFilledTankWaitsForMore() {
        timer.sent();

        assertThat(ticksUntilDue(false)).isEqualTo(InsertionTimer.WAIT_FOR_MORE + InsertionTimer.AFTER_SENDING);
    }

    @Test
    void aTankThatFillsUpWhileWaitingStopsWaiting() {
        timer.sent();
        for (int tick = 0; tick < 20; tick++) {
            timer.due(false);
        }

        assertThat(ticksUntilDue(true)).isEqualTo(InsertionTimer.AFTER_SENDING);
    }
}
