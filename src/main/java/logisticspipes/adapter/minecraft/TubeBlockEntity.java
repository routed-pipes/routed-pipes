package logisticspipes.adapter.minecraft;

import java.util.List;
import java.util.Optional;

import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.domain.transport.TubeItem;
import logisticspipes.domain.transport.TubeTransport;
import logisticspipes.domain.tube.TubeEnd;
import logisticspipes.domain.tube.TubeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// holds the items of a whole tube, however many blocks it fills
public class TubeBlockEntity extends BlockEntity {

    private final TubeTransport transport = new TubeTransport();

    public TubeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TubeShape shape() {
        return tube().shape(getBlockState());
    }

    private TubeBlock tube() {
        return (TubeBlock) getBlockState().getBlock();
    }

    public List<TubeItem> travellingItems() {
        return transport.items();
    }

    // hands an item leaving a pipe to the tube that starts at the target block, false when there is none
    static boolean takeFromPipe(Level level, BlockPos target, Direction from, TravellingItem item) {
        Optional<Tubes.Entry> entry = Tubes.entryAt(level, target, from).filter(Tubes.Entry::pipeEnd);
        if (entry.isEmpty() || !(level.getBlockEntity(entry.get().main()) instanceof TubeBlockEntity tube)) {
            return false;
        }
        tube.accept(TubeItem.entering(item, entry.get().end()));
        return true;
    }

    // the same for a viewer who guesses where the item went
    static void expectFromPipe(Level level, BlockPos target, Direction from, TravellingItem item) {
        Tubes.entryAt(level, target, from).filter(Tubes.Entry::pipeEnd).ifPresent(entry -> {
            if (level.getBlockEntity(entry.main()) instanceof TubeBlockEntity tube) {
                tube.transport.expect(TubeItem.entering(item, entry.end()));
            }
        });
    }

    private void accept(TubeItem item) {
        transport.insert(item);
        setChanged();
        // tell clients now, a tube that already ticked would only do so a tick later and the item would blink
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // where an item leaving through the given end comes out
    private BlockPos beyond(TubeEnd end) {
        return Tubes.at(worldPosition, end.block()).relative(Directions.toMinecraft(end.side()));
    }

    // moves items on without waiting for the server
    void clientTick() {
        TubeShape shape = shape();
        for (TubeItem item : transport.tick(level.getGameTime(), shape.kind().length())) {
            int out = 1 - item.entry();
            TubeEnd end = shape.end(out);
            Direction back = Directions.toMinecraft(end.side()).getOpposite();
            BlockPos target = beyond(end);
            Optional<Tubes.Entry> next = Tubes.entryAt(level, target, back);
            if (next.isPresent() && level.getBlockEntity(next.get().main()) instanceof TubeBlockEntity tube) {
                tube.transport.expect(item.enteringNext(next.get().end(), shape.kind().length()));
            } else if (shape.kind().pipeEnd(out) && level.getBlockEntity(target) instanceof PipeBlockEntity pipe) {
                pipe.expectFromTube(item.leaving(Directions.toDomain(back)));
            }
        }
    }

    void serverTick() {
        if (!transport.isEmpty()) {
            TubeShape shape = shape();
            for (TubeItem item : transport.tick(level.getGameTime(), shape.kind().length())) {
                arrive(shape, item);
            }
            setChanged();
        }
        if (transport.pollChanged()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // an item is through: on into the next tube, or out into a pipe at the end made for that
    private void arrive(TubeShape shape, TubeItem item) {
        int out = 1 - item.entry();
        TubeEnd end = shape.end(out);
        Direction back = Directions.toMinecraft(end.side()).getOpposite();
        BlockPos target = beyond(end);
        if (!Tubes.loadedAt(level, target)) {
            // wait at the chunk border and try again next tick
            item.holdBefore(shape.kind().length());
            transport.insert(item);
            return;
        }
        Optional<Tubes.Entry> next = Tubes.entryAt(level, target, back);
        if (next.isPresent() && level.getBlockEntity(next.get().main()) instanceof TubeBlockEntity tube) {
            tube.accept(item.enteringNext(next.get().end(), shape.kind().length()));
            return;
        }
        if (shape.kind().pipeEnd(out) && level.getBlockEntity(target) instanceof PipeBlockEntity pipe) {
            pipe.acceptFromTube(item.leaving(Directions.toDomain(back)));
            return;
        }
        if (item.mode() == TransportMode.REQUESTED && item.destination().isPresent()) {
            tube().services().networks().of(level).itemLost(item.destination().get(), item.batch(), level.getGameTime());
        }
        popOut(McItemType.stackOf(item.batch()), Tubes.at(worldPosition, end.block()), target);
    }

    // an item at an open end falls out of it, or ends up on top of the tube when the end is blocked
    private void popOut(ItemStack stack, BlockPos last, BlockPos target) {
        boolean open = level.getBlockState(target).getCollisionShape(level, target).isEmpty();
        ItemEntity item = open
                ? new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.4, target.getZ() + 0.5, stack, 0, 0, 0)
                : new ItemEntity(level, last.getX() + 0.5, last.getY() + 0.85, last.getZ() + 0.5, stack, 0, 0, 0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    void dropContents() {
        for (TubeItem item : transport.clear()) {
            Block.popResource(level, worldPosition, McItemType.stackOf(item.batch()));
        }
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
        tag.put("Items", TubeTags.save(transport, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        TubeTags.load(transport, tag.getList("Items", Tag.TAG_COMPOUND), registries);
    }
}
