package logisticspipes.adapter.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// goes next to basic logistics pipes, teaches a disk programs and flashes them onto a programmer
public class ProgramCompilerBlock extends Block implements EntityBlock {

    private final ProgramCompilerServices services;

    public ProgramCompilerBlock(Properties properties, ProgramCompilerServices services) {
        super(properties);
        this.services = services;
    }

    ProgramCompilerServices services() {
        return services;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProgramCompilerBlockEntity(services.blockEntityType().get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ProgramCompilerBlockEntity compiler) {
            player.openMenu(compiler.menuProvider());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ProgramCompilerBlockEntity compiler) {
            Containers.dropContents(level, pos, compiler.slots());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ProgramCompilerBlockEntity compiler) {
                compiler.serverTick();
            }
        };
    }
}
