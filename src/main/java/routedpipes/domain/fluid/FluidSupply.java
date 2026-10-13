package routedpipes.domain.fluid;

// how much of a fluid a supplier pipe keeps in the tank next to it
public record FluidSupply(FluidType fluid, int target, boolean partial, Minimum minimum) {

    // nothing is ordered until at least this much is missing
    public enum Minimum {
        NONE(0),
        ONE_BUCKET(1000),
        TWO_BUCKETS(2000),
        FIVE_BUCKETS(5000);

        private final int amount;

        Minimum(int amount) {
            this.amount = amount;
        }

        public int amount() {
            return amount;
        }
    }

    // how much to order for a tank that holds this much while that much is on its way, 0 for nothing
    public int order(int stocked, int onRoute) {
        int missing = target - stocked - onRoute;
        return missing < 1 || missing < minimum.amount() ? 0 : missing;
    }
}
