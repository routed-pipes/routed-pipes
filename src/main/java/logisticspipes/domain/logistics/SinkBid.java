package logisticspipes.domain.logistics;

// how much a pipe wants an item nobody asked for, and what taking a stack of it costs in energy
public record SinkBid(int priority, int energy) {
}
