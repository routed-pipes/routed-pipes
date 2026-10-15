package routedpipes.adapter.fabric;

import java.util.Optional;

import routedpipes.adapter.minecraft.EnergyAccess;
import routedpipes.domain.power.EnergyReceiver;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import team.reborn.energy.api.EnergyStorage;

// energy on Fabric goes through the api of Team Reborn, one unit of it is one RF
public final class RebornEnergyAccess implements EnergyAccess {

    private record Receiver(EnergyStorage storage) implements EnergyReceiver {

        @Override
        public int missing() {
            return (int) Math.min(Math.max(0, storage.getCapacity() - storage.getAmount()), Integer.MAX_VALUE);
        }

        @Override
        public int receive(int amount) {
            try (Transaction transaction = Transaction.openOuter()) {
                long received = storage.insert(amount, transaction);
                transaction.commit();
                return (int) received;
            }
        }
    }

    @Override
    public Optional<EnergyReceiver> receiver(Level level, BlockPos pos, Direction side) {
        EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, side);
        return storage == null || !storage.supportsInsertion() ? Optional.empty() : Optional.of(new Receiver(storage));
    }
}
