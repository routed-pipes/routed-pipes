package routedpipes.adapter.minecraft;

import routedpipes.domain.security.Access;
import routedpipes.domain.security.SecuritySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// makes security cards and decides who may do what with the pipes locked by them
public class SecurityStationBlock extends Block implements EntityBlock {

    private final SecurityStationServices services;

    public SecurityStationBlock(Properties properties, SecurityStationServices services) {
        super(properties);
        this.services = services;
    }

    SecurityStationServices services() {
        return services;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityStationBlockEntity(services.blockEntityType().get(), pos, state);
    }

    // whoever places the station owns it
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof SecurityStationBlockEntity station) {
            station.station().settings().setOwner(player.getUUID());
            SecurityStations.of(level).setDirty();
        }
    }

    // only the owner gets at the station, one that was not placed by a player belongs to the first who uses it
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SecurityStationBlockEntity station) {
            SecuritySettings settings = station.station().settings();
            if (settings.owner().isEmpty()) {
                settings.setOwner(player.getUUID());
                SecurityStations.of(level).setDirty();
            }
            if (PipeSecurity.told(player, settings.isOwner(player.getUUID()) ? Access.ALLOWED : Access.DENIED)) {
                player.openMenu(station.menuProvider());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // the cards are worth nothing without their station, so they go with it
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SecurityStationBlockEntity station) {
            SecurityStations.of(level).remove(station.id());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
