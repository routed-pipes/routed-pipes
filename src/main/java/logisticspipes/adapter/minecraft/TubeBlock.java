package logisticspipes.adapter.minecraft;

import java.util.List;
import java.util.function.Predicate;

import logisticspipes.domain.tube.Offset;
import logisticspipes.domain.tube.TubeKind;
import logisticspipes.domain.tube.TubeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// the main block of a high speed tube, the other blocks it fills are tube parts
public class TubeBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final TubeKind kind;
    private final TubeServices services;

    public TubeBlock(Properties properties, TubeKind kind, TubeServices services) {
        super(properties);
        this.kind = kind;
        this.services = services;
    }

    public TubeKind kind() {
        return kind;
    }

    TubeServices services() {
        return services;
    }

    public TubeShape shape(BlockState state) {
        return new TubeShape(kind, Directions.toDomain(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static List<BlockPos> blocks(BlockPos main, TubeShape shape) {
        return shape.parts().stream().map(offset -> Tubes.at(main, offset)).toList();
    }

    // true when nothing is in the way of any block the tube would fill
    public boolean fits(Level level, BlockPos main, TubeShape shape, Predicate<BlockPos> allowed) {
        Predicate<BlockPos> free = pos -> level.isInWorldBounds(pos) && level.isLoaded(pos)
                && level.getBlockState(pos).canBeReplaced() && allowed.test(pos);
        return free.test(main) && blocks(main, shape).stream().allMatch(free);
    }

    // puts the whole tube down, or nothing at all when it does not fit
    public boolean build(Level level, BlockPos main, TubeShape shape, Predicate<BlockPos> allowed) {
        if (!fits(level, main, shape, allowed)) {
            return false;
        }
        level.setBlock(main, defaultBlockState().setValue(FACING, Directions.toMinecraft(shape.facing())), Block.UPDATE_ALL);
        for (BlockPos pos : blocks(main, shape)) {
            level.setBlock(pos, services.part().get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof TubePartBlockEntity part) {
                part.addMain(main);
            }
        }
        PipeTopology.markRoutersDirty(level, main, shape);
        return true;
    }

    // the parts go with the main block
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TubeBlockEntity tube) {
            tube.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        TubeShape shape = shape(state);
        for (BlockPos part : blocks(pos, shape)) {
            if (level.getBlockEntity(part) instanceof TubePartBlockEntity entity) {
                entity.removeMain(pos);
            }
        }
        PipeTopology.markRoutersDirty(level, pos, shape);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Tubes.slice(shape(state), Offset.ZERO);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TubeBlockEntity(services.blockEntityType().get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TubeBlockEntity tube) {
                if (tickLevel.isClientSide) {
                    tube.clientTick();
                } else {
                    tube.serverTick();
                }
            }
        };
    }
}
