package routedpipes.adapter.neoforge;

import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import routedpipes.domain.power.ProviderStore;
import net.neoforged.neoforge.energy.IEnergyStorage;

// lets generators and cables of other mods charge a power provider, only the pipes can take energy out
final class PowerProviderEnergy implements IEnergyStorage {

    private final PowerProviderBlockEntity provider;

    PowerProviderEnergy(PowerProviderBlockEntity provider) {
        this.provider = provider;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return provider.receive(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return provider.storedWithInput();
    }

    @Override
    public int getMaxEnergyStored() {
        return ProviderStore.CAPACITY;
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
