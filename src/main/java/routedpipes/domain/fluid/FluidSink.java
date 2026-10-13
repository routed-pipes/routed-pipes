package routedpipes.domain.fluid;

import java.util.Set;

// the fluids a pipe takes when nobody asked for them
public record FluidSink(Set<FluidType> filter, Priority priority) {

    // a later one wins
    public enum Priority {
        TERMINUS,
        FLUID_SINK
    }

    public FluidSink {
        filter = Set.copyOf(filter);
    }

    // how much of the batch is taken with that much room and that much already on its way, 0 for none
    public int amount(FluidBatch offered, int room, int onRoute) {
        if (!filter.contains(offered.type())) {
            return 0;
        }
        return Math.max(0, Math.min(offered.amount(), room - onRoute));
    }
}
