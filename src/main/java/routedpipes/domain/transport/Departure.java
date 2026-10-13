package routedpipes.domain.transport;

import java.util.Optional;

import routedpipes.domain.routing.Side;

// an item leaving a pipe, without an exit it has nowhere to go
public record Departure(TravellingItem item, Optional<Side> exit) {
}
