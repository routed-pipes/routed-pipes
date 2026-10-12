package logisticspipes.domain.fluid;

// an amount of one fluid in mB
public record FluidBatch(FluidType type, int amount) {
    public FluidBatch {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }
}
