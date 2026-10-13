package routedpipes.adapter.neoforge.benchmark;

import routedpipes.domain.logistics.UpgradeKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.UpgradeItem;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

// a large base built in the air for measuring, used by the server and the client benchmark
//
// sorting hall: a trunk of routed pipes along z, rows of chassis pipes with one filtered chest each branching off it,
// and a row of chests with extractors at the head of the trunk that are kept full of mixed items
// workshop, optional, further down the trunk: provider chests with raw materials, crafting pipes on logistics
// crafting tables, request pipes that are sent orders, and supplier pipes whose chests keep being emptied
public final class BenchmarkBase {

    private static final int WORKSHOP_STATIONS = 8;

    // what the workshop can be asked for, how many at a time
    private static final List<ItemStack> ORDERS = List.of(
            new ItemStack(Items.OAK_PLANKS, 16), new ItemStack(Items.STICK, 16), new ItemStack(Items.CRAFTING_TABLE, 4),
            new ItemStack(Items.FURNACE, 2), new ItemStack(Items.CHEST, 4), new ItemStack(Items.COBBLESTONE, 32),
            new ItemStack(Items.OAK_LOG, 8));

    private final ServerLevel level;
    private final BlockPos origin;
    private final int rows;
    private final int columns;
    private final int inputs;
    private final boolean workshop;
    private final List<Item> items = new ArrayList<>();
    private final List<BlockPos> sinkChests = new ArrayList<>();
    private final List<BlockPos> inputChests = new ArrayList<>();
    private final List<BlockPos> providerChests = new ArrayList<>();
    private final List<BlockPos> requestPipes = new ArrayList<>();
    private final List<BlockPos> supplierChests = new ArrayList<>();
    private final List<BlockPos> pipePositions = new ArrayList<>();
    private final Map<BlockPos, Map<Item, Integer>> ordered = new HashMap<>();
    private final Random random = new Random(42);
    private BlockPos junction;
    private int nextOrder;

    long supplied;
    long delivered;
    long missorted;
    long ordersPlaced;
    long ordersRefused;
    long suppliedToSuppliers;
    // why orders were refused, and how often
    final Map<String, Integer> refusals = new HashMap<>();

    public BenchmarkBase(ServerLevel level, BlockPos origin, int rows, int columns, int inputs, boolean workshop) {
        this.level = level;
        this.origin = origin;
        this.rows = rows;
        this.columns = columns;
        this.inputs = inputs;
        this.workshop = workshop;
    }

    private BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    private int lastRowZ() {
        return 4 + 3 * (rows - 1);
    }

    private int lastZ() {
        return workshop ? lastRowZ() + 12 : lastRowZ();
    }

    public int pipes() {
        return pipePositions.size();
    }

    public int filteredChests() {
        return sinkChests.size();
    }

    // every chunk the base stands in, for keeping them loaded or letting them go
    public List<long[]> chunks() {
        List<long[]> chunks = new ArrayList<>();
        int width = Math.max(columns, workshop ? WORKSHOP_STATIONS : 0);
        for (int chunkX = (origin.getX() - 2 * inputs - 2) >> 4; chunkX <= (origin.getX() + 2 * width + 2) >> 4; chunkX++) {
            for (int chunkZ = (origin.getZ() - 1) >> 4; chunkZ <= (origin.getZ() + lastZ() + 2) >> 4; chunkZ++) {
                chunks.add(new long[] {chunkX, chunkZ});
            }
        }
        return chunks;
    }

    public void forceChunks(boolean forced, boolean farHalfOnly) {
        int middle = (origin.getZ() + lastZ() / 2) >> 4;
        for (long[] chunk : chunks()) {
            if (!farHalfOnly || chunk[1] > middle) {
                level.setChunkForced((int) chunk[0], (int) chunk[1], forced);
            }
        }
    }

    public int loadedChunks() {
        return (int) chunks().stream().filter(chunk -> level.getChunkSource().hasChunk((int) chunk[0], (int) chunk[1])).count();
    }

    private PipeBlockEntity pipe(BlockPos pos, PipeKind kind) {
        level.setBlock(pos, LogisticsPipes.pipe(kind).defaultBlockState(), 3);
        pipePositions.add(pos);
        return (PipeBlockEntity) level.getBlockEntity(pos);
    }

    private void transport(BlockPos pos) {
        level.setBlock(pos, LogisticsPipes.TRANSPORT_PIPE.get().defaultBlockState(), 3);
        pipePositions.add(pos);
    }

    private PipeBlockEntity chassis(BlockPos pos, ItemStack module) {
        level.setBlock(pos, LogisticsPipes.CHASSIS_PIPES.get(1).get().defaultBlockState(), 3);
        pipePositions.add(pos);
        PipeBlockEntity chassis = (PipeBlockEntity) level.getBlockEntity(pos);
        chassis.modules().setItem(0, module);
        return chassis;
    }

    private BlockPos chestAbove(BlockPos pipe) {
        level.setBlock(pipe.above(), Blocks.CHEST.defaultBlockState(), 3);
        return pipe.above();
    }

    // loads the chunks the base will stand in, apart from building so that their memory is not counted as the base's
    public void prepare() {
        forceChunks(true, false);
        chunks().forEach(chunk -> level.getChunk((int) chunk[0], (int) chunk[1]));
    }

    public void build() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (items.size() < rows * columns && item != Items.AIR && new ItemStack(item).getMaxStackSize() == 64) {
                items.add(item);
            }
        }
        // head of the trunk with the power and the row of input chests
        pipe(at(0, 0, 0), PipeKind.BASIC);
        junction = at(0, -1, 0);
        level.setBlock(junction, LogisticsPipes.POWER_JUNCTION.get().defaultBlockState(), 3);
        for (int input = 1; input <= inputs; input++) {
            transport(at(-2 * input + 1, 0, 0));
            PipeBlockEntity extractor = chassis(at(-2 * input, 0, 0), new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.EXTRACTOR).get()));
            extractor.moduleUpgrades().setItem(0, new ItemStack(LogisticsPipes.UPGRADES.get(UpgradeKind.ITEM_STACK_EXTRACTION).get()));
            inputChests.add(chestAbove(at(-2 * input, 0, 0)));
        }
        // the trunk, a routed pipe wherever a row branches off
        for (int z = 1; z <= lastZ(); z++) {
            if (z >= 4 && (z - 4) % 3 == 0) {
                pipe(at(0, 0, z), PipeKind.BASIC);
            } else {
                transport(at(0, 0, z));
            }
        }
        for (int row = 0; row < rows; row++) {
            int z = 4 + 3 * row;
            for (int column = 1; column <= columns; column++) {
                transport(at(2 * column - 1, 0, z));
                ItemStack sink = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get());
                ModuleItem.configureSink(sink, List.of(new ItemStack(items.get((row * columns + column - 1) % items.size()))), false);
                chassis(at(2 * column, 0, z), sink);
                sinkChests.add(chestAbove(at(2 * column, 0, z)));
            }
        }
        if (workshop) {
            buildWorkshop();
        }
    }

    private void buildWorkshop() {
        int z = lastRowZ() + 3;
        // raw materials
        for (int station = 1; station <= WORKSHOP_STATIONS; station++) {
            transport(at(2 * station - 1, 0, z));
            pipe(at(2 * station, 0, z), PipeKind.PROVIDER);
            providerChests.add(chestAbove(at(2 * station, 0, z)));
        }
        // crafting tables, every recipe on two of them
        Item[][] recipes = {
                {Items.OAK_LOG},
                {Items.OAK_PLANKS, null, null, Items.OAK_PLANKS},
                {Items.OAK_PLANKS, Items.OAK_PLANKS, null, Items.OAK_PLANKS, Items.OAK_PLANKS},
                {Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, null, Items.COBBLESTONE,
                        Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE},
        };
        for (int station = 1; station <= WORKSHOP_STATIONS; station++) {
            transport(at(2 * station - 1, 0, z + 3));
            PipeBlockEntity crafting = pipe(at(2 * station, 0, z + 3), PipeKind.CRAFTING);
            level.setBlock(at(2 * station, 1, z + 3), LogisticsPipes.CRAFTING_TABLE.get().defaultBlockState(), 3);
            CraftingTableBlockEntity table = (CraftingTableBlockEntity) level.getBlockEntity(at(2 * station, 1, z + 3));
            Item[] recipe = recipes[(station - 1) % recipes.length];
            for (int slot = 0; slot < recipe.length; slot++) {
                if (recipe[slot] != null) {
                    table.pattern().setItem(slot, new ItemStack(recipe[slot]));
                }
            }
            crafting.importRecipe();
        }
        // people ordering things
        for (int station = 1; station <= WORKSHOP_STATIONS; station++) {
            transport(at(2 * station - 1, 0, z + 6));
            pipe(at(2 * station, 0, z + 6), PipeKind.REQUEST);
            chestAbove(at(2 * station, 0, z + 6));
            requestPipes.add(at(2 * station, 0, z + 6));
            ordered.put(at(2 * station, 0, z + 6), new HashMap<>());
        }
        // machines that are kept stocked
        for (int station = 1; station <= WORKSHOP_STATIONS; station++) {
            transport(at(2 * station - 1, 0, z + 9));
            PipeBlockEntity supplier = pipe(at(2 * station, 0, z + 9), PipeKind.SUPPLIER);
            supplier.config().setItem(0, new ItemStack(Items.COAL, 64));
            supplier.config().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            supplierChests.add(chestAbove(at(2 * station, 0, z + 9)));
        }
    }

    // keeps the junction charged, the input chests full of random items that have a chest to go to, and the stock up
    public void refill(boolean feedInputs) {
        if (level.getBlockEntity(junction) instanceof PowerJunctionBlockEntity power) {
            power.receive(PowerJunctionBlockEntity.CAPACITY, false);
        }
        if (feedInputs) {
            for (BlockPos pos : inputChests) {
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container chest) {
                    for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                        if (chest.getItem(slot).isEmpty()) {
                            chest.setItem(slot, new ItemStack(items.get(random.nextInt(items.size())), 64));
                            supplied += 64;
                        }
                    }
                }
            }
        }
        Item[] raw = {Items.OAK_LOG, Items.COBBLESTONE, Items.COAL};
        for (int index = 0; index < providerChests.size(); index++) {
            if (level.isLoaded(providerChests.get(index)) && level.getBlockEntity(providerChests.get(index)) instanceof Container chest) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    chest.setItem(slot, new ItemStack(raw[index % raw.length], 64));
                }
            }
        }
    }

    // one order at a request pipe, round robin over pipes and things to order
    public void placeOrder() {
        if (requestPipes.isEmpty()) {
            return;
        }
        BlockPos pos = requestPipes.get(nextOrder % requestPipes.size());
        ItemStack wanted = ORDERS.get((nextOrder / requestPipes.size() + nextOrder) % ORDERS.size());
        nextOrder++;
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PipeBlockEntity request) {
            RequestOutcome outcome = request.request(McItemType.batchOf(wanted));
            if (outcome.started()) {
                ordersPlaced++;
                ordered.get(pos).merge(wanted.getItem(), wanted.getCount(), Integer::sum);
            } else {
                ordersRefused++;
                String reason = outcome.powered() ? wanted.getItem() + " missing " + outcome.missing() : "no power";
                refusals.merge(reason, 1, Integer::sum);
            }
        }
    }

    // empties the supplier chests, as machines using up their fuel would
    public void consumeSupplies() {
        for (BlockPos pos : supplierChests) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container chest) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    suppliedToSuppliers += chest.getItem(slot).getCount();
                    chest.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    // counts what arrived in the filtered chests, and whether it arrived in the right one, then empties them
    public void collectSorted() {
        for (int index = 0; index < sinkChests.size(); index++) {
            if (!level.isLoaded(sinkChests.get(index)) || !(level.getBlockEntity(sinkChests.get(index)) instanceof Container chest)) {
                continue;
            }
            Item expected = items.get(index % items.size());
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!stack.isEmpty()) {
                    delivered += stack.getCount();
                    if (!stack.is(expected)) {
                        missorted += stack.getCount();
                    }
                    chest.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    // takes what arrived at the request pipes off what was ordered there, so that zero is left when all has come
    public void collectOrders() {
        for (BlockPos pos : requestPipes) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos.above()) instanceof Container chest)) {
                continue;
            }
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!stack.isEmpty()) {
                    ordered.get(pos).merge(stack.getItem(), -stack.getCount(), Integer::sum);
                    chest.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    // items ordered and not arrived, or arrived without being ordered
    public long ordersOutstanding() {
        return ordered.values().stream().flatMap(items1 -> items1.values().stream()).mapToLong(Math::abs).sum();
    }

    // takes what is left in the input chests back out and says how much it was
    public long emptyInputs() {
        long returned = 0;
        for (BlockPos pos : inputChests) {
            Container chest = (Container) level.getBlockEntity(pos);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                returned += chest.getItem(slot).getCount();
                chest.setItem(slot, ItemStack.EMPTY);
            }
        }
        return returned;
    }

    public long itemsInPipes() {
        long count = 0;
        for (BlockPos pos : pipePositions) {
            if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                count += pipe.travellingItems().stream().mapToInt(item -> item.batch().count()).sum();
            }
        }
        return count;
    }

    public long craftingOrdersOpen() {
        long open = 0;
        for (BlockPos pos : pipePositions) {
            if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                open += pipe.outputOrders().owedInTotal();
            }
        }
        return open;
    }

    public long itemsDropped() {
        AABB area = new AABB(origin.getX() - 2 * inputs - 8, origin.getY() - 60, origin.getZ() - 8,
                origin.getX() + 2 * Math.max(columns, WORKSHOP_STATIONS) + 8, origin.getY() + 8, origin.getZ() + lastZ() + 8);
        return level.getEntitiesOfClass(ItemEntity.class, area).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    // a spot from which the whole base is in view, for the client
    public BlockPos overview() {
        return at(columns, Math.max(rows, columns), -Math.max(rows, columns) / 2);
    }
}
