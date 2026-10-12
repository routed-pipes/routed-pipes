package logisticspipes.domain.routing;

import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LinkTest {

    @Test
    void exitsAreTheSidesThatLeadToAnotherRouter() {
        List<Link> links = List.of(new Link(router(2), Side.EAST, 3), new Link(router(3), Side.EAST, 5),
                new Link(router(4), Side.UP, 1));

        assertThat(Link.exits(links)).containsExactlyInAnyOrder(Side.EAST, Side.UP);
    }

    @Test
    void aRouterWithoutLinksHasNoExits() {
        assertThat(Link.exits(List.of())).isEmpty();
    }
}
