package routedpipes.adapter.minecraft;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import routedpipes.domain.logistics.PatternSupply;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.Departure;
import routedpipes.domain.transport.PipeTransport;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import routedpipes.domain.transport.UnroutedPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

// where the items in a pipe go: which way one turns in the middle and what happens to it at the end of the pipe
final class PipeDepartures {

    private PipeDepartures() {
    }

    static Optional<Side> chooseExit(PipeBlockEntity pipe, TravellingItem item) {
        Level level = pipe.getLevel();
        if (pipe.fluidPipe() != null && pipe.fluidPipe().stops(item)) {
            return Optional.empty();
        }
        if (pipe.block().routed()) {
            Optional<Side> exit = pipe.network().chooseExit(pipe.routerId(), item, level.getGameTime());
            item.takeGaveUp().ifPresent(destination -> lost(pipe, destination, item));
            return exit;
        }
        Set<Side> connections = EnumSet.noneOf(Side.class);
        for (Direction side : Direction.values()) {
            if (LogisticsPipeBlock.connected(pipe.getBlockState(), side)) {
                connections.add(Directions.toDomain(side));
            }
        }
        return UnroutedPipe.chooseExit(connections, item, router -> PipeTopology
                .branchTowards(level, pipe.getBlockPos(), router).map(Directions::toDomain));
    }

    // an ordered item that will not reach its destination, packed fluid is told to the fluid side
    private static void lost(PipeBlockEntity pipe, RouterId destination, TravellingItem item) {
        if (item.batch().type().packedFluid()) {
            FluidContainerItem.fluidIn(McItemType.stackOf(item.batch()))
                    .ifPresent(packed -> pipe.network().fluids().lost(destination, packed));
        } else {
            pipe.network().itemLost(destination, item.batch(), pipe.getLevel().getGameTime());
        }
    }

    static void depart(PipeBlockEntity pipe, Departure departure) {
        Level level = pipe.getLevel();
        RouterId routerId = pipe.routerId();
        RequestTable table = pipe.requestTable();
        PipeTransport transport = pipe.transport();
        PipeSurroundings surroundings = pipe.surroundings();
        TravellingItem item = departure.item();
        if (FluidPipe.ended(pipe, departure)) {
            return;
        }
        ItemStack stack = McItemType.stackOf(item.batch());
        if (departure.exit().isEmpty() && table != null && item.destination().filter(routerId::equals).isPresent()) {
            // what a request table ordered ends up in its buffer
            pipe.popOut(table.takeIn(stack));
            return;
        }
        if (departure.exit().isEmpty()) {
            if (item.mode() == TransportMode.REQUESTED && item.destination().filter(wanted -> !wanted.equals(routerId)).isPresent()) {
                pipe.network().itemLost(item.destination().get(), item.batch(), level.getGameTime());
            }
            pipe.popOut(stack);
            return;
        }
        Side exit = departure.exit().get();
        Direction side = Directions.toMinecraft(exit);
        BlockPos target = pipe.getBlockPos().relative(side);
        if (!level.isLoaded(target)) {
            // wait at the chunk border and try again next tick
            transport.insert(TravellingItem.restore(item.id(), item.batch(), item.origin(),
                    item.destination().orElse(null), item.nextHop().orElse(null), item.mode(), exit,
                    PipeTransport.PIPE_LENGTH - item.speed(), item.speed()));
            return;
        }
        if (TubeBlockEntity.takeFromPipe(level, target, side.getOpposite(), item)) {
            return;
        }
        if (level.getBlockEntity(target) instanceof PipeBlockEntity neighbour) {
            neighbour.transport().insert(item.enteringFrom(exit.opposite()));
            neighbour.setChanged();
            // tell clients now, a neighbour that already ticked would only do so a tick later and the item would blink
            if (!neighbour.opaque()) {
                level.sendBlockUpdated(target, neighbour.getBlockState(), neighbour.getBlockState(), Block.UPDATE_CLIENTS);
            }
            return;
        }
        if (pipe.connector() != null && pipe.connector().handsOver(item, side)) {
            return;
        }
        List<PatternSupply> patterns = item.mode() == TransportMode.REQUESTED && item.destination().filter(routerId::equals).isPresent()
                && pipe.inventorySides().indexOf(side) == 0 ? pipe.patternSupplies() : List.of();
        if (!patterns.isEmpty()) {
            stack = surroundings.fillPattern(patterns, target, pipe.accessFace(side), stack);
            surroundings.contentsChanged();
            if (stack.isEmpty()) {
                return;
            }
            if (patterns.stream().anyMatch(pattern -> pattern.limited() && pattern.lists(item.batch().type()))) {
                // limited: what does not fit its slot must not end up in another one, so it goes back into the pipes
                transport.insert(new TravellingItem(McItemType.batchOf(stack), exit, null));
                return;
            }
        }
        ItemStack rest = surroundings.insertThrough(target,
                surroundings.insertFaces(side, item.batch().type(), item.mode() == TransportMode.REQUESTED), stack, false);
        surroundings.contentsChanged();
        if (rest.isEmpty()) {
            return;
        }
        if (pipe.block().routed() && item.mode() == TransportMode.REQUESTED && item.destination().filter(routerId::equals).isPresent()) {
            // ordered for this inventory and it did not all fit, the rest waits in the pipe for room
            transport.insert(item.remainder(McItemType.batchOf(rest)));
        } else if (pipe.block().routed()) {
            // did not fit after all, send it somewhere else
            transport.insert(new TravellingItem(McItemType.batchOf(rest), exit, null));
        } else {
            pipe.popOut(rest);
        }
    }
}
