package routedpipes.adapter.minecraft;

import java.util.function.Predicate;

import routedpipes.domain.tube.Offset;
import routedpipes.domain.tube.PartType;
import routedpipes.domain.tube.TubeKind;
import routedpipes.domain.tube.TubePart;
import routedpipes.domain.tube.TubeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// the main block of a high speed tube, the other blocks it fills are tube parts
public class TubeBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // only tubes that move over sideways come in two kinds
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    private final TubeKind kind;
    private final TubeServices services;

    public TubeBlock(Properties properties, TubeKind kind, TubeServices services) {
        super(properties);
        this.kind = kind;
        this.services = services;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MIRRORED, false));
    }

    public TubeKind kind() {
        return kind;
    }

    TubeServices services() {
        return services;
    }

    public TubeShape shape(BlockState state) {
        return new TubeShape(kind, Directions.toDomain(state.getValue(FACING)), state.getValue(MIRRORED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MIRRORED);
    }

    // true when nothing is in the way of any block the tube would fill
    // a block of another tube is not in the way when the two leave each other room there
    public boolean fits(Level level, BlockPos main, TubeShape shape, Predicate<BlockPos> allowed) {
        Predicate<BlockPos> inReach = pos -> level.isInWorldBounds(pos) && level.isLoaded(pos) && allowed.test(pos);
        if (!inReach.test(main) || !level.getBlockState(main).canBeReplaced()) {
            return false;
        }
        for (TubePart part : shape.parts()) {
            BlockPos pos = Tubes.at(main, part.offset());
            if (!inReach.test(pos) || !level.getBlockState(pos).canBeReplaced() && !shares(level, pos, part.type())) {
                return false;
            }
        }
        return true;
    }

    private static boolean shares(Level level, BlockPos pos, PartType type) {
        return level.getBlockState(pos).getBlock() instanceof TubePartBlock && PartType.canShare(Tubes.typesAt(level, pos), type);
    }

    // puts the whole tube down, or nothing at all when it does not fit
    public boolean build(Level level, BlockPos main, TubeShape shape, Predicate<BlockPos> allowed) {
        if (!fits(level, main, shape, allowed)) {
            return false;
        }
        level.setBlock(main, defaultBlockState().setValue(FACING, Directions.toMinecraft(shape.facing()))
                .setValue(MIRRORED, shape.mirrored()), Block.UPDATE_ALL);
        for (TubePart part : shape.parts()) {
            BlockPos pos = Tubes.at(main, part.offset());
            if (!(level.getBlockState(pos).getBlock() instanceof TubePartBlock)) {
                level.setBlock(pos, services.part().get().defaultBlockState(), Block.UPDATE_ALL);
            }
            if (level.getBlockEntity(pos) instanceof TubePartBlockEntity entity) {
                entity.addMain(main);
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
        for (TubePart part : shape.parts()) {
            if (level.getBlockEntity(Tubes.at(pos, part.offset())) instanceof TubePartBlockEntity entity) {
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
