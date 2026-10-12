package logisticspipes.adapter.minecraft;

import net.minecraft.core.BlockPos;
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

// goes next to a routed pipe and draws how the amounts of chosen items in its network change over the day
public class StatisticsTableBlock extends Block implements EntityBlock {

    private final StatisticsTableServices services;

    public StatisticsTableBlock(Properties properties, StatisticsTableServices services) {
        super(properties);
        this.services = services;
    }

    StatisticsTableServices services() {
        return services;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatisticsTableBlockEntity(services.blockEntityType().get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StatisticsTableBlockEntity table) {
            player.openMenu(table.menuProvider());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof StatisticsTableBlockEntity table) {
                table.serverTick();
            }
        };
    }
}
