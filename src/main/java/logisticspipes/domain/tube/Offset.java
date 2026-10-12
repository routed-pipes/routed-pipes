package logisticspipes.domain.tube;

import logisticspipes.domain.routing.Side;

// a whole number of blocks, usually counted from the main block of a tube
public record Offset(int x, int y, int z) {

    public static final Offset ZERO = new Offset(0, 0, 0);

    // one block to the given side
    public static Offset of(Side side) {
        return switch (side) {
            case DOWN -> new Offset(0, -1, 0);
            case UP -> new Offset(0, 1, 0);
            case NORTH -> new Offset(0, 0, -1);
            case SOUTH -> new Offset(0, 0, 1);
            case WEST -> new Offset(-1, 0, 0);
            case EAST -> new Offset(1, 0, 0);
        };
    }

    public Offset plus(Offset other) {
        return new Offset(x + other.x, y + other.y, z + other.z);
    }

    public Offset minus(Offset other) {
        return new Offset(x - other.x, y - other.y, z - other.z);
    }

    // a quarter turn seen from above, what was north is east afterwards
    public Offset turned() {
        return new Offset(-z, y, x);
    }

    public Offset mirrored() {
        return new Offset(-x, y, z);
    }

    public int dot(Offset other) {
        return x * other.x + y * other.y + z * other.z;
    }
}
