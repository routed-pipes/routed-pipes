package routedpipes.domain.logistics;

// how a pipe pushes the contents of its inventory into the network
public enum ExtractionMode {
    // a few items at a time to anyone who takes them, paid per item
    EXTRACTOR(16, 20, true, 10, 0),
    // whole stacks in quick succession, but only to pipes that asked for that item by name, paid per stack
    QUICK_SORT(64, 6, false, 0, 1000);

    private final int amount;
    private final int interval;
    private final boolean usesDefaultRoutes;
    private final int energyPerItem;
    private final int energyPerRound;

    ExtractionMode(int amount, int interval, boolean usesDefaultRoutes, int energyPerItem, int energyPerRound) {
        this.amount = amount;
        this.interval = interval;
        this.usesDefaultRoutes = usesDefaultRoutes;
        this.energyPerItem = energyPerItem;
        this.energyPerRound = energyPerRound;
    }

    // items per round
    public int amount() {
        return amount;
    }

    // ticks between rounds
    public int interval() {
        return interval;
    }

    public int energyFor(int items) {
        return energyPerRound + energyPerItem * items;
    }

    public boolean usesDefaultRoutes() {
        return usesDefaultRoutes;
    }
}
