package logisticspipes.adapter.minecraft;

import java.util.Map;
import java.util.Optional;

import logisticspipes.adapter.minecraft.menu.StatisticsTableMenu;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.statistics.AmountHistory;
import logisticspipes.domain.statistics.AmountTracking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// writes down once a minute how many of the tracked items the network of the pipe next to it has
public class StatisticsTableBlockEntity extends BlockEntity {

    private final AmountTracking tracking = new AmountTracking();

    public StatisticsTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public AmountTracking tracking() {
        return tracking;
    }

    // the first routed pipe next to the table, its network is the one the table looks at
    public Optional<PipeBlockEntity> pipe() {
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            if (level.isLoaded(target) && level.getBlockEntity(target) instanceof PipeBlockEntity pipe && pipe.kind() != null) {
                return Optional.of(pipe);
            }
        }
        return Optional.empty();
    }

    // starts tracking an item with the amount there is right now, false if it is tracked already or there is no pipe
    public boolean track(ItemType type) {
        Optional<PipeBlockEntity> pipe = pipe();
        if (pipe.isEmpty() || !tracking.track(type, pipe.get().amounts().getOrDefault(type, 0))) {
            return false;
        }
        setChanged();
        return true;
    }

    public void untrack(ItemType type) {
        tracking.untrack(type);
        setChanged();
    }

    // without a pipe nothing is written down
    void serverTick() {
        if (!tracking.due() || tracking.items().isEmpty()) {
            return;
        }
        pipe().ifPresent(pipe -> {
            Map<ItemType, Integer> amounts = pipe.amounts();
            tracking.record(type -> amounts.getOrDefault(type, 0));
            setChanged();
        });
    }

    MenuProvider menuProvider() {
        StatisticsTableBlock block = (StatisticsTableBlock) getBlockState().getBlock();
        return new SimpleMenuProvider((id, inventory, player) -> new StatisticsTableMenu(block.services().menu().get(), id, this),
                getBlockState().getBlock().getName());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag tracked = new ListTag();
        for (ItemType type : tracking.items()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", McItemType.stackOf(new ItemBatch(type, 1)).save(registries));
            entry.putLongArray("Samples", tracking.history(type).orElseThrow().oldestFirst());
            tracked.add(entry);
        }
        tag.put("Tracked", tracked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag tracked = tag.getList("Tracked", Tag.TAG_COMPOUND);
        for (int index = 0; index < tracked.size(); index++) {
            CompoundTag entry = tracked.getCompound(index);
            ItemStack.parse(registries, entry.getCompound("Stack")).filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> tracking.restore(McItemType.batchOf(stack).type(),
                            AmountHistory.of(entry.getLongArray("Samples"))));
        }
    }
}
