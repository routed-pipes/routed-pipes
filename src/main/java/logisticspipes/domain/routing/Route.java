package logisticspipes.domain.routing;

// where to send an item first to reach the destination, and how far it is in total
public record Route(RouterId destination, Side exit, int distance) {
}
