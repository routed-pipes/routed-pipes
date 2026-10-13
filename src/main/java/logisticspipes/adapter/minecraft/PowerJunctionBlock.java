package logisticspipes.adapter.minecraft;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// stores energy for the routed pipes next to it and everything they are connected to
public class PowerJunctionBlock extends Block implements EntityBlock {

    private final Supplier<BlockEntityType<PowerJunctionBlockEntity>> blockEntityType;

    public PowerJunctionBlock(Properties properties, Supplier<BlockEntityType<PowerJunctionBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerJunctionBlockEntity(blockEntityType.get(), pos, state);
    }

    // shows how full it is
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PowerJunctionBlockEntity junction) {
            player.displayClientMessage(Component.translatable("routedpipes.power_junction.stored",
                    junction.stored(), PowerJunctionBlockEntity.CAPACITY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
