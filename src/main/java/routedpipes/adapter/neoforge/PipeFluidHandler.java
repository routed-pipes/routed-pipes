package routedpipes.adapter.neoforge;

import routedpipes.adapter.minecraft.Directions;
import routedpipes.adapter.minecraft.FluidPipe;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.PipeTanks;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

// lets pumps and other mods push fluid into the tank on one side of a fluid pipe, nothing can be pulled out
final class PipeFluidHandler implements IFluidHandler {

    private final FluidPipe pipe;
    private final Direction side;

    PipeFluidHandler(FluidPipe pipe, Direction side) {
        this.pipe = pipe;
        this.side = side;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return pipe.pipeTanks().side(Directions.toDomain(side)).content().map(CapabilityFluidStorage::stackOf).orElse(FluidStack.EMPTY);
    }

    @Override
    public int getTankCapacity(int tank) {
        return PipeTanks.SIDE_CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        return pipe.fill(side, new FluidBatch(CapabilityFluidStorage.typeOf(resource), resource.getAmount()), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
