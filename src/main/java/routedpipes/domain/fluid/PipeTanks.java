package routedpipes.domain.fluid;

import java.util.EnumMap;
import java.util.Map;

import routedpipes.domain.routing.Side;

// the tanks inside a fluid pipe: one towards every side and one in the centre
// fluid moves from the centre to a side and from there into the tank next to the pipe
public final class PipeTanks {

    public static final int SIDE_CAPACITY = 5000;
    public static final int CENTRE_CAPACITY = 10000;

    private final Map<Side, Tank> sides = new EnumMap<>(Side.class);
    private final Tank centre = new Tank(CENTRE_CAPACITY);

    public PipeTanks() {
        for (Side side : Side.values()) {
            sides.put(side, new Tank(SIDE_CAPACITY));
        }
    }

    public Tank side(Side side) {
        return sides.get(side);
    }

    public Tank centre() {
        return centre;
    }

    public boolean isEmpty() {
        return centre.isEmpty() && sides.values().stream().allMatch(Tank::isEmpty);
    }

    // puts arrived fluid into the tanks around the pipe, then the side tanks towards them, then the centre
    // returns what fit nowhere
    public int unpack(FluidBatch batch, Map<Side, TankAccess> neighbours) {
        int left = batch.amount();
        for (TankAccess neighbour : neighbours.values()) {
            if (left > 0) {
                left -= neighbour.fill(new FluidBatch(batch.type(), left), false);
            }
        }
        for (Side side : neighbours.keySet()) {
            if (left > 0) {
                left -= sides.get(side).fill(new FluidBatch(batch.type(), left), false);
            }
        }
        if (left > 0) {
            left -= centre.fill(new FluidBatch(batch.type(), left), false);
        }
        return left;
    }

    // one step of emptying the pipe, returns whether anything moved
    public boolean pushOut(Map<Side, TankAccess> neighbours) {
        boolean moved = false;
        for (Map.Entry<Side, TankAccess> neighbour : neighbours.entrySet()) {
            Tank tank = sides.get(neighbour.getKey());
            if (!tank.isEmpty()) {
                moved |= tank.drain(neighbour.getValue().fill(tank.content().get(), false)) > 0;
            }
        }
        for (Side side : neighbours.keySet()) {
            if (!centre.isEmpty()) {
                moved |= centre.drain(sides.get(side).fill(centre.content().get(), false)) > 0;
            }
        }
        return moved;
    }

    // the tank on that side is gone: what the side tank held goes back to the centre, what does not fit is gone
    public void sideLost(Side side) {
        Tank tank = sides.get(side);
        tank.content().ifPresent(content -> centre.fill(content, false));
        tank.drain(tank.amount());
    }

    // how much of the fluid the tanks around the pipe and the side tanks towards them still take
    public int roomFor(FluidType type, Map<Side, TankAccess> neighbours) {
        long room = 0;
        for (Map.Entry<Side, TankAccess> neighbour : neighbours.entrySet()) {
            room += neighbour.getValue().room(type) + sides.get(neighbour.getKey()).room(type);
        }
        return (int) Math.min(room, Integer.MAX_VALUE);
    }
}
