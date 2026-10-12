package logisticspipes.domain.logistics;

// how a pipe pushes the contents of its inventory into the network
public enum ExtractionMode {
    // a few items at a time to anyone who takes them
    EXTRACTOR(16, true),
    // whole stacks, but only to pipes that asked for that item by name
    QUICK_SORT(64, false);

    private final int amount;
    private final boolean usesDefaultRoutes;

    ExtractionMode(int amount, boolean usesDefaultRoutes) {
        this.amount = amount;
        this.usesDefaultRoutes = usesDefaultRoutes;
    }

    // items per upkeep round
    public int amount() {
        return amount;
    }

    public boolean usesDefaultRoutes() {
        return usesDefaultRoutes;
    }
}
