package logisticspipes.domain.hud;

import java.util.Optional;

// a panel in the world: it stands in front of its block and is turned to the eyes of the player
// all three directions have length one, a panel unit is a hundredth of a block along right and down
public record HudFacing(Vec origin, Vec right, Vec down, Vec normal) {

    // blocks from the middle of the block towards the player
    public static final double PUSH = 0.4;
    public static final double UNIT = 0.01;

    public record Vec(double x, double y, double z) {

        Vec plus(Vec other, double times) {
            return new Vec(x + other.x * times, y + other.y * times, z + other.z * times);
        }

        double dot(Vec other) {
            return x * other.x + y * other.y + z * other.z;
        }

        Vec cross(Vec other) {
            return new Vec(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }

        Vec normalized() {
            double length = Math.sqrt(dot(this));
            return new Vec(x / length, y / length, z / length);
        }
    }

    // a place on the panel in panel units, 0 0 is its middle
    public record Spot(double x, double y) {
    }

    // eye and centre must not be the same place
    public static HudFacing of(Vec eye, Vec centre) {
        Vec normal = eye.plus(centre, -1).normalized();
        Vec forward = new Vec(-normal.x, -normal.y, -normal.z);
        Vec flat = forward.cross(new Vec(0, 1, 0));
        // straight above or below there is no sideways, any will do
        Vec right = flat.dot(flat) < 1e-9 ? new Vec(1, 0, 0) : flat.normalized();
        Vec down = forward.cross(right);
        return new HudFacing(centre.plus(normal, PUSH), right, down, normal);
    }

    // where somebody looking along look from eye meets the plane of the panel, empty when they look away from it
    public Optional<Spot> cursor(Vec eye, Vec look) {
        double towards = look.dot(normal);
        if (towards >= -1e-9) {
            return Optional.empty();
        }
        Vec hit = eye.plus(look, origin.plus(eye, -1).dot(normal) / towards);
        Vec onPanel = hit.plus(origin, -1);
        return Optional.of(new Spot(onPanel.dot(right) / UNIT, onPanel.dot(down) / UNIT));
    }
}
