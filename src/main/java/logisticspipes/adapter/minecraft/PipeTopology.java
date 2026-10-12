package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.tube.TubeEnd;
import logisticspipes.domain.tube.TubeKind;
import logisticspipes.domain.tube.TubeShape;
import logisticspipes.domain.security.PipeLock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// finds the routed pipes that can be reached through plain pipes and tubes
// distances count a pipe as TubeKind.PIPE_WEIGHT
final class PipeTopology {

    // keeps a broken or absurdly large pipe system from stalling the server
    private static final int MAX_PIPES = 4096;

    // from is the side of the block the search comes in through, order keeps equally near steps in the order they were found
    private record Step(BlockPos pos, int distance, Direction from, boolean fromTube, int order) {
    }

    private PipeTopology() {
    }

    static List<Link> discoverLinks(Level level, BlockPos routerPos) {
        List<Link> links = new ArrayList<>();
        BlockState state = level.getBlockState(routerPos);
        Optional<UUID> station = level.getBlockEntity(routerPos) instanceof PipeBlockEntity self
                ? self.security().station() : Optional.empty();
        for (Direction direction : Direction.values()) {
            // a side can be cut off from the pipe next to it
            if (state.getBlock() instanceof LogisticsPipeBlock && !state.getValue(LogisticsPipeBlock.CONNECTED.get(direction))) {
                continue;
            }
            explore(level, routerPos, direction, false).forEach((router, distance) -> {
                // pipes locked to different security stations are not connected
                if (PipeLock.connects(station, router.security().station())) {
                    links.add(new Link(router.routerId(), Directions.toDomain(direction), distance));
                }
            });
        }
        return links;
    }

    // the routed pipes at the other end of the plain pipes on every side
    static Set<PipeBlockEntity> neighbours(Level level, BlockPos routerPos) {
        Set<PipeBlockEntity> routers = new HashSet<>();
        for (Direction direction : Direction.values()) {
            routers.addAll(explore(level, routerPos, direction, false).keySet());
        }
        return routers;
    }

    // call when a pipe appeared or vanished at pos
    static void markRoutersDirty(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            explore(level, pos, direction, false).keySet().forEach(PipeBlockEntity::markLinksDirty);
        }
    }

    // call when a tube appeared or vanished
    static void markRoutersDirty(Level level, BlockPos main, TubeShape shape) {
        for (int index = 0; index < 2; index++) {
            TubeEnd end = shape.end(index);
            explore(level, Tubes.at(main, end.block()), Directions.toMinecraft(end.side()), true).keySet()
                    .forEach(PipeBlockEntity::markLinksDirty);
        }
    }

    // the side of a fork that gets to the router the shortest way
    static Optional<Direction> branchTowards(Level level, BlockPos fork, RouterId router) {
        Direction best = null;
        int shortest = Integer.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(fork).getValue(LogisticsPipeBlock.CONNECTED.get(direction))) {
                continue;
            }
            for (Map.Entry<PipeBlockEntity, Integer> found : explore(level, fork, direction, false).entrySet()) {
                if (found.getKey().routerId().equals(router) && found.getValue() < shortest) {
                    best = direction;
                    shortest = found.getValue();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // every routed pipe reachable from start through plain pipes and tubes when leaving in the given direction
    // with its distance, the search spreads through forks and stops at routed pipes
    private static Map<PipeBlockEntity, Integer> explore(Level level, BlockPos start, Direction direction, boolean fromTube) {
        Map<PipeBlockEntity, Integer> routers = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start);
        // nearest first, tubes are shorter than they look
        PriorityQueue<Step> open = new PriorityQueue<>(Comparator.comparingInt(Step::distance).thenComparingInt(Step::order));
        int order = 0;
        open.add(new Step(start.relative(direction), TubeKind.PIPE_WEIGHT, direction.getOpposite(), fromTube, order++));
        while (!open.isEmpty() && seen.size() < MAX_PIPES) {
            Step step = open.poll();
            if (!seen.add(step.pos()) || !level.isLoaded(step.pos())) {
                continue;
            }
            BlockState state = level.getBlockState(step.pos());
            if (!(state.getBlock() instanceof LogisticsPipeBlock pipe)) {
                Optional<Step> out = throughTube(level, step, order);
                if (out.isPresent()) {
                    open.add(out.get());
                    order++;
                }
                continue;
            }
            if (pipe.routed()) {
                if (level.getBlockEntity(step.pos()) instanceof PipeBlockEntity router) {
                    routers.putIfAbsent(router, step.distance());
                }
                continue;
            }
            for (Direction side : Direction.values()) {
                if (state.getValue(LogisticsPipeBlock.CONNECTED.get(side))) {
                    open.add(new Step(step.pos().relative(side), step.distance() + TubeKind.PIPE_WEIGHT, side.getOpposite(),
                            false, order++));
                }
            }
        }
        return routers;
    }

    // the step out of the far end of a tube that is entered with the given step
    // pipes only get in and out at the ends made for them, anything else is a dead end
    private static Optional<Step> throughTube(Level level, Step step, int order) {
        Optional<Tubes.Entry> entry = Tubes.entryAt(level, step.pos(), step.from());
        if (entry.isEmpty() || !step.fromTube() && !entry.get().pipeEnd()) {
            return Optional.empty();
        }
        TubeShape shape = entry.get().shape();
        int out = 1 - entry.get().end();
        Direction side = Directions.toMinecraft(shape.end(out).side());
        BlockPos beyond = Tubes.at(entry.get().main(), shape.end(out).block()).relative(side);
        if (!level.isLoaded(beyond)) {
            return Optional.empty();
        }
        boolean onward = Tubes.entryAt(level, beyond, side.getOpposite()).isPresent()
                || shape.kind().pipeEnd(out) && level.getBlockState(beyond).getBlock() instanceof LogisticsPipeBlock;
        // what was counted for the step into the tube now stands for the step out of it
        int distance = step.distance() + shape.kind().weight();
        return onward ? Optional.of(new Step(beyond, distance, side.getOpposite(), true, order)) : Optional.empty();
    }
}
