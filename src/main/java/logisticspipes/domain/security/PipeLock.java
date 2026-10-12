package logisticspipes.domain.security;

import java.util.Optional;
import java.util.UUID;

// the rules for the security card in a pipe
public final class PipeLock {

    // ticks a card may stay in a pipe after its station stopped vouching for it
    public static final int PATIENCE = 20;

    private int unauthorizedFor;

    // called every tick while the pipe holds a card, true once the card has to go
    public boolean expired(boolean authorized) {
        if (authorized) {
            unauthorizedFor = 0;
            return false;
        }
        return ++unauthorizedFor > PATIENCE;
    }

    // pipes locked to different stations do not pass items to each other, a pipe without a card works with everyone
    public static boolean connects(Optional<UUID> station, Optional<UUID> other) {
        return station.isEmpty() || other.isEmpty() || station.equals(other);
    }
}
