package routedpipes.adapter.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PowerJunctionBlockEntity extends BlockEntity {

    public static final int CAPACITY = 4_000_000;

    private int stored;

    public PowerJunctionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int stored() {
        return stored;
    }

    // returns how much was taken in
    public int receive(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, CAPACITY - stored));
        if (!simulate && accepted > 0) {
            stored += accepted;
            setChanged();
        }
        return accepted;
    }

    // returns how much was handed out
    public int draw(int amount, boolean simulate) {
        int drawn = Math.max(0, Math.min(amount, stored));
        if (!simulate && drawn > 0) {
            stored -= drawn;
            setChanged();
        }
        return drawn;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.min(tag.getInt("Energy"), CAPACITY);
    }
}
