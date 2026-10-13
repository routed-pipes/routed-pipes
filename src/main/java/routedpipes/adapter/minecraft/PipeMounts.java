package routedpipes.adapter.minecraft;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import routedpipes.domain.look.Mount;
import routedpipes.domain.look.Mounts;
import routedpipes.domain.routing.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

// the mounts of a pipe follow from the blocks around it
// clients work them out when they draw the pipe, 13 ways to be held would be too many more block states
public final class PipeMounts {

    private PipeMounts() {
    }

    public static List<Mount> at(BlockGetter level, BlockPos pos, BlockState pipe) {
        Set<Side> connected = EnumSet.noneOf(Side.class);
        Set<Side> solid = EnumSet.noneOf(Side.class);
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (PipeBlock.connected(pipe, direction)) {
                connected.add(Directions.toDomain(direction));
            } else if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, direction.getOpposite())) {
                solid.add(Directions.toDomain(direction));
            }
        }
        return Mounts.choose(connected, solid);
    }

    // where the model of a mount is, below the models folder
    public static String modelPath(Mount mount) {
        return ("block/pipe/mount_" + mount.towards() + "_" + mount.flank()).toLowerCase(Locale.ROOT);
    }
}
