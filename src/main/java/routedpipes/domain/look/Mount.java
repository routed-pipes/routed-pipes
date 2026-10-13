package routedpipes.domain.look;

import java.util.ArrayList;
import java.util.List;

import routedpipes.domain.routing.Side;

// a foot that holds a pipe to the solid block in the direction of towards, it sits on the flank of the pipe that faces flank
public record Mount(Side towards, Side flank) {

    // top and bottom first, then the walls, the order the original mod goes through them
    private static final List<Side> ORDER = List.of(Side.UP, Side.DOWN, Side.NORTH, Side.SOUTH, Side.EAST, Side.WEST);

    public Mount {
        if (flank == towards || flank == towards.opposite()) {
            throw new IllegalArgumentException("a mount towards " + towards + " has no flank " + flank);
        }
    }

    // all 24 places a mount can be
    public static List<Mount> all() {
        List<Mount> mounts = new ArrayList<>();
        for (Side towards : ORDER) {
            for (Side flank : ORDER) {
                if (flank != towards && flank != towards.opposite()) {
                    mounts.add(new Mount(towards, flank));
                }
            }
        }
        return mounts;
    }
}
