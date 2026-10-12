package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.routing.Link;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// finds the routed pipes at the ends of plain pipe runs
final class PipeTopology {

    private record Found(PipeBlockEntity router, int distance) {
    }

    private PipeTopology() {
    }

    static List<Link> discoverLinks(Level level, BlockPos routerPos) {
        List<Link> links = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            walk(level, routerPos, direction).ifPresent(found ->
                    links.add(new Link(found.router().routerId(), Directions.toDomain(direction), found.distance())));
        }
        return links;
    }

    // call when a pipe appeared or vanished at pos
    static void markRoutersDirty(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            walk(level, pos, direction).ifPresent(found -> found.router().markLinksDirty());
        }
    }

    // follows a run of plain pipes, a run ends at a routed pipe and breaks at any branch
    private static Optional<Found> walk(Level level, BlockPos start, Direction direction) {
        BlockPos current = start.relative(direction);
        Direction heading = direction;
        int distance = 1;
        while (!current.equals(start) && level.isLoaded(current)) {
            BlockState state = level.getBlockState(current);
            if (!(state.getBlock() instanceof LogisticsPipeBlock pipe)) {
                return Optional.empty();
            }
            if (pipe.routed()) {
                return level.getBlockEntity(current) instanceof PipeBlockEntity router
                        ? Optional.of(new Found(router, distance))
                        : Optional.empty();
            }
            Direction next = null;
            for (Direction side : Direction.values()) {
                if (side != heading.getOpposite() && state.getValue(LogisticsPipeBlock.CONNECTED.get(side))) {
                    if (next != null) {
                        return Optional.empty();
                    }
                    next = side;
                }
            }
            if (next == null) {
                return Optional.empty();
            }
            heading = next;
            current = current.relative(next);
            distance++;
        }
        return Optional.empty();
    }
}
