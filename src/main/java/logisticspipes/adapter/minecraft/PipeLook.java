package logisticspipes.adapter.minecraft;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import logisticspipes.application.LogisticsNetwork;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// keeps what a routed pipe shows of its network up to date
final class PipeLook {

    private final PipeBlockEntity pipe;
    // the network could not tell last time
    private boolean askAgain;

    PipeLook(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    // the arms that lead to another routed pipe get the green light
    void showLinks(List<Link> links) {
        Set<Direction> exits = EnumSet.noneOf(Direction.class);
        for (Side side : Link.exits(links)) {
            exits.add(Directions.toMinecraft(side));
        }
        ((LogisticsPipeBlock) pipe.getBlockState().getBlock()).showRoutedExits(pipe.getBlockState(), pipe.getLevel(),
                pipe.getBlockPos(), exits);
    }

    // the frame goes dark when the network has no energy
    // looked at once a second, every pipe in a tick of its own so that a large base does not do it all at once
    void tick() {
        Level level = pipe.getLevel();
        BlockPos pos = pipe.getBlockPos();
        if (!askAgain && Math.floorMod(level.getGameTime() + pos.hashCode(), LogisticsNetwork.POWER_LOOK_INTERVAL) != 0) {
            return;
        }
        Optional<Boolean> powered = pipe.network().hasPower(pipe.routerId(), level.getGameTime());
        askAgain = powered.isEmpty();
        BlockState state = pipe.getBlockState();
        if (powered.isEmpty() || powered.get() == state.getValue(LogisticsPipeBlock.POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(LogisticsPipeBlock.POWERED, powered.get()), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (level instanceof ServerLevel server) {
            // three red sparks like the original
            server.sendParticles(DustParticleOptions.REDSTONE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3,
                    0.3, 0.3, 0.3, 0);
        }
    }
}
