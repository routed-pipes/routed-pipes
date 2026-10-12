package logisticspipes.domain.tube;

import java.util.List;

import logisticspipes.domain.routing.Side;

// how a tube ends up when a player puts it down: its shape and where the main block goes, counted from the clicked block
public record TubePlacement(TubeShape shape, Offset main) {

    private static final List<Side> FACINGS = List.of(Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST);

    // dx and dz point from the player to the middle of the clicked block, lookX and lookZ are where the player looks
    // the clicked block is the near end of the tube, the rest lies away from the player
    // a tube that bends does so to the side the player looks at
    public static TubePlacement of(TubeKind kind, double dx, double dz, double lookX, double lookZ) {
        Side away = away(dx, dz);
        Side far = kind.bends() ? sideways(away, lookX, lookZ) : away;
        for (Side facing : FACINGS) {
            TubeShape shape = new TubeShape(kind, facing);
            // tubes that bend can lie either way round, the others start with their first end
            for (int near = 0; near < (kind.bends() ? 2 : 1); near++) {
                if (shape.end(near).side() == away.opposite() && shape.end(1 - near).side() == far) {
                    return new TubePlacement(shape, Offset.ZERO.minus(shape.end(near).block()));
                }
            }
        }
        throw new IllegalStateException("no way to put down " + kind + " going " + away + " to " + far);
    }

    // the side the block is on as seen from the player
    static Side away(double dx, double dz) {
        double angle = Math.atan2(dx, dz);
        if (angle > Math.PI / 4 && angle <= 3 * Math.PI / 4) {
            return Side.EAST;
        }
        if (angle > -3 * Math.PI / 4 && angle <= -Math.PI / 4) {
            return Side.WEST;
        }
        return angle > -Math.PI / 4 && angle <= Math.PI / 4 ? Side.SOUTH : Side.NORTH;
    }

    // the side the player looks at, of the two that are left and right of the tube
    private static Side sideways(Side away, double lookX, double lookZ) {
        if (away == Side.EAST || away == Side.WEST) {
            return lookZ < 0 ? Side.NORTH : Side.SOUTH;
        }
        return lookX > 0 ? Side.EAST : Side.WEST;
    }
}
