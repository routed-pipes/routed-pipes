package logisticspipes.domain.routing;

// a direct pipe connection from one router to a neighbour, leaving through the given side
public record Link(RouterId target, Side exit, int distance) {
    public Link {
        if (distance <= 0) {
            throw new IllegalArgumentException("distance must be positive: " + distance);
        }
    }
}
