package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.transport.NearestItem;
import logisticspipes.domain.transport.TravellingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// what the pipe controller tells about an item in a pipe: what it is, where it is headed and the pipes it passes on its way
public final class ItemTracer {

    // keeps a broken or absurdly large pipe system from stalling the server
    private static final int MAX_PIPES = 4096;
    private static final int PARTICLES_PER_PIPE = 3;
    private static final double PARTICLE_SPREAD = 0.25;
    // an item is through a pipe in two or three ticks, so it has often left the pipe by the time the click gets here
    public static final int SEARCH_DISTANCE = 3;

    // the path starts at the pipe the item is in and ends at its destination, or where its way cannot be followed any further
    // an item that is not headed anywhere, or to a pipe that is gone, has no destination and only the pipe it is in as its path
    public record Trace(ItemStack item, Optional<BlockPos> destination, List<BlockPos> path) {
    }

    private ItemTracer() {
    }

    // the item nearest to where the pipe was clicked, in that pipe or one a few pipes on
    // empty when there is nothing in any of them
    public static Optional<Trace> of(PipeBlockEntity pipe, Vec3 click) {
        return nearest(pipe, click).map(found -> trace(found.pipe(), found.item()));
    }

    static Optional<NearestItem.InPipe<PipeBlockEntity>> nearest(PipeBlockEntity clicked, Vec3 click) {
        BlockPos origin = clicked.getBlockPos();
        Level level = clicked.getLevel();
        List<NearestItem.InPipe<PipeBlockEntity>> items = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(origin);
        List<PipeBlockEntity> ring = List.of(clicked);
        for (int distance = 0; distance <= SEARCH_DISTANCE && !ring.isEmpty(); distance++) {
            List<PipeBlockEntity> next = new ArrayList<>();
            for (PipeBlockEntity pipe : ring) {
                BlockPos pos = pipe.getBlockPos();
                for (TravellingItem item : pipe.travellingItems()) {
                    items.add(new NearestItem.InPipe<>(pipe, pos.getX() - origin.getX(), pos.getY() - origin.getY(),
                            pos.getZ() - origin.getZ(), item));
                }
                if (distance == SEARCH_DISTANCE) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = pos.relative(direction);
                    if (LogisticsPipeBlock.connected(pipe.getBlockState(), direction) && seen.add(neighbour)
                            && level.getBlockEntity(neighbour) instanceof PipeBlockEntity connected) {
                        next.add(connected);
                    }
                }
            }
            ring = next;
        }
        return NearestItem.among(items, click.x - origin.getX(), click.y - origin.getY(), click.z - origin.getZ());
    }

    private static Trace trace(PipeBlockEntity pipe, TravellingItem item) {
        Level level = pipe.getLevel();
        // in a plain pipe the item is on its way to the router it was last passed to
        List<RouterId> routers = routed(pipe.getBlockState())
                ? pipe.network().routeOf(pipe.routerId(), item)
                : item.nextHop().map(next -> pipe.network().routeOf(next, item)).orElse(List.of());
        List<BlockPos> path = new ArrayList<>();
        path.add(pipe.getBlockPos());
        BlockPos at = pipe.getBlockPos();
        for (RouterId router : routers) {
            Optional<BlockPos> reached = follow(pipe, level, at, router, path);
            if (reached.isEmpty()) {
                break;
            }
            at = reached.get();
        }
        return new Trace(McItemType.stackOf(item.batch()), item.destination().flatMap(pipe::positionOf), List.copyOf(path));
    }

    private static boolean routed(BlockState state) {
        return state.getBlock() instanceof LogisticsPipeBlock block && block.routed();
    }

    // adds the pipes from one place to the routed pipe with that id to the path and tells where that pipe is
    // a stretch that is not made of pipes, like a tube or the inventory between two connectors, is skipped
    private static Optional<BlockPos> follow(PipeBlockEntity pipe, Level level, BlockPos from, RouterId target, List<BlockPos> path) {
        BlockPos at = from;
        for (int pipes = 0; pipes < MAX_PIPES; pipes++) {
            if (routed(level.getBlockState(at)) && level.getBlockEntity(at) instanceof PipeBlockEntity here
                    && here.routerId().equals(target)) {
                return Optional.of(at);
            }
            Optional<Direction> side = PipeTopology.branchTowards(level, at, target);
            if (side.isEmpty() || !(level.getBlockState(at.relative(side.get())).getBlock() instanceof LogisticsPipeBlock)) {
                Optional<BlockPos> beyond = pipe.positionOf(target);
                beyond.ifPresent(path::add);
                return beyond;
            }
            at = at.relative(side.get());
            path.add(at);
        }
        return Optional.empty();
    }

    // tells the player about the item and lights up its way for a few seconds, the sparks are only sent to that player
    static void show(Player player, Trace trace) {
        player.displayClientMessage(message(player.level(), trace), true);
        if (!(player instanceof ServerPlayer viewer)) {
            return;
        }
        for (BlockPos pos : trace.path()) {
            viewer.serverLevel().sendParticles(viewer, ParticleTypes.END_ROD, true, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, PARTICLES_PER_PIPE, PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPREAD, 0);
        }
    }

    public static Component message(Level level, Trace trace) {
        ItemStack item = trace.item();
        if (trace.destination().isEmpty()) {
            return Component.translatable("logisticspipes.trace.unclaimed", item.getCount(), item.getHoverName());
        }
        BlockPos destination = trace.destination().get();
        return Component.translatable("logisticspipes.trace.headed", item.getCount(), item.getHoverName(),
                level.getBlockState(destination).getBlock().getName(), destination.getX(), destination.getY(), destination.getZ());
    }
}
