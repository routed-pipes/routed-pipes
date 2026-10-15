package routedpipes.adapter.fabric;

import routedpipes.adapter.minecraft.FluidPipe;
import routedpipes.adapter.minecraft.McFluidType;
import routedpipes.domain.fluid.FluidBatch;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;

// lets pumps and other mods push fluid into the tank on one side of a fluid pipe, nothing can be pulled out
final class PipeFluidInput implements InsertionOnlyStorage<FluidVariant> {

    private final FluidPipe pipe;
    private final Direction side;
    private McFluidType type;
    private final PendingAmount pending;

    PipeFluidInput(FluidPipe pipe, Direction side) {
        this.pipe = pipe;
        this.side = side;
        this.pending = new PendingAmount(amount -> pipe.fill(side, new FluidBatch(type, (int) amount), false));
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        int wanted = TransferFluidStorage.millibuckets(maxAmount);
        if (resource.isBlank() || wanted == 0) {
            return 0;
        }
        McFluidType offered = TransferFluidStorage.typeOf(resource);
        // one fluid per transaction, the tank of a side holds one anyway
        if (pending.pending() > 0 && !offered.equals(type)) {
            return 0;
        }
        type = offered;
        int already = (int) pending.pending();
        int fits = pipe.fill(side, new FluidBatch(offered, already + wanted), true) - already;
        if (fits <= 0) {
            return 0;
        }
        pending.add(fits, transaction);
        return fits * TransferFluidStorage.DROPLETS_PER_MB;
    }
}
