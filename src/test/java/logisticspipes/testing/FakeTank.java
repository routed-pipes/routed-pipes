package logisticspipes.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import logisticspipes.domain.fluid.FluidBatch;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.Tank;
import logisticspipes.domain.fluid.TankAccess;

// a block with one tank next to a pipe
public final class FakeTank implements TankAccess {

    public final Tank tank;

    public FakeTank(int capacity) {
        tank = new Tank(capacity);
    }

    public FakeTank holding(FluidBatch batch) {
        tank.fill(batch, false);
        return this;
    }

    @Override
    public int fill(FluidBatch batch, boolean simulate) {
        return tank.fill(batch, simulate);
    }

    @Override
    public int room(FluidType type) {
        return tank.room(type);
    }

    @Override
    public Map<FluidType, Integer> contents() {
        Map<FluidType, Integer> contents = new LinkedHashMap<>();
        tank.content().ifPresent(content -> contents.put(content.type(), content.amount()));
        return contents;
    }

    @Override
    public int drain(FluidBatch batch, boolean simulate) {
        int available = Math.min(batch.amount(), tank.holding(batch.type()));
        return simulate ? available : tank.drain(available);
    }

    @Override
    public Optional<FluidBatch> drainAny(int upTo, boolean simulate) {
        return tank.content().filter(content -> upTo > 0)
                .map(content -> new FluidBatch(content.type(), drain(new FluidBatch(content.type(), upTo), simulate)));
    }
}
