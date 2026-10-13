package routedpipes.domain.routing;

public enum Side {
    DOWN, UP, NORTH, SOUTH, WEST, EAST;

    public Side opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
