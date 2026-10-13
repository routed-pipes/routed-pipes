package routedpipes.adapter.minecraft;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// fills the blocks a tube runs through besides its main block, the main block draws the whole tube
public class TubePartBlock extends Block implements EntityBlock {

    private final TubeServices services;

    public TubePartBlock(Properties properties, TubeServices services) {
        super(properties);
        this.services = services;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Tubes.shapeAt(level, pos);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return Tubes.mainsAt(level, pos).stream().findFirst()
                .map(main -> new ItemStack(level.getBlockState(main).getBlock())).orElse(ItemStack.EMPTY);
    }

    // in creative the tube goes without dropping itself
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            for (BlockPos main : List.copyOf(Tubes.mainsAt(level, pos))) {
                level.destroyBlock(main, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // breaking any part takes the whole tube down, it drops as one item where its main block was
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TubePartBlockEntity part) {
            for (BlockPos main : List.copyOf(part.mains())) {
                level.destroyBlock(main, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TubePartBlockEntity(services.partBlockEntityType().get(), pos, state);
    }
}
