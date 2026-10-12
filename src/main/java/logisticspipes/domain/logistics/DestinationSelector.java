package logisticspipes.domain.logistics;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import logisticspipes.domain.routing.Route;
import logisticspipes.domain.routing.RouterId;

public final class DestinationSelector {

    private DestinationSelector() {
    }

    // best reachable offer: highest priority first, then the shortest way
    public static Optional<RouterId> select(RouterId source, Collection<SinkOffer> offers, Map<RouterId, Route> routes) {
        record Candidate(SinkOffer offer, int distance) {
        }
        return offers.stream()
                .flatMap(offer -> distance(source, offer.router(), routes).stream()
                        .mapToObj(distance -> new Candidate(offer, distance)))
                .min(Comparator
                        .comparingInt((Candidate candidate) -> -candidate.offer().priority())
                        .thenComparingInt(Candidate::distance)
                        .thenComparing(candidate -> candidate.offer().router().value()))
                .map(candidate -> candidate.offer().router());
    }

    private static OptionalInt distance(RouterId source, RouterId target, Map<RouterId, Route> routes) {
        if (target.equals(source)) {
            return OptionalInt.of(0);
        }
        Route route = routes.get(target);
        return route == null ? OptionalInt.empty() : OptionalInt.of(route.distance());
    }
}
