package logisticspipes.domain.tube;

import logisticspipes.domain.routing.Side;

// how a tube ends up when a player puts it down: its shape and where the main block goes, counted from the clicked block
public record TubePlacement(TubeShape shape, Offset main) {

    // dx and dz point from the player to the middle of the clicked block
    // the clicked block is the near end of the tube, the rest lies away from the player
    public static TubePlacement of(TubeKind kind, double dx, double dz) {
        return new TubePlacement(new TubeShape(kind, away(dx, dz)), Offset.ZERO);
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
}
