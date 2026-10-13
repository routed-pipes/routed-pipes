package logisticspipes.adapter.minecraft;

import java.util.function.Supplier;

import logisticspipes.domain.power.ProviderStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

// stores energy of other mods and sends it through the pipes to the machines that need it
public class PowerProviderBlock extends Block implements EntityBlock {

    private final Supplier<BlockEntityType<PowerProviderBlockEntity>> blockEntityType;
    private final LevelNetworks networks;

    public PowerProviderBlock(Properties properties, Supplier<BlockEntityType<PowerProviderBlockEntity>> blockEntityType,
            LevelNetworks networks) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.networks = networks;
    }

    LevelNetworks networks() {
        return networks;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerProviderBlockEntity(blockEntityType.get(), pos, state);
    }

    // shows how full it is
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PowerProviderBlockEntity provider) {
            player.displayClientMessage(Component.translatable("routedpipes.power_provider.stored",
                    provider.stored(), ProviderStore.CAPACITY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof PowerProviderBlockEntity provider) {
                provider.serverTick();
            }
        };
    }
}
