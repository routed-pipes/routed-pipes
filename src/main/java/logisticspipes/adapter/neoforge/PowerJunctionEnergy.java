package logisticspipes.adapter.neoforge;

import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

// lets generators and cables of other mods charge a junction, only the pipes can take energy out
final class PowerJunctionEnergy implements IEnergyStorage {

    private final PowerJunctionBlockEntity junction;

    PowerJunctionEnergy(PowerJunctionBlockEntity junction) {
        this.junction = junction;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return junction.receive(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return junction.stored();
    }

    @Override
    public int getMaxEnergyStored() {
        return PowerJunctionBlockEntity.CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
