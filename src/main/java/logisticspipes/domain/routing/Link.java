package logisticspipes.domain.routing;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

// a direct pipe connection from one router to a neighbour, leaving through the given side
public record Link(RouterId target, Side exit, int distance) {
    public Link {
        if (distance <= 0) {
            throw new IllegalArgumentException("distance must be positive: " + distance);
        }
    }

    // the sides of a router that lead to another one, those show the green light
    public static Set<Side> exits(Collection<Link> links) {
        Set<Side> exits = EnumSet.noneOf(Side.class);
        for (Link link : links) {
            exits.add(link.exit());
        }
        return exits;
    }
}
