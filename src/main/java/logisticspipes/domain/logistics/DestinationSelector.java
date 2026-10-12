package logisticspipes.domain.logistics;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import logisticspipes.domain.routing.RouteTable;
import logisticspipes.domain.routing.RouterId;

public final class DestinationSelector {

    private DestinationSelector() {
    }

    // reachable offers from best to worst: highest priority first, then the shortest way
    public static List<RouterId> rank(RouterId source, Collection<SinkOffer> offers, RouteTable routes) {
        record Candidate(SinkOffer offer, int distance) {
        }
        return offers.stream()
                .flatMap(offer -> distance(source, offer.router(), routes).stream()
                        .mapToObj(distance -> new Candidate(offer, distance)))
                .sorted(Comparator
                        .comparingInt((Candidate candidate) -> -candidate.offer().priority())
                        .thenComparingInt(Candidate::distance)
                        .thenComparing(candidate -> candidate.offer().router().value()))
                .map(candidate -> candidate.offer().router())
                .toList();
    }

    public static Optional<RouterId> select(RouterId source, Collection<SinkOffer> offers, RouteTable routes) {
        return rank(source, offers, routes).stream().findFirst();
    }

    private static OptionalInt distance(RouterId source, RouterId target, RouteTable routes) {
        return target.equals(source) ? OptionalInt.of(0) : routes.distance(target);
    }
}
