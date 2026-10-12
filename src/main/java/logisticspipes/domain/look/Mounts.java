package logisticspipes.domain.look;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import logisticspipes.domain.routing.Side;

// picks the mounts that hold a pipe to the solid blocks around it, never more than two
// a pipe stands on the floor if it can, else it hangs from the ceiling, else it holds on to walls
public final class Mounts {

    private static final List<Mount> ALL = Mount.all();
    private static final List<Side> WALLS = List.of(Side.NORTH, Side.SOUTH, Side.WEST, Side.EAST);

    private Mounts() {
    }

    // connected are the sides with an arm, solid the sides where the block next to the pipe has a full face towards it
    public static List<Mount> choose(Set<Side> connected, Set<Side> solid) {
        List<Mount> mounts = new ArrayList<>(ALL);
        // nothing sits on an arm or between an arm and what it leads to
        mounts.removeIf(mount -> connected.contains(mount.towards()) || connected.contains(mount.flank())
                || !solid.contains(mount.towards()));
        if (mounts.isEmpty()) {
            return mounts;
        }
        if (solid.contains(Side.DOWN) && !connected.contains(Side.DOWN)) {
            oppositePair(mounts, Side.DOWN);
        } else if (solid.contains(Side.UP) && !connected.contains(Side.UP)) {
            oppositePair(mounts, Side.UP);
        } else {
            if (mounts.size() > 2) {
                betweenTwoWalls(mounts);
            }
            if (mounts.size() > 2) {
                inACorner(mounts);
            }
            if (mounts.size() > 2) {
                oppositePair(mounts, mounts.get(0).towards());
            }
        }
        return mounts;
    }

    private static boolean has(List<Mount> mounts, Side towards) {
        return mounts.stream().anyMatch(mount -> mount.towards() == towards);
    }

    // only the mounts towards one block stay, and of more than two of them a pair on opposite flanks
    private static void oppositePair(List<Mount> mounts, Side towards) {
        mounts.removeIf(mount -> mount.towards() != towards);
        if (mounts.size() <= 2) {
            return;
        }
        Set<Side> flanks = EnumSet.noneOf(Side.class);
        mounts.forEach(mount -> flanks.add(mount.flank()));
        Set<Side> keep = EnumSet.noneOf(Side.class);
        for (Side first : List.of(Side.NORTH, Side.EAST, Side.UP)) {
            if (flanks.contains(first) && flanks.contains(first.opposite())) {
                keep = EnumSet.of(first, first.opposite());
                break;
            }
        }
        Set<Side> kept = keep;
        mounts.removeIf(mount -> !kept.contains(mount.flank()));
    }

    // walls on opposite sides hold the pipe with one mount each, north and south before east and west
    private static void betweenTwoWalls(List<Mount> mounts) {
        for (Side wall : List.of(Side.NORTH, Side.EAST)) {
            if (has(mounts, wall) && has(mounts, wall.opposite())) {
                mounts.removeIf(mount -> mount.towards() != wall && mount.towards() != wall.opposite());
                onlyFirst(mounts, wall);
                onlyFirst(mounts, wall.opposite());
                return;
            }
        }
    }

    // two walls that meet hold the pipe with one mount each, on the flanks that face away from the corner
    private static void inACorner(List<Mount> mounts) {
        Set<Side> walls = EnumSet.noneOf(Side.class);
        mounts.forEach(mount -> walls.add(mount.towards()));
        for (Side wall : WALLS) {
            Side next = clockwise(wall);
            if (walls.contains(wall) && walls.contains(next)) {
                onlyOne(mounts, wall, clockwise(clockwise(next)));
                onlyOne(mounts, next, clockwise(next));
            }
        }
    }

    // of the mounts towards one block only the first stays
    private static void onlyFirst(List<Mount> mounts, Side towards) {
        Mount first = mounts.stream().filter(mount -> mount.towards() == towards).findFirst().orElse(null);
        mounts.removeIf(mount -> mount.towards() == towards && !mount.equals(first));
    }

    // of the mounts towards one block only the one on the given flank stays, or the first if there is none there
    private static void onlyOne(List<Mount> mounts, Side towards, Side flank) {
        Mount wanted = new Mount(towards, flank);
        if (mounts.contains(wanted)) {
            mounts.removeIf(mount -> mount.towards() == towards && !mount.equals(wanted));
        } else {
            onlyFirst(mounts, towards);
        }
    }

    // seen from above
    private static Side clockwise(Side side) {
        return switch (side) {
            case NORTH -> Side.EAST;
            case EAST -> Side.SOUTH;
            case SOUTH -> Side.WEST;
            case WEST -> Side.NORTH;
            default -> throw new IllegalArgumentException(side + " does not turn");
        };
    }
}
