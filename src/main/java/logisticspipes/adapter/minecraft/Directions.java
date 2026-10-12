package logisticspipes.adapter.minecraft;

import logisticspipes.domain.routing.Side;
import net.minecraft.core.Direction;

public final class Directions {

    private Directions() {
    }

    public static Side toDomain(Direction direction) {
        return switch (direction) {
            case DOWN -> Side.DOWN;
            case UP -> Side.UP;
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case WEST -> Side.WEST;
            case EAST -> Side.EAST;
        };
    }

    public static Direction toMinecraft(Side side) {
        return switch (side) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }
}
