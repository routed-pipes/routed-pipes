package logisticspipes.domain.logistics;

import logisticspipes.domain.routing.RouterId;

// a router that is willing to take an item, higher priority wins
public record SinkOffer(RouterId router, int priority) {
}
