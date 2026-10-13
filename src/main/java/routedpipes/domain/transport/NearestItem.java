package routedpipes.domain.transport;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import routedpipes.domain.routing.Side;

// picks the item in a pipe that is closest to a point, for telling which one somebody pointed at
// the pipe is a block that runs from 0 to 1 in every direction
public final class NearestItem {

    // an item and the pipe it is in, which is that many blocks away from the pipe the point is measured in
    public record InPipe<P>(P pipe, int dx, int dy, int dz, TravellingItem item) {
    }

    private NearestItem() {
    }

    // the same over several pipes, for an item that has moved on since somebody pointed at it
    // of two equally near ones the first in the list wins
    public static <P> Optional<InPipe<P>> among(List<InPipe<P>> items, double x, double y, double z) {
        return items.stream().min(Comparator.comparingDouble(
                found -> distanceSquared(position(found.item()), x - found.dx(), y - found.dy(), z - found.dz())));
    }

    // empty for a pipe without items, of two equally near ones the one that came in first wins
    public static Optional<TravellingItem> to(List<TravellingItem> items, double x, double y, double z) {
        return items.stream().min(Comparator.comparingDouble(item -> distanceSquared(position(item), x, y, z)));
    }

    // where the item is: it comes in from its origin side to the centre and goes out through its exit
    // one without an exit stays at the centre
    static double[] position(TravellingItem item) {
        double progress = Math.min(item.progress(), PipeTransport.PIPE_LENGTH) / (double) PipeTransport.PIPE_LENGTH;
        Side side = item.origin();
        double offset = Math.max(0.5 - progress, 0);
        if (progress > 0.5 && item.exit().isPresent()) {
            side = item.exit().get();
            offset = progress - 0.5;
        }
        double[] position = {0.5, 0.5, 0.5};
        switch (side) {
            case DOWN -> position[1] -= offset;
            case UP -> position[1] += offset;
            case NORTH -> position[2] -= offset;
            case SOUTH -> position[2] += offset;
            case WEST -> position[0] -= offset;
            case EAST -> position[0] += offset;
        }
        return position;
    }

    private static double distanceSquared(double[] position, double x, double y, double z) {
        double dx = position[0] - x;
        double dy = position[1] - y;
        double dz = position[2] - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
