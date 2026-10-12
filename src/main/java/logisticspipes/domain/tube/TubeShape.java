package logisticspipes.domain.tube;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import logisticspipes.domain.routing.Side;

// a tube of some kind turned to face a side, everything is counted from its main block
public record TubeShape(TubeKind kind, Side facing) {

    // half the width of a tube
    private static final double RADIUS = 0.3125;

    public TubeShape {
        if (facing == Side.UP || facing == Side.DOWN) {
            throw new IllegalArgumentException("tubes only turn around the vertical axis: " + facing);
        }
    }

    // quarter turns from north, seen from above
    private int turns() {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    private Offset placed(Offset base) {
        Offset result = base;
        for (int turn = 0; turn < turns(); turn++) {
            result = result.turned();
        }
        return result;
    }

    private Side placed(Side base) {
        Side result = base;
        for (int turn = 0; turn < turns(); turn++) {
            result = switch (result) {
                case NORTH -> Side.EAST;
                case EAST -> Side.SOUTH;
                case SOUTH -> Side.WEST;
                case WEST -> Side.NORTH;
                default -> result;
            };
        }
        return result;
    }

    // turns around the middle of the main block
    private Point placed(Point base) {
        double x = base.x() - 0.5;
        double z = base.z() - 0.5;
        for (int turn = 0; turn < turns(); turn++) {
            double oldX = x;
            x = -z;
            z = oldX;
        }
        return new Point(x + 0.5, base.y(), z + 0.5);
    }

    // the blocks it fills besides the main block
    public List<Offset> parts() {
        return kind.parts().stream().map(this::placed).toList();
    }

    public TubeEnd end(int index) {
        TubeEnd base = kind.ends().get(index);
        return new TubeEnd(placed(base.block()), placed(base.side()));
    }

    // which end is in that block and opens to that side
    public OptionalInt endAt(Offset block, Side side) {
        for (int index = 0; index < 2; index++) {
            if (end(index).equals(new TubeEnd(block, side))) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    // where an item is after the given part of the way from the first end to the second
    public Point pointAt(double fraction) {
        return placed(kind.pointAt(fraction));
    }

    // the room the tube takes up in one of its blocks, counted from the corner of that block
    public List<Box> boxesIn(Offset block) {
        List<Box> boxes = new ArrayList<>();
        int pieces = kind.pieces();
        for (int piece = 0; piece < pieces; piece++) {
            Box.around(pointAt(piece / (double) pieces), pointAt((piece + 1) / (double) pieces), RADIUS)
                    .within(block).ifPresent(boxes::add);
        }
        return boxes;
    }
}
