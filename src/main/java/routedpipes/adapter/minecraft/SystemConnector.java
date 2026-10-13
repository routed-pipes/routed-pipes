package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import routedpipes.adapter.minecraft.ConnectorPipeBlock.LinkState;
import routedpipes.adapter.minecraft.menu.ChannelMenu;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.ConnectorLinks;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

// what makes a pipe an inventory system connector: its channel, its resistance and the items it waits for
// nothing teleports, what one connector puts into the inventory beside it another one takes out of the same inventory,
// an ender chest for example
// a teleport pipe is the direct kind: it needs no inventory and puts the item straight into the pipe of its partner
public final class SystemConnector {

    // energy in FE for every stack taken out of the inventory, the original mod's 6 units at its rate of 2 RF per unit
    public static final int ENERGY_PER_STACK = 12;
    // with inventories that are not really shared nothing ever arrives, the list must not grow forever then
    private static final int MAX_WAITING = 512;
    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0f, 0.5f, 0.0f), 1.0f);

    // an item another connector put into the inventory for this one to send on
    private record Waiting(ItemStack stack, RouterId destination, TransportMode mode) {
    }

    private final PipeBlockEntity pipe;
    private final boolean direct;
    private final List<Waiting> waiting = new ArrayList<>();
    private UUID channel;
    private int resistance;
    // what the network was last told about this connector
    private UUID toldChannel;
    private Direction toldSide;
    private int toldResistance;
    // the last tick it still waited for something, for the panel of the glasses
    private long waitedAt = Long.MIN_VALUE / 2;

    SystemConnector(PipeBlockEntity pipe) {
        this.pipe = pipe;
        this.direct = pipe.kind() == PipeKind.TELEPORT;
    }

    // empty while the connector is on no channel
    public Optional<UUID> channel() {
        return Optional.ofNullable(channel);
    }

    public void setChannel(UUID id) {
        channel = id;
        pipe.setChanged();
    }

    public int resistance() {
        return resistance;
    }

    // how many pipes longer the way through the inventory counts than it is
    public void setResistance(int value) {
        resistance = Math.max(0, Math.min(value, ConnectorLinks.MAX_RESISTANCE));
        pipe.setChanged();
    }

    // what this connector still has to take out of its inventory, one stack per kind of item
    public List<ItemStack> expected() {
        List<ItemStack> expected = new ArrayList<>();
        for (Waiting entry : waiting) {
            Optional<ItemStack> same = expected.stream()
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, entry.stack())).findFirst();
            if (same.isPresent()) {
                same.get().grow(entry.stack().getCount());
            } else {
                expected.add(entry.stack().copy());
            }
        }
        return expected;
    }

    private PipeServices services() {
        return ((PipeBlock) pipe.getBlockState().getBlock()).services();
    }

    MenuProvider menuProvider() {
        return new SimpleMenuProvider((id, inventory, player) -> new ChannelMenu(services().connectorMenu().get(), id, inventory,
                this, null, user -> Container.stillValidBlockEntity(pipe, user)), pipe.getBlockState().getBlock().getName());
    }

    void tick() {
        tellNetwork();
        takeOut();
        if (!waiting.isEmpty()) {
            waitedAt = pipe.getLevel().getGameTime();
        }
    }

    // 0 while it waits for something
    public long ticksEmpty() {
        return waiting.isEmpty() ? pipe.getLevel().getGameTime() - waitedAt : 0;
    }

    private void tellNetwork() {
        Level level = pipe.getLevel();
        if (channel != null && ChannelRegistry.of(level).channels().find(channel).isEmpty()) {
            // somebody deleted the channel
            setChannel(null);
        }
        Direction side = channel == null ? null : linkSide();
        if (!Objects.equals(channel, toldChannel) || side != toldSide || resistance != toldResistance) {
            toldChannel = channel;
            toldSide = side;
            toldResistance = resistance;
            Side told = side == null ? null : Directions.toDomain(side);
            if (direct) {
                pipe.network().setDirectConnector(pipe.routerId(), channel, told, resistance, level.getGameTime());
            } else {
                pipe.network().setConnector(pipe.routerId(), channel, told, resistance, level.getGameTime());
            }
        }
        LinkState state = LinkState.values()[pipe.network().connectorState(pipe.routerId()).ordinal()];
        BlockState shown = pipe.getBlockState();
        if (shown.getValue(ConnectorPipeBlock.LINK) != state) {
            level.setBlock(pipe.getBlockPos(), shown.setValue(ConnectorPipeBlock.LINK, state), Block.UPDATE_CLIENTS);
        }
    }

    // the side it reaches its partners through: its first inventory, for a teleport pipe the first side with no arm
    // null when there is none
    private Direction linkSide() {
        if (!direct) {
            List<Direction> sides = pipe.inventorySides();
            return sides.isEmpty() ? null : sides.get(0);
        }
        for (Direction side : Direction.values()) {
            if (!PipeBlock.connected(pipe.getBlockState(), side)) {
                return side;
            }
        }
        return null;
    }

    // an item leaving for another connector goes into the inventory they share and that connector is told to expect it
    // from a teleport pipe it goes straight into the pipe of the other one, coming in through the free side of that
    // false for every other item
    boolean handsOver(TravellingItem item, Direction side) {
        Optional<RouterId> partner = item.nextHop().filter(hop -> item.destination().isPresent()
                && pipe.network().connectorsLinked(pipe.routerId(), Directions.toDomain(side), hop));
        if (partner.isEmpty() || !(pipe.network().node(partner.get()) instanceof PipeBlockEntity other)
                || other.connector() == null) {
            return false;
        }
        if (direct) {
            Direction entry = other.connector().toldSide;
            if (entry == null) {
                return false;
            }
            other.acceptFromTube(item.enteringFrom(Directions.toDomain(entry)));
            sparkle();
            return true;
        }
        ItemStack stack = McItemType.stackOf(item.batch());
        ItemStack rest = services().storage().insert(pipe.getLevel(), pipe.getBlockPos().relative(side), pipe.accessFace(side),
                stack, false);
        if (rest.getCount() < stack.getCount()) {
            other.connector().expect(new Waiting(stack.copyWithCount(stack.getCount() - rest.getCount()),
                    item.destination().get(), item.mode()));
            sparkle();
        }
        if (!rest.isEmpty()) {
            // the inventory is full, what is left tries again from this pipe
            pipe.insert(item.remainder(McItemType.batchOf(rest)));
        }
        return true;
    }

    private void expect(Waiting entry) {
        if (waiting.size() >= MAX_WAITING) {
            waiting.remove(0);
        }
        waiting.add(entry);
        waitedAt = pipe.getLevel().getGameTime();
        pipe.setChanged();
    }

    private static int held(List<ItemStack> contents, ItemStack wanted) {
        int held = 0;
        for (ItemStack stack : contents) {
            if (ItemStack.isSameItemSameComponents(stack, wanted)) {
                held += stack.getCount();
            }
        }
        return held;
    }

    // takes what has arrived in the inventories out again and sends it on to where it was going
    private void takeOut() {
        if (waiting.isEmpty()) {
            return;
        }
        Level level = pipe.getLevel();
        ItemStorage storage = services().storage();
        for (Direction side : pipe.inventorySides()) {
            BlockPos target = pipe.getBlockPos().relative(side);
            Direction face = pipe.accessFace(side);
            List<ItemStack> contents = storage.contents(level, target, face);
            for (Iterator<Waiting> entries = waiting.iterator(); entries.hasNext();) {
                Waiting entry = entries.next();
                // only a stack that is there as a whole is taken, it may still be on its way in
                if (held(contents, entry.stack()) < entry.stack().getCount()) {
                    continue;
                }
                if (!pipe.usePower(ENERGY_PER_STACK)) {
                    return;
                }
                ItemStack taken = storage.extract(level, target, face, entry.stack(), entry.stack().getCount());
                if (taken.isEmpty()) {
                    continue;
                }
                pipe.insert(new TravellingItem(McItemType.batchOf(taken), Directions.toDomain(side), entry.destination(),
                        entry.mode()));
                // what could not be taken out stays on the list
                entry.stack().shrink(taken.getCount());
                if (entry.stack().isEmpty()) {
                    entries.remove();
                }
                contents = storage.contents(level, target, face);
                sparkle();
            }
        }
    }

    private void sparkle() {
        if (pipe.getLevel() instanceof ServerLevel level) {
            BlockPos pos = pipe.getBlockPos();
            level.sendParticles(SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
        }
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (channel != null) {
            tag.putUUID("Channel", channel);
        }
        tag.putInt("Resistance", resistance);
        ListTag list = new ListTag();
        for (Waiting entry : waiting) {
            CompoundTag saved = new CompoundTag();
            saved.put("Stack", entry.stack().save(registries));
            saved.putUUID("Destination", entry.destination().value());
            saved.putBoolean("Ordered", entry.mode() == TransportMode.REQUESTED);
            list.add(saved);
        }
        tag.put("Waiting", list);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        channel = tag.hasUUID("Channel") ? tag.getUUID("Channel") : null;
        resistance = tag.getInt("Resistance");
        waiting.clear();
        ListTag list = tag.getList("Waiting", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag saved = list.getCompound(index);
            ItemStack.parse(registries, saved.getCompound("Stack")).filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> waiting.add(new Waiting(stack, new RouterId(saved.getUUID("Destination")),
                            saved.getBoolean("Ordered") ? TransportMode.REQUESTED : TransportMode.SORTED)));
        }
    }
}
