package routedpipes.domain.transport;

import java.util.Optional;

import routedpipes.domain.routing.Side;

@FunctionalInterface
public interface ExitChooser {
    // empty when the item has nowhere to go
    Optional<Side> chooseExit(TravellingItem item);
}
