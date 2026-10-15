package routedpipes.adapter.fabric;

import java.util.function.LongConsumer;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

// what was moved inside a transaction, the block only gets it once the outermost one commits
final class PendingAmount extends SnapshotParticipant<Long> {

    private final LongConsumer deliver;
    private long pending;

    PendingAmount(LongConsumer deliver) {
        this.deliver = deliver;
    }

    long pending() {
        return pending;
    }

    void add(long amount, TransactionContext transaction) {
        updateSnapshots(transaction);
        pending += amount;
    }

    @Override
    protected Long createSnapshot() {
        return pending;
    }

    @Override
    protected void readSnapshot(Long snapshot) {
        pending = snapshot;
    }

    @Override
    protected void onFinalCommit() {
        long amount = pending;
        pending = 0;
        deliver.accept(amount);
    }
}
