package logisticspipes.adapter.minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// finds the routed pipes that can be reached through plain pipes
final class PipeTopology {

    // keeps a broken or absurdly large pipe system from stalling the server
    private static final int MAX_PIPES = 4096;

    private record Step(BlockPos pos, int distance) {
    }

    private PipeTopology() {
    }

    static List<Link> discoverLinks(Level level, BlockPos routerPos) {
        List<Link> links = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            explore(level, routerPos, direction).forEach((router, distance) ->
                    links.add(new Link(router.routerId(), Directions.toDomain(direction), distance)));
        }
        return links;
    }

    // call when a pipe appeared or vanished at pos
    static void markRoutersDirty(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            explore(level, pos, direction).keySet().forEach(PipeBlockEntity::markLinksDirty);
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
            for (Map.Entry<PipeBlockEntity, Integer> found : explore(level, fork, direction).entrySet()) {
                if (found.getKey().routerId().equals(router) && found.getValue() < shortest) {
                    best = direction;
                    shortest = found.getValue();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // every routed pipe reachable from start through plain pipes when leaving in the given direction, with its distance
    // the search spreads through forks and stops at routed pipes
    private static Map<PipeBlockEntity, Integer> explore(Level level, BlockPos start, Direction direction) {
        Map<PipeBlockEntity, Integer> routers = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start);
        Deque<Step> open = new ArrayDeque<>();
        open.add(new Step(start.relative(direction), 1));
        while (!open.isEmpty() && seen.size() < MAX_PIPES) {
            Step step = open.poll();
            if (!seen.add(step.pos()) || !level.isLoaded(step.pos())) {
                continue;
            }
            BlockState state = level.getBlockState(step.pos());
            if (!(state.getBlock() instanceof LogisticsPipeBlock pipe)) {
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
                    open.add(new Step(step.pos().relative(side), step.distance() + 1));
                }
            }
        }
        return routers;
    }
}
