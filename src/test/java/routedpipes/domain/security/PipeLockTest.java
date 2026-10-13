package routedpipes.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PipeLockTest {

    private static final Optional<UUID> ONE = Optional.of(new UUID(0, 1));
    private static final Optional<UUID> OTHER = Optional.of(new UUID(0, 2));

    private final PipeLock lock = new PipeLock();

    @Test
    void anAuthorizedCardStays() {
        for (int tick = 0; tick < 100; tick++) {
            assertThat(lock.expired(true)).isFalse();
        }
    }

    @Test
    void aCardGoesAboutASecondAfterItsStationStoppedVouchingForIt() {
        for (int tick = 0; tick < PipeLock.PATIENCE; tick++) {
            assertThat(lock.expired(false)).isFalse();
        }

        assertThat(lock.expired(false)).isTrue();
    }

    @Test
    void theCountStartsOverWhenTheStationIsBack() {
        for (int tick = 0; tick < PipeLock.PATIENCE; tick++) {
            lock.expired(false);
        }
        lock.expired(true);

        assertThat(lock.expired(false)).isFalse();
    }

    @Test
    void pipesOfTheSameStationWorkTogether() {
        assertThat(PipeLock.connects(ONE, ONE)).isTrue();
    }

    @Test
    void pipesOfDifferentStationsDoNot() {
        assertThat(PipeLock.connects(ONE, OTHER)).isFalse();
    }

    @Test
    void aPipeWithoutCardWorksWithEveryone() {
        assertThat(PipeLock.connects(Optional.empty(), ONE)).isTrue();
        assertThat(PipeLock.connects(ONE, Optional.empty())).isTrue();
        assertThat(PipeLock.connects(Optional.empty(), Optional.empty())).isTrue();
    }
}
