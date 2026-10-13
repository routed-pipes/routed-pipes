package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.application.port.PowerSource;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PatternSupply;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.ProviderSettings;
import logisticspipes.domain.logistics.SneakyFaces;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

// what is next to the pipe: the inventories it works on, what is in them and how items get in and out,
// and the power junctions it draws from
final class PipeSurroundings {

    // capabilities of a neighbour can change without its block changing, so nothing about it is trusted for longer
    private static final int SURROUNDINGS_MAX_AGE = 40;

    private final PipeBlockEntity pipe;
    // the one inventory a chassis works on, null until it finds one
    private Direction pointed;
    private List<Direction> pointableCache = List.of();
    private Map<ItemType, Integer> contentsCache;
    // what the inventories hold, also what cannot be taken out, looked up at most once per tick
    private Map<ItemType, Integer> heldCache;
    private long heldCachedAt;
    // what is next to the pipe, looked up again after a neighbour changed and every couple of seconds anyway
    private List<Direction> inventorySidesCache;
    private List<BlockPos> junctionsCache;
    private long surroundingsCachedAt;
    private long contentsCachedAt;

    PipeSurroundings(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    private Level level() {
        return pipe.getLevel();
    }

    private BlockPos pos() {
        return pipe.getBlockPos();
    }

    private ItemStorage storage() {
        return pipe.services().storage();
    }

    // a block next to the pipe changed
    void neighbourChanged() {
        inventorySidesCache = null;
        junctionsCache = null;
        contentsCache = null;
    }

    // the pipe got or lost an arm
    void armsChanged() {
        inventorySidesCache = null;
        contentsCache = null;
    }

    // something went into or came out of an inventory
    void contentsChanged() {
        contentsCache = null;
    }

    private boolean surroundingsStale() {
        return level().getGameTime() - surroundingsCachedAt >= SURROUNDINGS_MAX_AGE;
    }

    // empty while the chassis has no inventory next to it
    Optional<Direction> pointed() {
        return inventorySides().isEmpty() || pipe.kind() != PipeKind.CHASSIS ? Optional.empty() : Optional.of(pointed);
    }

    // turns a chassis to the next inventory around it
    void pointAtNext() {
        if (pointed().isEmpty()) {
            return;
        }
        pointed = pointableCache.get((pointableCache.indexOf(pointed) + 1) % pointableCache.size());
        pipe.neighbourChanged();
        pipe.settingsChanged();
    }

    // as it is saved, null when the chassis never found an inventory
    Direction savedPointed() {
        return pointed;
    }

    void loadPointed(Direction saved) {
        pointed = saved;
    }

    // sides that touch an inventory
    List<Direction> inventorySides() {
        if (inventorySidesCache == null || surroundingsStale()) {
            lookAround();
        }
        return inventorySidesCache;
    }

    private void lookAround() {
        Level level = level();
        List<Direction> sides = new ArrayList<>();
        List<BlockPos> junctions = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos target = pos().relative(side);
            // never look into a chunk that is not loaded, that would load it
            if (!level.isLoaded(target)) {
                continue;
            }
            if (level.getBlockEntity(target) instanceof PowerJunctionBlockEntity) {
                junctions.add(target);
            } else if (pipe.fluidPipe() == null && LogisticsPipeBlock.connected(pipe.getBlockState(), side)
                    && !(level.getBlockState(target).getBlock() instanceof LogisticsPipeBlock)
                    && storage().exists(level, target, pipe.accessFace(side))) {
                sides.add(side);
            }
        }
        if (pipe.requestTable() != null) {
            // a request table keeps what it gets, it does not fill inventories next to it
            sides.clear();
        }
        pointableCache = sides;
        if (pipe.kind() == PipeKind.CHASSIS && !sides.isEmpty()) {
            // a chassis works on one inventory only, the first one it finds unless it was turned
            if (pointed == null || !sides.contains(pointed)) {
                pointed = sides.get(0);
                pipe.setChanged();
            }
            sides = List.of(pointed);
        }
        inventorySidesCache = sides;
        junctionsCache = junctions;
        surroundingsCachedAt = level.getGameTime();
    }

    Map<Side, InventoryRef> inventories() {
        Map<Side, InventoryRef> inventories = new EnumMap<>(Side.class);
        for (Direction side : inventorySides()) {
            inventories.put(Directions.toDomain(side), inventoryRef(pos().relative(side)));
        }
        return inventories;
    }

    // both halves of a double chest are the same inventory
    private InventoryRef inventoryRef(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            return new InventoryRef(Math.min(pos.asLong(), other.asLong()));
        }
        return new InventoryRef(pos.asLong());
    }

    // the faces of the inventory on that side an arriving item tries, in order
    // ordered items are not for a sink module, so the sneaky upgrade of one does not count for them
    List<Direction> insertFaces(Direction side, ItemType type, boolean ordered) {
        SneakyFaces faces = ordered ? pipe.pipeUpgrades().faces()
                : pipe.pipeUpgrades().faces().forModule(pipe.pipeModules().face(type));
        return faces.inserting(Directions.toDomain(side.getOpposite())).stream().map(Directions::toMinecraft).toList();
    }

    // returns what did not fit through any of the faces
    ItemStack insertThrough(BlockPos target, List<Direction> faces, ItemStack stack, boolean simulate) {
        ItemStack rest = stack;
        for (Direction face : faces) {
            if (!rest.isEmpty() && storage().exists(level(), target, face)) {
                rest = storage().insert(level(), target, face, rest, simulate);
            }
        }
        return rest;
    }

    Set<Side> inventoriesAccepting(ItemBatch batch) {
        Set<Side> sides = EnumSet.noneOf(Side.class);
        ItemStack stack = McItemType.stackOf(batch);
        for (Direction side : inventorySides()) {
            ItemStack rest = insertThrough(pos().relative(side), insertFaces(side, batch.type(), false), stack, true);
            if (rest.getCount() < stack.getCount()) {
                sides.add(Directions.toDomain(side));
            }
        }
        return sides;
    }

    int roomFor(ItemType type, int upTo) {
        ItemStack stack = McItemType.stackOf(new ItemBatch(type, upTo));
        int room = 0;
        for (Direction side : inventorySides()) {
            room += upTo - insertThrough(pos().relative(side), insertFaces(side, type, true), stack, true).getCount();
        }
        return Math.min(room, upTo);
    }

    // looked up at most once per tick, sinks that depend on it get asked for every passing item
    Map<ItemType, Integer> contents() {
        if (contentsCache == null || contentsCachedAt != level().getGameTime()) {
            Map<ItemType, Integer> contents = new LinkedHashMap<>();
            for (Direction side : inventorySides()) {
                for (ItemStack stack : storage().contents(level(), pos().relative(side), pipe.accessFace(side))) {
                    contents.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
                }
            }
            contentsCache = contents;
            contentsCachedAt = level().getGameTime();
        }
        return new LinkedHashMap<>(contentsCache);
    }

    Map<ItemType, Integer> held() {
        if (heldCache == null || heldCachedAt != level().getGameTime()) {
            Map<ItemType, Integer> held = new LinkedHashMap<>();
            for (Direction side : inventorySides()) {
                for (ItemStack stack : storage().held(level(), pos().relative(side), pipe.accessFace(side))) {
                    if (!stack.isEmpty()) {
                        held.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
                    }
                }
            }
            heldCache = held;
            heldCachedAt = level().getGameTime();
        }
        return new LinkedHashMap<>(heldCache);
    }

    int send(ItemType type, int count, RouterId destination, TransportMode mode) {
        ItemStack match = McItemType.stackOf(new ItemBatch(type, 1));
        int remaining = count;
        for (Direction side : inventorySides()) {
            while (remaining > 0) {
                ItemStack taken = storage().extract(level(), pos().relative(side), pipe.accessFace(side), match,
                        Math.min(remaining, match.getMaxStackSize()));
                if (taken.isEmpty()) {
                    break;
                }
                pipe.transport().insert(new TravellingItem(McItemType.batchOf(taken), Directions.toDomain(side), destination,
                        destination == null ? TransportMode.UNCLAIMED : mode));
                remaining -= taken.getCount();
            }
        }
        if (remaining < count) {
            contentsCache = null;
            pipe.setChanged();
        }
        return count - remaining;
    }

    private static List<ItemBatch> batchesOf(List<ItemStack> slots) {
        List<ItemBatch> batches = new ArrayList<>();
        for (ItemStack stack : slots) {
            batches.add(stack.isEmpty() ? null : McItemType.batchOf(stack));
        }
        return batches;
    }

    Map<ItemType, Integer> provided() {
        ProviderSettings settings = pipe.providerSettings();
        Map<ItemType, Integer> stock = new LinkedHashMap<>();
        for (Direction side : inventorySides()) {
            List<ItemStack> slots = storage().slots(level(), pos().relative(side), pipe.accessFace(side));
            settings.stock(batchesOf(slots)).forEach((type, count) -> stock.merge(type, count, Integer::sum));
        }
        return stock;
    }

    int provide(ItemType type, int count, RouterId destination) {
        ProviderSettings settings = pipe.providerSettings();
        int remaining = count;
        for (Direction side : inventorySides()) {
            BlockPos target = pos().relative(side);
            List<ItemBatch> slots = batchesOf(storage().slots(level(), target, pipe.accessFace(side)));
            int[] takeable = settings.takeable(slots);
            for (int slot = 0; slot < takeable.length && remaining > 0; slot++) {
                if (takeable[slot] == 0 || !slots.get(slot).type().equals(type)) {
                    continue;
                }
                // bins and drawers hand out one stack per call, so a slot is asked until it has given its share
                int allowed = takeable[slot];
                while (allowed > 0 && remaining > 0) {
                    ItemStack taken = storage().extractSlot(level(), target, pipe.accessFace(side), slot, Math.min(allowed, remaining));
                    if (taken.isEmpty()) {
                        break;
                    }
                    pipe.transport().insert(new TravellingItem(McItemType.batchOf(taken), Directions.toDomain(side), destination));
                    allowed -= taken.getCount();
                    remaining -= taken.getCount();
                }
            }
        }
        if (remaining < count) {
            contentsCache = null;
            pipe.setChanged();
        }
        return count - remaining;
    }

    // a pattern is about the first inventory of the pipe
    List<ItemBatch> slots() {
        List<Direction> sides = inventorySides();
        return sides.isEmpty() ? List.of()
                : batchesOf(storage().held(level(), pos().relative(sides.get(0)), pipe.accessFace(sides.get(0))));
    }

    // puts what a pattern ordered into the slots it is meant for, returns what is left over
    ItemStack fillPattern(List<PatternSupply> patterns, BlockPos target, Direction face, ItemStack stack) {
        ItemStack rest = stack;
        for (PatternSupply pattern : patterns) {
            if (rest.isEmpty()) {
                break;
            }
            List<ItemBatch> slots = batchesOf(storage().held(level(), target, face));
            for (Map.Entry<Integer, Integer> placed : pattern.placement(McItemType.batchOf(rest), slots).entrySet()) {
                ItemStack refused = storage().insertSlot(level(), target, face, placed.getKey(), rest.copyWithCount(placed.getValue()), false);
                rest = rest.copyWithCount(rest.getCount() - placed.getValue() + refused.getCount());
            }
        }
        return rest;
    }

    List<PowerSource> powerSources() {
        if (junctionsCache == null || surroundingsStale()) {
            lookAround();
        }
        if (junctionsCache.isEmpty()) {
            return List.of();
        }
        List<PowerSource> sources = new ArrayList<>();
        for (BlockPos target : junctionsCache) {
            long key = target.asLong();
            sources.add(new PowerSource() {
                @Override
                public Object key() {
                    return key;
                }

                @Override
                public int draw(int amount, boolean simulate) {
                    return level().getBlockEntity(target) instanceof PowerJunctionBlockEntity junction
                            ? junction.draw(amount, simulate) : 0;
                }
            });
        }
        return sources;
    }

    int drawPower(int amount, boolean simulate) {
        if (junctionsCache == null || surroundingsStale()) {
            lookAround();
        }
        int drawn = 0;
        for (BlockPos target : junctionsCache) {
            if (drawn < amount && level().getBlockEntity(target) instanceof PowerJunctionBlockEntity junction) {
                drawn += junction.draw(amount - drawn, simulate);
            }
        }
        return drawn;
    }
}
