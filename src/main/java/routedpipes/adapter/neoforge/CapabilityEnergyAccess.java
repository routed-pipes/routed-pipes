package routedpipes.adapter.neoforge;

import java.util.Optional;

import routedpipes.adapter.minecraft.EnergyAccess;
import routedpipes.domain.power.EnergyReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class CapabilityEnergyAccess implements EnergyAccess {

    private record Receiver(IEnergyStorage storage) implements EnergyReceiver {

        @Override
        public int missing() {
            return Math.max(0, storage.getMaxEnergyStored() - storage.getEnergyStored());
        }

        @Override
        public int receive(int amount) {
            return storage.receiveEnergy(amount, false);
        }
    }

    @Override
    public Optional<EnergyReceiver> receiver(Level level, BlockPos pos, Direction side) {
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        return storage == null || !storage.canReceive() ? Optional.empty() : Optional.of(new Receiver(storage));
    }
}
