package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// remembers which tubes run through this block
public class TubePartBlockEntity extends BlockEntity {

    // swapped as a whole, the shape of the block is asked for from other threads too
    private volatile List<BlockPos> mains = List.of();

    public TubePartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // the main blocks of the tubes
    public List<BlockPos> mains() {
        return mains;
    }

    void addMain(BlockPos main) {
        List<BlockPos> more = new ArrayList<>(mains);
        more.add(main.immutable());
        mains = List.copyOf(more);
        changed();
    }

    // a part no tube runs through any more is gone
    void removeMain(BlockPos main) {
        if (!mains.contains(main)) {
            return;
        }
        mains = mains.stream().filter(other -> !other.equals(main)).toList();
        if (mains.isEmpty()) {
            level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            changed();
        }
    }

    private void changed() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLongArray("Mains", mains.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mains = Arrays.stream(tag.getLongArray("Mains")).mapToObj(BlockPos::of).toList();
    }
}
