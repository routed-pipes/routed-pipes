package logisticspipes.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.fluid.FluidSink;
import logisticspipes.domain.fluid.FluidSupply;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.routing.RouterId;

// what the network needs to know about a fluid pipe and the tanks around it
public interface FluidNode {

    // the tanks next to the pipe, two pipes on the same tank never send to each other
    Set<InventoryRef> tanks();

    // the fluids the pipe takes when nobody asked for them, empty if it takes none
    default Optional<FluidSink> sink() {
        return Optional.empty();
    }

    // how much of the fluid the tanks next to the pipe still take
    default int sinkRoom(FluidType type) {
        return 0;
    }

    // whether the fluid in the tanks next to the pipe can be ordered
    default boolean provides() {
        return false;
    }

    // what can be taken out of the tanks next to the pipe
    default Map<FluidType, Integer> providable() {
        return Map.of();
    }

    // takes fluid out of the tanks and sends it on its way packed, returns how much went out
    default int provide(FluidType type, int amount, RouterId destination) {
        return 0;
    }

    // supplier pipes only, the stock the pipe keeps up
    default Optional<FluidSupply> supply() {
        return Optional.empty();
    }

    // how much of the fluid is there already, one number for every tank next to the pipe
    default List<Integer> stocked(FluidType type) {
        return List.of();
    }
}
