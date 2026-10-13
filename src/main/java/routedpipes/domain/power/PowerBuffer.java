package routedpipes.domain.power;

import java.util.List;

// the energy a supplying pipe holds for the machines next to it
public final class PowerBuffer {

    public static final int CAPACITY = 10_000;
    // ordering this much from the providers costs the network one of its own energy
    public static final int ORDERED_PER_ENERGY = 100;

    private int stored;

    public int stored() {
        return stored;
    }

    // energy from a provider arrived
    public void add(int amount) {
        stored += Math.max(0, amount);
    }

    // a machine took that much
    public void take(int amount) {
        stored -= Math.max(0, Math.min(amount, stored));
    }

    public int missing() {
        return Math.max(0, CAPACITY - stored);
    }

    // what each machine is offered when it is short of that much
    // everyone gets all of it, or the same share when the buffer does not hold enough for all
    public int[] offers(List<Integer> needs) {
        long needed = 0;
        for (int need : needs) {
            needed += Math.max(0, need);
        }
        int[] offers = new int[needs.size()];
        for (int index = 0; index < offers.length; index++) {
            long need = Math.max(0, needs.get(index));
            offers[index] = (int) (needed <= stored ? need : need * stored / needed);
        }
        return offers;
    }

    // what the network pays of its own energy for ordering that much
    public static int orderCost(int missing) {
        return missing / ORDERED_PER_ENERGY;
    }

    // how much of what is missing is ordered from a provider that holds that much of all the energy there is
    public static int share(int missing, int stored, long available) {
        return available <= 0 ? 0 : (int) ((long) missing * stored / available);
    }

    public void load(int stored) {
        this.stored = Math.max(0, stored);
    }
}
