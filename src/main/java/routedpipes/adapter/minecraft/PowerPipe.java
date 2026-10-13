package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;

import routedpipes.application.port.PowerNode;
import routedpipes.application.port.PowerProvider;
import routedpipes.domain.logistics.UpgradeKind;
import routedpipes.domain.power.EnergyReceiver;
import routedpipes.domain.power.PowerBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

// what a routed pipe does with the energy of power providers
// with a supplier upgrade it feeds the machines next to it
public final class PowerPipe implements PowerNode {

    private final PipeBlockEntity pipe;
    private final PowerBuffer buffer = new PowerBuffer();

    PowerPipe(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    // what waits in the pipe for the machines
    public int buffered() {
        return buffer.stored();
    }

    void load(int stored) {
        buffer.load(stored);
    }

    private boolean supplies() {
        return pipe.hasUpgrade(UpgradeKind.POWER_SUPPLIER_RF);
    }

    // a pipe that supplies lets the energy through too
    @Override
    public boolean passes() {
        return supplies() || pipe.hasUpgrade(UpgradeKind.POWER_TRANSPORTATION);
    }

    @Override
    public List<PowerProvider> providers() {
        List<PowerProvider> providers = new ArrayList<>();
        Level level = pipe.getLevel();
        for (Direction side : Direction.values()) {
            BlockPos target = pipe.getBlockPos().relative(side);
            if (level.isLoaded(target) && level.getBlockEntity(target) instanceof PowerProviderBlockEntity provider) {
                providers.add(provider);
            }
        }
        return providers;
    }

    @Override
    public void receive(int amount) {
        buffer.add(amount);
        pipe.setChanged();
    }

    void serverTick() {
        if (!supplies()) {
            return;
        }
        List<EnergyReceiver> machines = machines();
        List<Integer> needs = new ArrayList<>();
        for (EnergyReceiver machine : machines) {
            needs.add(machine.missing());
        }
        int[] offers = buffer.offers(needs);
        for (int index = 0; index < offers.length; index++) {
            if (offers[index] > 0) {
                buffer.take(machines.get(index).receive(offers[index]));
                pipe.setChanged();
            }
        }
        if (buffer.missing() > 0) {
            pipe.network().subSystemPower().refill(pipe.routerId(), buffer.missing());
        }
    }

    // what takes energy on the sides that are not cut off, never the blocks of the network itself
    private List<EnergyReceiver> machines() {
        List<EnergyReceiver> machines = new ArrayList<>();
        Level level = pipe.getLevel();
        EnergyAccess energy = ((LogisticsPipeBlock) pipe.getBlockState().getBlock()).services().energy();
        for (Direction side : Direction.values()) {
            BlockPos target = pipe.getBlockPos().relative(side);
            if (pipe.isDisconnected(side) || !level.isLoaded(target)) {
                continue;
            }
            BlockEntity neighbour = level.getBlockEntity(target);
            if (neighbour instanceof PipeBlockEntity || neighbour instanceof PowerProviderBlockEntity
                    || neighbour instanceof PowerJunctionBlockEntity) {
                continue;
            }
            energy.receiver(level, target, side.getOpposite()).ifPresent(machines::add);
        }
        return machines;
    }
}
