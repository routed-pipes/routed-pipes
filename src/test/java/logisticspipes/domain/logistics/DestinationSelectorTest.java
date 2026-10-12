package logisticspipes.domain.logistics;

import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.routing.Side;
import logisticspipes.domain.routing.Route;
import logisticspipes.domain.routing.RouterId;
import org.junit.jupiter.api.Test;

class DestinationSelectorTest {

    private static final RouterId SOURCE = router(1);

    private static Map.Entry<RouterId, Route> routeTo(int number, int distance) {
        return Map.entry(router(number), new Route(router(number), Side.EAST, distance));
    }

    @Test
    void picksNothingWithoutOffers() {
        assertThat(DestinationSelector.select(SOURCE, List.of(), Map.ofEntries(routeTo(2, 1)))).isEmpty();
    }

    @Test
    void picksNearestAmongEqualPriority() {
        var offers = List.of(new SinkOffer(router(2), 0), new SinkOffer(router(3), 0));

        var selected = DestinationSelector.select(SOURCE, offers, Map.ofEntries(routeTo(2, 9), routeTo(3, 4)));

        assertThat(selected).contains(router(3));
    }

    @Test
    void prefersPriorityOverDistance() {
        var offers = List.of(new SinkOffer(router(2), 5), new SinkOffer(router(3), 0));

        var selected = DestinationSelector.select(SOURCE, offers, Map.ofEntries(routeTo(2, 9), routeTo(3, 4)));

        assertThat(selected).contains(router(2));
    }

    @Test
    void skipsUnreachableOffers() {
        var offers = List.of(new SinkOffer(router(2), 5), new SinkOffer(router(3), 0));

        var selected = DestinationSelector.select(SOURCE, offers, Map.ofEntries(routeTo(3, 4)));

        assertThat(selected).contains(router(3));
    }

    @Test
    void treatsSourceAsDistanceZero() {
        var offers = List.of(new SinkOffer(SOURCE, 0), new SinkOffer(router(2), 0));

        var selected = DestinationSelector.select(SOURCE, offers, Map.ofEntries(routeTo(2, 1)));

        assertThat(selected).contains(SOURCE);
    }

    @Test
    void breaksFullTiesByRouterId() {
        var offers = List.of(new SinkOffer(router(3), 0), new SinkOffer(router(2), 0));

        var selected = DestinationSelector.select(SOURCE, offers, Map.ofEntries(routeTo(2, 4), routeTo(3, 4)));

        assertThat(selected).contains(router(2));
    }
}
