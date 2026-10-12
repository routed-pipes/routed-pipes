package logisticspipes.domain.fluid;

import java.util.Optional;
import java.util.function.BooleanSupplier;

// how much an extractor pipe may still pull through one of its sides, it pays for that ahead
public final class ExtractionBudget {

    // most that is pulled at a time
    public static final int FLOW = 500;
    // what a helping of up to that much costs
    public static final int ENERGY = 5;
    // ticks between pulls
    public static final int INTERVAL = 10;

    private int paidFor;

    public ExtractionBudget() {
    }

    public ExtractionBudget(int paidFor) {
        this.paidFor = paidFor;
    }

    public int paidFor() {
        return paidFor;
    }

    // moves fluid from the tank next to the pipe into the side tank towards it, returns how much
    // what was paid for and not pulled is kept for the next time
    public int pull(Tank sideTank, TankAccess source, BooleanSupplier pay) {
        int wanted = Math.min(FLOW, sideTank.capacity() - sideTank.amount());
        if (paidFor < wanted && pay.getAsBoolean()) {
            paidFor += wanted;
        }
        Optional<FluidBatch> offered = source.drainAny(Math.min(paidFor, FLOW), true);
        if (offered.isEmpty()) {
            return 0;
        }
        int fits = sideTank.fill(offered.get(), true);
        if (fits == 0) {
            return 0;
        }
        int drained = source.drain(new FluidBatch(offered.get().type(), fits), false);
        if (drained > 0) {
            sideTank.fill(new FluidBatch(offered.get().type(), drained), false);
            paidFor -= drained;
        }
        return drained;
    }
}
