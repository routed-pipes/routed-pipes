package logisticspipes.adapter.neoforge.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.mojang.logging.LogUtils;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.minecraft.UpgradeItem;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

// dev only: with -Dlogisticspipes.benchmark=<file> a server builds a large sorting base in the air, measures how long
// ticks take with the base idle and with items pouring through it, writes the numbers to the file and stops
//
// the base: a trunk of routed pipes, rows of chassis pipes with one filtered chest each branching off it,
// and a row of chests with extractors at the head of the trunk that are kept full of mixed items
public final class Benchmark {

    public static final String PROPERTY = "logisticspipes.benchmark";
    // rows x chests per row x input chests, for example 20x20x4
    public static final String SIZE_PROPERTY = "logisticspipes.benchmark.size";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int Y = 100;
    private static final int SETTLE_TICKS = 100;
    private static final int IDLE_TICKS = 200;
    private static final int LOAD_TICKS = 1200;
    private static final int EXTRACTOR_SPEED_UPGRADES = 2;

    private final Path output;
    private final int rows;
    private final int columns;
    private final int inputs;
    private final List<Item> items = new ArrayList<>();
    private final List<BlockPos> sinkChests = new ArrayList<>();
    private final List<BlockPos> inputChests = new ArrayList<>();
    private final Random random = new Random(42);
    private final long[] idle = new long[IDLE_TICKS];
    private final long[] load = new long[LOAD_TICKS];
    private int tick;
    private long tickStarted;
    private long delivered;
    private long missorted;
    private int pipes;

    private Benchmark(Path output, int rows, int columns, int inputs) {
        this.output = output;
        this.rows = rows;
        this.columns = columns;
        this.inputs = inputs;
    }

    public static void registerIfRequested() {
        String file = System.getProperty(PROPERTY);
        if (file == null) {
            return;
        }
        int[] size = Arrays.stream(System.getProperty(SIZE_PROPERTY, "20x20x4").split("x")).mapToInt(Integer::parseInt).toArray();
        Benchmark benchmark = new Benchmark(Path.of(file), size[0], size[1], size[2]);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> benchmark.tickStarted = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> benchmark.afterTick(event.getServer()));
    }

    private void afterTick(MinecraftServer server) {
        long took = System.nanoTime() - tickStarted;
        ServerLevel level = server.overworld();
        tick++;
        int idleStart = 20 + SETTLE_TICKS;
        int loadStart = idleStart + IDLE_TICKS;
        if (tick == 20) {
            build(level);
        } else if (tick > idleStart && tick <= loadStart) {
            idle[tick - idleStart - 1] = took;
        } else if (tick > loadStart && tick <= loadStart + LOAD_TICKS) {
            load[tick - loadStart - 1] = took;
            if (tick % 20 == 0) {
                refill(level);
            }
            if (tick % 200 == 0) {
                collect(level);
            }
        } else if (tick == loadStart + LOAD_TICKS + 1) {
            collect(level);
            report(level);
            server.halt(false);
        }
    }

    private static PipeBlockEntity pipe(ServerLevel level, BlockPos pos, PipeKind kind) {
        level.setBlock(pos, LogisticsPipes.pipe(kind).defaultBlockState(), 3);
        return (PipeBlockEntity) level.getBlockEntity(pos);
    }

    private void transport(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, LogisticsPipes.TRANSPORT_PIPE.get().defaultBlockState(), 3);
        pipes++;
    }

    private static PipeBlockEntity chassis(ServerLevel level, BlockPos pos, ItemStack module) {
        level.setBlock(pos, LogisticsPipes.CHASSIS_PIPES.get(1).get().defaultBlockState(), 3);
        PipeBlockEntity chassis = (PipeBlockEntity) level.getBlockEntity(pos);
        chassis.modules().setItem(0, module);
        return chassis;
    }

    private void build(ServerLevel level) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (items.size() < rows * columns && item != Items.AIR && new ItemStack(item).getMaxStackSize() == 64) {
                items.add(item);
            }
        }
        int lastRow = 4 + 3 * (rows - 1);
        for (int chunkX = (-2 * inputs - 2) >> 4; chunkX <= (2 * columns + 2) >> 4; chunkX++) {
            for (int chunkZ = -1; chunkZ <= (lastRow + 2) >> 4; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true);
                level.getChunk(chunkX, chunkZ);
            }
        }
        // head of the trunk with the power and the row of input chests
        pipe(level, new BlockPos(0, Y, 0), PipeKind.BASIC);
        pipes++;
        level.setBlock(new BlockPos(0, Y - 1, 0), LogisticsPipes.POWER_JUNCTION.get().defaultBlockState(), 3);
        for (int input = 1; input <= inputs; input++) {
            transport(level, new BlockPos(-2 * input + 1, Y, 0));
            PipeBlockEntity extractor = chassis(level, new BlockPos(-2 * input, Y, 0),
                    new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.EXTRACTOR).get()));
            ItemStack upgrades = new ItemStack(LogisticsPipes.UPGRADES.get(UpgradeItem.Kind.SPEED).get(), EXTRACTOR_SPEED_UPGRADES);
            for (int upgrade = 0; upgrade < EXTRACTOR_SPEED_UPGRADES; upgrade++) {
                extractor.installUpgrade(upgrades);
            }
            pipes++;
            BlockPos chest = new BlockPos(-2 * input, Y + 1, 0);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            inputChests.add(chest);
        }
        // the trunk and one row of filtered chests per junction
        for (int z = 1; z <= lastRow; z++) {
            if ((z - 4) % 3 == 0 && z >= 4) {
                pipe(level, new BlockPos(0, Y, z), PipeKind.BASIC);
                pipes++;
            } else {
                transport(level, new BlockPos(0, Y, z));
            }
        }
        for (int row = 0; row < rows; row++) {
            int z = 4 + 3 * row;
            for (int column = 1; column <= columns; column++) {
                transport(level, new BlockPos(2 * column - 1, Y, z));
                ItemStack sink = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get());
                ModuleItem.configureSink(sink, List.of(new ItemStack(items.get((row * columns + column - 1) % items.size()))), false);
                chassis(level, new BlockPos(2 * column, Y, z), sink);
                pipes++;
                BlockPos chest = new BlockPos(2 * column, Y + 1, z);
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
                sinkChests.add(chest);
            }
        }
        LOGGER.info("benchmark base built: {} filtered chests, {} input chests, {} pipes", sinkChests.size(), inputChests.size(), pipes);
    }

    // keeps the junction charged and the input chests full of random items that have a chest to go to
    private void refill(ServerLevel level) {
        if (level.getBlockEntity(new BlockPos(0, Y - 1, 0)) instanceof PowerJunctionBlockEntity junction) {
            junction.receive(PowerJunctionBlockEntity.CAPACITY, false);
        }
        for (BlockPos pos : inputChests) {
            Container chest = (Container) level.getBlockEntity(pos);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).isEmpty()) {
                    chest.setItem(slot, new ItemStack(items.get(random.nextInt(items.size())), 64));
                }
            }
        }
    }

    // counts what arrived, and whether it arrived in the right chest, then empties the chests
    private void collect(ServerLevel level) {
        for (int index = 0; index < sinkChests.size(); index++) {
            Container chest = (Container) level.getBlockEntity(sinkChests.get(index));
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

    private static String summary(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double average = Arrays.stream(sorted).average().orElse(0) / 1e6;
        long slow = Arrays.stream(sorted).filter(nanos1 -> nanos1 > 50_000_000L).count();
        return String.format("avg %.2f ms, p95 %.2f ms, max %.2f ms, %d of %d ticks over 50 ms", average,
                sorted[(int) (sorted.length * 0.95)] / 1e6, sorted[sorted.length - 1] / 1e6, slow, sorted.length);
    }

    private void report(ServerLevel level) {
        int dropped = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(-2 * inputs - 4, Y - 40, -4, 2 * columns + 4, Y + 8, 4 + 3 * rows + 4)).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum();
        String report = String.join("\n",
                "size: " + rows + " rows x " + columns + " chests, " + inputs + " inputs, " + pipes + " pipes",
                "tick time idle: " + summary(idle),
                "tick time under load: " + summary(load),
                String.format("items sorted per second: %.0f", delivered / (LOAD_TICKS / 20.0)),
                "items in the wrong chest: " + missorted,
                "items dropped: " + dropped,
                "");
        LOGGER.info("benchmark result\n{}", report);
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, report);
        } catch (IOException e) {
            LOGGER.error("could not write the benchmark result", e);
        }
    }
}
