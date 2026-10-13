package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.logistics.SneakyFaces;
import logisticspipes.domain.logistics.UpgradeKind;
import logisticspipes.domain.routing.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// the upgrades of the pipe itself: how they get in and out, the faces sneaky upgrades pick
// and the sides a disconnection upgrade cuts off
// how many there are of a kind is counted by the pipe, its tick asks for that
final class PipeUpgrades {

    private final PipeBlockEntity pipe;
    private final SimpleContainer slots = new SimpleContainer(PipeBlockEntity.UPGRADE_SLOTS);
    // the row of sneaky upgrades a combination upgrade unlocks
    private final SimpleContainer sneakyRow = new SimpleContainer(PipeBlockEntity.UPGRADE_SLOTS);
    private SneakyFaces facesCache;
    // sides cut off with a disconnection upgrade, one bit per direction
    private int disconnected;

    PipeUpgrades(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    SimpleContainer slots() {
        return slots;
    }

    SimpleContainer sneakyRow() {
        return sneakyRow;
    }

    // puts one upgrade from the stack into the pipe, false when it does not fit this pipe or there is no room
    boolean install(ItemStack stack) {
        if (!pipe.block().routed() || !(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.kind().fitsPipe(pipe.kind())) {
            return false;
        }
        // with a combination upgrade in the pipe sneaky upgrades go into its row first
        for (int slot = 0; slot < PipeBlockEntity.UPGRADE_SLOTS && upgrade.kind() == UpgradeKind.SNEAKY
                && pipe.hasUpgrade(UpgradeKind.SNEAKY_COMBINATION); slot++) {
            if (sneakyRow.getItem(slot).isEmpty()) {
                sneakyRow.setItem(slot, stack.split(1));
                return true;
            }
        }
        for (int slot = 0; slot < PipeBlockEntity.UPGRADE_SLOTS; slot++) {
            ItemStack present = slots.getItem(slot);
            if (present.isEmpty()) {
                slots.setItem(slot, stack.split(1));
                return true;
            }
            if (ItemStack.isSameItemSameComponents(present, stack) && present.getCount() < PipeBlockEntity.UPGRADE_STACK) {
                stack.shrink(1);
                present.grow(1);
                slots.setChanged();
                return true;
            }
        }
        return false;
    }

    // takes all upgrades out again
    List<ItemStack> removeAll() {
        List<ItemStack> removed = new ArrayList<>(slots.removeAllItems());
        removed.addAll(sneakyRow.removeAllItems());
        return removed;
    }

    // the face of an inventory this pipe uses when the inventory is on the given side of the pipe
    Direction accessFace(Direction side) {
        return Directions.toMinecraft(faces().reading(Directions.toDomain(side.getOpposite())));
    }

    static Optional<Side> sneakyFace(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeKind.SNEAKY
                ? Optional.of(Directions.toDomain(UpgradeItem.sideOf(stack))) : Optional.empty();
    }

    // what the sneaky upgrades of the pipe itself say
    SneakyFaces faces() {
        if (facesCache == null) {
            List<SneakyFaces.Slot> faces = new ArrayList<>();
            List<Side> row = new ArrayList<>();
            for (int slot = 0; slot < PipeBlockEntity.UPGRADE_SLOTS; slot++) {
                ItemStack stack = slots.getItem(slot);
                boolean combination = stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeKind.SNEAKY_COMBINATION;
                faces.add(combination ? SneakyFaces.Slot.COMBINATION
                        : sneakyFace(stack).map(SneakyFaces.Slot::sneaky).orElse(SneakyFaces.Slot.OTHER));
                sneakyFace(sneakyRow.getItem(slot)).ifPresent(row::add);
            }
            facesCache = SneakyFaces.of(faces, row);
        }
        return facesCache;
    }

    void forgetFaces() {
        facesCache = null;
    }

    boolean isDisconnected(Direction side) {
        return (disconnected & 1 << side.get3DDataValue()) != 0;
    }

    // as it is saved
    int disconnected() {
        return disconnected;
    }

    void loadDisconnected(int saved) {
        disconnected = saved;
    }

    // cuts a side off or connects it again, the upgrade in the hand goes in if the pipe has none yet
    void toggleDisconnected(ItemStack held, Direction side) {
        if (pipe.upgradeCount(UpgradeKind.DISCONNECTION) == 0 && !install(held)) {
            return;
        }
        disconnected ^= 1 << side.get3DDataValue();
        pipe.setChanged();
        reconnect();
    }

    // called when the slots changed
    void connectWithoutUpgrade() {
        if (disconnected != 0 && pipe.upgradeCount(UpgradeKind.DISCONNECTION) == 0) {
            // without the upgrade everything is connected again
            disconnected = 0;
            reconnect();
        }
    }

    // works out the connections of this pipe and the ones around it again
    private void reconnect() {
        Level level = pipe.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = pipe.getBlockPos();
        pipe.block().refreshConnections(pipe.getBlockState(), level, pos);
        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.relative(side);
            if (level.getBlockState(neighbour).getBlock() instanceof LogisticsPipeBlock other) {
                other.refreshConnections(level.getBlockState(neighbour), level, neighbour);
            }
        }
        pipe.neighbourChanged();
        // the routers on both sides of the cut have to look again
        pipe.markLinksDirty();
        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.relative(side);
            if (level.getBlockEntity(neighbour) instanceof PipeBlockEntity other) {
                other.markLinksDirty();
            }
            PipeTopology.markRoutersDirty(level, neighbour);
        }
    }
}
