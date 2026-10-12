package logisticspipes.domain.transport;

import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.routing.Side;

public final class UnroutedPipe {

    private UnroutedPipe() {
    }

    // plain pipes just pass items on, never back where they came from
    public static Optional<Side> chooseExit(Set<Side> connections, TravellingItem item) {
        return connections.stream()
                .filter(side -> side != item.origin())
                .sorted()
                .findFirst();
    }
}
