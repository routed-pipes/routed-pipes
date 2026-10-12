package logisticspipes.domain.transport;

import java.util.Optional;

import logisticspipes.domain.routing.Side;

// an item leaving a pipe, without an exit it has nowhere to go
public record Departure(TravellingItem item, Optional<Side> exit) {
}
