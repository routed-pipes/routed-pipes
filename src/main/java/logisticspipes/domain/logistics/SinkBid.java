package logisticspipes.domain.logistics;

// how much a pipe wants an item nobody asked for, what taking a stack of it costs in energy,
// and how many it takes at most, 0 for as many as fit
public record SinkBid(int priority, int energy, int maxItems) {
}
