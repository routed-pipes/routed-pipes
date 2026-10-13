package routedpipes.adapter.minecraft;

import java.util.Optional;

import routedpipes.domain.power.EnergyReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

// access to machines in the world that take energy, every loader has its own api for this
public interface EnergyAccess {

    // the block as a taker of energy through that side, empty if it takes none there
    Optional<EnergyReceiver> receiver(Level level, BlockPos pos, Direction side);
}
