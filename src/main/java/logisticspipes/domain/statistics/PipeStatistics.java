package logisticspipes.domain.statistics;

// how many items a routed pipe sent off, took in and passed on, since it was loaded and over its whole life
public final class PipeStatistics {

    public enum Kind {
        // came out of the inventory of this pipe and left for another one
        SENT,
        // arrived at this pipe as its destination
        RECEIVED,
        // passed through on the way to another pipe
        RELAYED
    }

    private final int[] session = new int[Kind.values().length];
    private final long[] lifetime = new long[Kind.values().length];

    public void count(Kind kind, int items) {
        if (items < 0) {
            throw new IllegalArgumentException("cannot count a negative amount: " + items);
        }
        session[kind.ordinal()] += items;
        lifetime[kind.ordinal()] += items;
    }

    public int session(Kind kind) {
        return session[kind.ordinal()];
    }

    public long lifetime(Kind kind) {
        return lifetime[kind.ordinal()];
    }

    // for a pipe that is loaded again: the lifetime goes on where it was, the session starts over
    public void restoreLifetime(Kind kind, long items) {
        lifetime[kind.ordinal()] = Math.max(0, items);
    }
}
