package routedpipes.adapter.fabric;

import java.util.ArrayList;
import java.util.List;

import routedpipes.adapter.minecraft.PipeBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

// lets hoppers and other mods push items into a pipe, nothing can be pulled out
final class PipeItemInput extends SnapshotParticipant<List<ItemStack>> implements InsertionOnlyStorage<ItemVariant> {

    private final PipeBlockEntity pipe;
    private final Direction side;
    private List<ItemStack> pending = new ArrayList<>();

    PipeItemInput(PipeBlockEntity pipe, Direction side) {
        this.pipe = pipe;
        this.side = side;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        pending.add(resource.toStack(amount));
        return amount;
    }

    @Override
    protected List<ItemStack> createSnapshot() {
        return new ArrayList<>(pending);
    }

    @Override
    protected void readSnapshot(List<ItemStack> snapshot) {
        pending = snapshot;
    }

    @Override
    protected void onFinalCommit() {
        List<ItemStack> arrived = pending;
        pending = new ArrayList<>();
        arrived.forEach(stack -> pipe.accept(stack, side));
    }
}
