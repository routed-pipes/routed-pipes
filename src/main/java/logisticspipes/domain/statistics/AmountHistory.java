package logisticspipes.domain.statistics;

// the amount of one item over time, a sample a minute for the last 24 hours
public final class AmountHistory {

    public static final int SAMPLES = 1440;

    private final long[] samples = new long[SAMPLES];
    // where the next sample goes, older ones are overwritten once the day is full
    private int next;
    private int recorded;

    public static AmountHistory of(long[] oldestFirst) {
        AmountHistory history = new AmountHistory();
        for (long amount : oldestFirst) {
            history.record(amount);
        }
        return history;
    }

    public void record(long amount) {
        samples[next] = amount;
        next = (next + 1) % SAMPLES;
        recorded = Math.min(recorded + 1, SAMPLES);
    }

    public int size() {
        return recorded;
    }

    // every sample there is, the newest one last
    public long[] oldestFirst() {
        long[] result = new long[recorded];
        for (int index = 0; index < recorded; index++) {
            result[index] = samples[Math.floorMod(next - recorded + index, SAMPLES)];
        }
        return result;
    }
}
