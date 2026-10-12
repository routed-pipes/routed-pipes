package logisticspipes.adapter.minecraft;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

import logisticspipes.domain.tube.Box;
import logisticspipes.domain.tube.Offset;
import logisticspipes.domain.tube.TubeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// finds the tube a block belongs to, a tube is a main block and parts that only know where that is
final class Tubes {

    // a way into a tube: its main block, its shape and the end one comes in at
    record Entry(BlockPos main, TubeShape shape, int end) {

        boolean pipeEnd() {
            return shape.kind().pipeEnd(end);
        }
    }

    private record Slice(TubeShape shape, Offset block) {
    }

    private static final Map<Slice, VoxelShape> SLICES = new ConcurrentHashMap<>();

    private Tubes() {
    }

    static BlockPos at(BlockPos main, Offset offset) {
        return main.offset(offset.x(), offset.y(), offset.z());
    }

    private static Offset between(BlockPos main, BlockPos pos) {
        return new Offset(pos.getX() - main.getX(), pos.getY() - main.getY(), pos.getZ() - main.getZ());
    }

    // the main blocks of the tubes that fill this block
    static List<BlockPos> mainsAt(BlockGetter level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof TubeBlock) {
            return List.of(pos);
        }
        if (block instanceof TubePartBlock && level.getBlockEntity(pos) instanceof TubePartBlockEntity part) {
            return part.mains();
        }
        return List.of();
    }

    // false while a tube that fills this block has its main block in a chunk that is not loaded
    static boolean loadedAt(Level level, BlockPos pos) {
        return level.isLoaded(pos) && mainsAt(level, pos).stream().allMatch(level::isLoaded);
    }

    // the tube that has an end in this block opening to the given side
    static Optional<Entry> entryAt(Level level, BlockPos pos, Direction side) {
        if (!loadedAt(level, pos)) {
            return Optional.empty();
        }
        for (BlockPos main : mainsAt(level, pos)) {
            BlockState state = level.getBlockState(main);
            if (state.getBlock() instanceof TubeBlock tube) {
                TubeShape shape = tube.shape(state);
                OptionalInt end = shape.endAt(between(main, pos), Directions.toDomain(side));
                if (end.isPresent()) {
                    return Optional.of(new Entry(main.immutable(), shape, end.getAsInt()));
                }
            }
        }
        return Optional.empty();
    }

    // what the tubes take up of this block
    static VoxelShape shapeAt(BlockGetter level, BlockPos pos) {
        VoxelShape shape = Shapes.empty();
        for (BlockPos main : mainsAt(level, pos)) {
            BlockState state = level.getBlockState(main);
            if (state.getBlock() instanceof TubeBlock tube) {
                shape = Shapes.or(shape, slice(tube.shape(state), between(main, pos)));
            }
        }
        return shape;
    }

    static VoxelShape slice(TubeShape shape, Offset block) {
        return SLICES.computeIfAbsent(new Slice(shape, block), slice -> {
            VoxelShape voxels = Shapes.empty();
            for (Box box : shape.boxesIn(block)) {
                double[] pixels = {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
                for (int index = 0; index < pixels.length; index++) {
                    pixels[index] = Math.round(pixels[index] * 16);
                }
                if (pixels[0] < pixels[3] && pixels[1] < pixels[4] && pixels[2] < pixels[5]) {
                    voxels = Shapes.or(voxels, Block.box(pixels[0], pixels[1], pixels[2], pixels[3], pixels[4], pixels[5]));
                }
            }
            return voxels.optimize();
        });
    }
}
