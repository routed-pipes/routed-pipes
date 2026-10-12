package logisticspipes.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmountHistoryTest {

    private final AmountHistory history = new AmountHistory();

    @Test
    void aNewHistoryIsEmpty() {
        assertThat(history.size()).isZero();
        assertThat(history.oldestFirst()).isEmpty();
    }

    @Test
    void keepsSamplesInTheOrderTheyWereTaken() {
        history.record(5);
        history.record(7);
        history.record(6);

        assertThat(history.oldestFirst()).containsExactly(5, 7, 6);
    }

    @Test
    void holdsADayOfSamplesAndThenForgetsTheOldest() {
        for (int minute = 0; minute < AmountHistory.SAMPLES + 2; minute++) {
            history.record(minute);
        }

        long[] samples = history.oldestFirst();
        assertThat(samples).hasSize(AmountHistory.SAMPLES);
        assertThat(samples[0]).isEqualTo(2);
        assertThat(samples[samples.length - 1]).isEqualTo(AmountHistory.SAMPLES + 1);
    }

    @Test
    void holdsAmountsLargerThanAnInt() {
        history.record(5_000_000_000L);

        assertThat(history.oldestFirst()).containsExactly(5_000_000_000L);
    }

    @Test
    void comesBackFromItsSamples() {
        history.record(1);
        history.record(2);

        AmountHistory restored = AmountHistory.of(history.oldestFirst());
        restored.record(3);

        assertThat(restored.oldestFirst()).containsExactly(1, 2, 3);
    }
}
