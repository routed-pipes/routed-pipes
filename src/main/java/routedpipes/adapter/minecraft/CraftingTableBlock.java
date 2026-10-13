package routedpipes.adapter.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// crafts its recipe from stored ingredients whenever something takes the result out
public class CraftingTableBlock extends Block implements EntityBlock {

    private final CraftingTableServices services;

    public CraftingTableBlock(Properties properties, CraftingTableServices services) {
        super(properties);
        this.services = services;
    }

    CraftingTableServices services() {
        return services;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingTableBlockEntity(services.blockEntityType().get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CraftingTableBlockEntity table) {
            player.openMenu(table.menuProvider());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CraftingTableBlockEntity table) {
            Containers.dropContents(level, pos, table.storage());
            Containers.dropContents(level, pos, table.surplus());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
