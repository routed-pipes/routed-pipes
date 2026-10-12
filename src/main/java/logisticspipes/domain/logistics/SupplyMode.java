package logisticspipes.domain.logistics;

// when a supplier reorders and how much
public enum SupplyMode {
    // tops up to the target with whatever can be had
    PARTIAL,
    // tops up only if all that is missing can be had
    FULL,
    // waits until the stock is down to half
    BULK50,
    // waits until nothing is left
    BULK100,
    // ignores the target and fills all the room there is, a stack at a time
    INFINITE;

    public SupplyMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean allOrNothing() {
        return this == FULL;
    }

    // how many to order, room is how many more would fit and only has to be exact up to a stack
    public int amount(int target, int have, int onTheWay, int room) {
        if (this == INFINITE) {
            return Math.max(0, room - onTheWay);
        }
        if (room < 1 || this == BULK50 && have > target / 2 || this == BULK100 && have > 0) {
            return 0;
        }
        return Math.max(0, target - have - onTheWay);
    }
}
