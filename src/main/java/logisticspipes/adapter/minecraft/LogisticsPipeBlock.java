package logisticspipes.adapter.minecraft;

import java.util.Map;

import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// both pipe kinds, routed ones take part in the network and connect to inventories
public class LogisticsPipeBlock extends Block implements EntityBlock {

    public static final Map<Direction, BooleanProperty> CONNECTED = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final VoxelShape[] SHAPES = buildShapes();

    private final PipeKind kind;
    private final int moduleSlots;
    private final PipeServices services;

    // kind is null for plain transport pipes, only chassis pipes have module slots
    public LogisticsPipeBlock(Properties properties, PipeKind kind, int moduleSlots, PipeServices services) {
        super(properties);
        this.kind = kind;
        this.moduleSlots = moduleSlots;
        this.services = services;
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : CONNECTED.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    public boolean routed() {
        return kind != null;
    }

    PipeKind kind() {
        return kind;
    }

    int moduleSlots() {
        return moduleSlots;
    }

    PipeServices services() {
        return services;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (kind == null || !(stack.getItem() instanceof UpgradeItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            // creative players keep their stack
            pipe.installUpgrade(player.hasInfiniteMaterials() ? stack.copy() : stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (kind != null && player.isShiftKeyDown()) {
            // sneaking with an empty hand takes the upgrades out
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                pipe.removeUpgrades().forEach(upgrade -> popResource(level, pos, upgrade));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == null || kind == PipeKind.PROVIDER) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            player.openMenu(pipe.menuProvider());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTED.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level world) {
            if (world.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                pipe.neighbourChanged();
            }
            return state.setValue(CONNECTED.get(direction), connectsTo(world, pos, direction));
        }
        return state;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            pipe.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        PipeTopology.markRoutersDirty(level, pos);
    }

    // for pipes that were set without a placement context, like commands and tests
    // not done in onPlace because the block entity would end up with the old state
    void refreshConnections(BlockState state, Level level, BlockPos pos) {
        BlockState connected = withConnections(state, level, pos);
        if (connected != state) {
            level.setBlock(pos, connected, Block.UPDATE_ALL);
        }
    }

    private BlockState withConnections(BlockState state, Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTED.get(direction), connectsTo(level, pos, direction));
        }
        return state;
    }

    private boolean connectsTo(Level level, BlockPos pos, Direction direction) {
        BlockPos neighbour = pos.relative(direction);
        if (level.getBlockState(neighbour).getBlock() instanceof LogisticsPipeBlock) {
            return true;
        }
        if (!routed()) {
            return false;
        }
        return level.getBlockState(neighbour).getBlock() instanceof PowerJunctionBlock
                || services.storage().exists(level, neighbour, direction.getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTED.get(direction))) {
                index |= 1 << direction.ordinal();
            }
        }
        return SHAPES[index];
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape core = Block.box(4, 4, 4, 12, 12, 12);
        VoxelShape[] arms = {
                Block.box(4, 0, 4, 12, 4, 12),
                Block.box(4, 12, 4, 12, 16, 12),
                Block.box(4, 4, 0, 12, 12, 4),
                Block.box(4, 4, 12, 12, 12, 16),
                Block.box(0, 4, 4, 4, 12, 12),
                Block.box(12, 4, 4, 16, 12, 12),
        };
        VoxelShape[] shapes = new VoxelShape[1 << arms.length];
        for (int index = 0; index < shapes.length; index++) {
            VoxelShape shape = core;
            for (int arm = 0; arm < arms.length; arm++) {
                if ((index & 1 << arm) != 0) {
                    shape = Shapes.or(shape, arms[arm]);
                }
            }
            shapes[index] = shape;
        }
        return shapes;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(services.blockEntityType().get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof PipeBlockEntity pipe) {
                if (tickLevel.isClientSide) {
                    pipe.clientTick();
                } else {
                    pipe.serverTick();
                }
            }
        };
    }
}
