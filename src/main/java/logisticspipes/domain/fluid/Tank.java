package logisticspipes.domain.fluid;

import java.util.Optional;

// holds one fluid at a time
public final class Tank {

    private final int capacity;
    private FluidType type;
    private int amount;

    public Tank(int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int amount() {
        return amount;
    }

    public boolean isEmpty() {
        return amount == 0;
    }

    public boolean isFull() {
        return amount == capacity;
    }

    public Optional<FluidBatch> content() {
        return amount == 0 ? Optional.empty() : Optional.of(new FluidBatch(type, amount));
    }

    // how much of the fluid is in here
    public int holding(FluidType fluid) {
        return amount > 0 && type.equals(fluid) ? amount : 0;
    }

    // how much more of the fluid fits, nothing while another fluid is in here
    public int room(FluidType fluid) {
        return amount == 0 || type.equals(fluid) ? capacity - amount : 0;
    }

    // returns how much went in
    public int fill(FluidBatch batch, boolean simulate) {
        int accepted = Math.min(batch.amount(), room(batch.type()));
        if (accepted > 0 && !simulate) {
            type = batch.type();
            amount += accepted;
        }
        return accepted;
    }

    // returns how much came out
    public int drain(int upTo) {
        int drained = Math.max(0, Math.min(upTo, amount));
        amount -= drained;
        if (amount == 0) {
            type = null;
        }
        return drained;
    }
}
