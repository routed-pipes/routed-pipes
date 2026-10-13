package routedpipes.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import routedpipes.application.port.FluidNode;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidSink;
import routedpipes.domain.fluid.FluidSupply;
import routedpipes.domain.fluid.FluidType;
import routedpipes.domain.logistics.InventoryRef;
import routedpipes.domain.routing.RouterId;

// the fluid half of a pretend pipe
public final class FakeFluidNode implements FluidNode {

    public final Set<InventoryRef> tanks = new HashSet<>();
    public FluidSink sink;
    // how much the tanks next to the pipe still take
    public int room;

    public record Sent(FluidBatch batch, RouterId destination) {
    }

    public boolean provides;
    // what the tanks next to the pipe hold
    public final Map<FluidType, Integer> stock = new LinkedHashMap<>();
    public final List<Sent> sent = new ArrayList<>();

    public FakeFluidNode stock(FluidBatch batch) {
        provides = true;
        stock.put(batch.type(), batch.amount());
        return this;
    }

    public FluidSupply supply;
    // what each tank next to the pipe holds of the supplied fluid
    public final List<Integer> stocked = new ArrayList<>();

    @Override
    public Optional<FluidSupply> supply() {
        return Optional.ofNullable(supply);
    }

    @Override
    public List<Integer> stocked(FluidType type) {
        return stocked;
    }

    @Override
    public boolean provides() {
        return provides;
    }

    @Override
    public Map<FluidType, Integer> providable() {
        return new LinkedHashMap<>(stock);
    }

    @Override
    public int provide(FluidType type, int amount, RouterId destination) {
        int taken = Math.min(amount, stock.getOrDefault(type, 0));
        if (taken > 0) {
            stock.merge(type, -taken, Integer::sum);
            sent.add(new Sent(new FluidBatch(type, taken), destination));
        }
        return taken;
    }

    @Override
    public Set<InventoryRef> tanks() {
        return tanks;
    }

    @Override
    public Optional<FluidSink> sink() {
        return Optional.ofNullable(sink);
    }

    @Override
    public int sinkRoom(FluidType type) {
        return room;
    }
}
