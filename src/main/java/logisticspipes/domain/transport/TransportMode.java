package logisticspipes.domain.transport;

// why an item is travelling, which decides how fast routed pipes send it on
public enum TransportMode {
    // nobody has claimed it yet
    UNCLAIMED(24),
    // on its way to a sink that takes such items
    SORTED(30),
    // ordered by someone
    REQUESTED(36);

    private final int speed;

    TransportMode(int speed) {
        this.speed = speed;
    }

    // steps per tick, a pipe is PipeTransport.PIPE_LENGTH steps long
    public int speed() {
        return speed;
    }
}
