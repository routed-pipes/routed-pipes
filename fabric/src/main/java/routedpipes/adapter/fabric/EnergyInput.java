package routedpipes.adapter.fabric;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

// lets generators and cables of other mods charge a junction or a power provider, only the pipes can take energy out
final class EnergyInput implements EnergyStorage {

    interface Target {
        // returns how much it took
        int receive(int amount, boolean simulate);

        int stored();

        int capacity();
    }

    private final Target target;
    private final PendingAmount pending;

    EnergyInput(Target target) {
        this.target = target;
        this.pending = new PendingAmount(amount -> target.receive((int) amount, false));
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        int already = (int) pending.pending();
        int taken = target.receive((int) Math.min(already + maxAmount, Integer.MAX_VALUE), true) - already;
        if (taken <= 0) {
            return 0;
        }
        pending.add(taken, transaction);
        return taken;
    }

    @Override
    public boolean supportsExtraction() {
        return false;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public long getAmount() {
        return target.stored() + pending.pending();
    }

    @Override
    public long getCapacity() {
        return target.capacity();
    }
}
