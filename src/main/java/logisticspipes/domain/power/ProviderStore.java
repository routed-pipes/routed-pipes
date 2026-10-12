package logisticspipes.domain.power;

import java.util.LinkedHashMap;
import java.util.Map;

import logisticspipes.domain.routing.RouterId;

// the energy of a power provider and the orders of the pipes that want some of it
public final class ProviderStore {

    public static final int CAPACITY = 10_000_000;
    // what goes out to all pipes together in one tick
    public static final int MAX_PER_TICK = 10_000;
    // energy comes in through a small buffer, so no more than this per tick
    public static final int INPUT_BUFFER = 10_000;

    // hands energy to the pipe that ordered it, false when it cannot get there
    public interface Delivery {
        boolean deliver(RouterId destination, int amount);
    }

    private final Map<RouterId, Integer> orders = new LinkedHashMap<>();
    private int stored;
    private int input;

    public int stored() {
        return stored;
    }

    // what waits in the input buffer
    public int input() {
        return input;
    }

    // returns how much was taken in
    public int receive(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, INPUT_BUFFER - input));
        if (!simulate) {
            input += accepted;
        }
        return accepted;
    }

    // a pipe asks for energy, asking again before the next tick replaces what it asked for
    public void order(RouterId destination, int amount) {
        if (amount > 0) {
            orders.put(destination, amount);
        }
    }

    // once a tick: the input buffer empties into the store and the orders are served and forgotten
    // when more is ordered than may go out everyone gets the same share of what they asked for
    // returns whether the energy changed
    public boolean tick(Delivery delivery) {
        int moved = Math.min(input, CAPACITY - stored);
        input -= moved;
        stored += moved;
        boolean changed = moved > 0;
        long ordered = 0;
        for (int amount : orders.values()) {
            ordered += amount;
        }
        long budget = Math.min(stored, MAX_PER_TICK);
        for (Map.Entry<RouterId, Integer> order : orders.entrySet()) {
            long share = ordered <= budget ? order.getValue() : order.getValue() * budget / ordered;
            int send = (int) Math.min(stored, share);
            if (send > 0 && delivery.deliver(order.getKey(), send)) {
                stored -= send;
                changed = true;
            }
        }
        orders.clear();
        return changed;
    }

    public void load(int stored, int input) {
        this.stored = Math.max(0, Math.min(stored, CAPACITY));
        this.input = Math.max(0, Math.min(input, INPUT_BUFFER));
    }
}
