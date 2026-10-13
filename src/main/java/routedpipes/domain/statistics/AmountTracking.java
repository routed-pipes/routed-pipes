package routedpipes.domain.statistics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;

import routedpipes.domain.item.ItemType;

// what a statistics table keeps track of: how many of each chosen item its network has, looked up once a minute
public final class AmountTracking {

    // ticks between two samples
    public static final int INTERVAL = 1200;

    private final Map<ItemType, AmountHistory> tracked = new LinkedHashMap<>();
    // starts over when the table is loaded, so the first sample after that comes a full interval later
    private int ticks;

    // the amount right now is the first sample, false for an item that is tracked already
    public boolean track(ItemType type, long amount) {
        if (tracked.containsKey(type)) {
            return false;
        }
        AmountHistory history = new AmountHistory();
        history.record(amount);
        tracked.put(type, history);
        return true;
    }

    public void untrack(ItemType type) {
        tracked.remove(type);
    }

    // for a table that is loaded again
    public void restore(ItemType type, AmountHistory history) {
        tracked.put(type, history);
    }

    // in the order they were added
    public List<ItemType> items() {
        return List.copyOf(tracked.keySet());
    }

    public Optional<AmountHistory> history(ItemType type) {
        return Optional.ofNullable(tracked.get(type));
    }

    // called every tick, true when it is time for the next sample
    public boolean due() {
        ticks++;
        return ticks % INTERVAL == 0;
    }

    public void record(ToLongFunction<ItemType> amounts) {
        tracked.forEach((type, history) -> history.record(amounts.applyAsLong(type)));
    }
}
