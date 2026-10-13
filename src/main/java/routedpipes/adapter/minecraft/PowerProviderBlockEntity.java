package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;

import routedpipes.application.port.PowerProvider;
import routedpipes.domain.power.ProviderStore;
import routedpipes.domain.routing.RouterId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PowerProviderBlockEntity extends BlockEntity implements PowerProvider {

    private final ProviderStore store = new ProviderStore();

    public PowerProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int stored() {
        return store.stored();
    }

    // with what came in since the last tick
    public int storedWithInput() {
        return store.stored() + store.input();
    }

    // returns how much was taken in
    public int receive(int amount, boolean simulate) {
        int accepted = store.receive(amount, simulate);
        if (!simulate && accepted > 0) {
            setChanged();
        }
        return accepted;
    }

    @Override
    public void order(RouterId destination, int amount) {
        store.order(destination, amount);
    }

    void serverTick() {
        boolean changed = store.tick((destination, amount) -> ((PowerProviderBlock) getBlockState().getBlock()).networks()
                .of(level).subSystemPower().deliver(neighbours(), destination, amount));
        if (changed) {
            setChanged();
        }
    }

    // the routed pipes on all six sides
    private List<RouterId> neighbours() {
        List<RouterId> routers = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            if (level.isLoaded(target) && level.getBlockEntity(target) instanceof PipeBlockEntity pipe
                    && pipe.getBlockState().getBlock() instanceof LogisticsPipeBlock block && block.routed()) {
                routers.add(pipe.routerId());
            }
        }
        return routers;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", store.stored());
        tag.putInt("Input", store.input());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        store.load(tag.getInt("Energy"), tag.getInt("Input"));
    }
}
