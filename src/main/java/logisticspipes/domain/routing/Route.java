package logisticspipes.domain.routing;

// how to reach the destination: the side to leave through, the next router on the way and the total distance
public record Route(RouterId destination, Side exit, RouterId nextHop, int distance) {
}
