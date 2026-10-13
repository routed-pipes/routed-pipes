package routedpipes.domain.fluid;

import java.util.Map;
import java.util.Optional;

// the tanks of a block next to a pipe
public interface TankAccess {

    // returns how much went in
    int fill(FluidBatch batch, boolean simulate);

    // how much of the fluid would fit into tanks that are empty or already hold it
    int room(FluidType type);

    // everything in the tanks
    Map<FluidType, Integer> contents();

    // takes out that fluid only, returns how much came out
    int drain(FluidBatch batch, boolean simulate);

    // takes out whatever the tanks hand out first
    Optional<FluidBatch> drainAny(int upTo, boolean simulate);
}
