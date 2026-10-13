package routedpipes.domain.tube;

import java.util.List;

import routedpipes.domain.routing.Side;

// how a tube ends up when a player puts it down: its shape and where the main block goes, counted from the clicked block
public record TubePlacement(TubeShape shape, Offset main) {

    private static final List<Side> FACINGS = List.of(Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST);

    // dx and dz point from the player to the middle of the clicked block, lookX and lookZ are where the player looks
    // the clicked block is the near end of the tube, the rest lies away from the player
    // a tube that bends or moves over does so to the side the player looks at
    public static TubePlacement of(TubeKind kind, double dx, double dz, double lookX, double lookZ) {
        Side away = away(dx, dz);
        Side aside = sideways(away, lookX, lookZ);
        // a tube that moves over is only ever turned north or east, the same shapes the other way round start at the far end
        for (Side facing : kind.shifts() ? FACINGS.subList(0, 2) : FACINGS) {
            for (int mirrored = 0; mirrored < (kind.shifts() ? 2 : 1); mirrored++) {
                TubeShape shape = new TubeShape(kind, facing, mirrored == 1);
                for (int near = 0; near < (kind.oneWay() ? 1 : 2); near++) {
                    if (leads(shape, near, away, aside)) {
                        return new TubePlacement(shape, Offset.ZERO.minus(shape.end(near).block()));
                    }
                }
            }
        }
        throw new IllegalStateException("no way to put down " + kind + " going " + away + " and " + aside);
    }

    // whether the tube, entered at the near end, leads away from the player and bends or moves over to the side asked for
    private static boolean leads(TubeShape shape, int near, Side away, Side aside) {
        TubeEnd from = shape.end(near);
        TubeEnd to = shape.end(1 - near);
        if (from.side() != away.opposite() || to.side() != (shape.kind().bends() ? aside : away)) {
            return false;
        }
        return !shape.kind().shifts() || to.block().minus(from.block()).dot(Offset.of(aside)) > 0;
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
