package routedpipes.adapter.neoforge.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

// dev only: with -Droutedpipes.benchmark=<file> a server builds a large base, see BenchmarkBase, measures how long
// ticks take with the base idle and with items pouring through it, checks that every item is accounted for,
// writes the numbers to the file and stops
//
// scenarios: sorting is the sorting hall alone, mixed adds the workshop with orders, crafting and suppliers,
// chunks is the sorting hall with the far half of its chunks unloading and loading again all the time
public final class Benchmark {

    public static final String PROPERTY = "routedpipes.benchmark";
    // rows x chests per row x input chests, for example 20x20x4
    public static final String SIZE_PROPERTY = "routedpipes.benchmark.size";
    public static final String SCENARIO_PROPERTY = "routedpipes.benchmark.scenario";
    private static final Logger LOGGER = LogUtils.getLogger();

    // far from the spawn chunks, which never unload
    private static final BlockPos ORIGIN = new BlockPos(2000, 100, 2000);
    private static final int BUILD_AT = 20;
    private static final int SETTLE_TICKS = 100;
    private static final int IDLE_TICKS = 200;
    private static final int LOAD_TICKS = 1200;
    private static final int DRAIN_TICKS = 900;
    private static final int CHUNK_CYCLE = 200;

    private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();

    private final Path output;
    private final int[] size;
    private final String scenario;
    private final long[] idle = new long[IDLE_TICKS];
    private final long[] load = new long[LOAD_TICKS];
    private final List<String> notes = new ArrayList<>();
    private BenchmarkBase base;
    private int tick;
    private long tickStarted;
    // processor time the server thread used, which other programs on the machine do not stretch like they do the clock
    private long cpuStarted;
    private long cpuUnderLoad;
    private long heapEmpty;
    private long heapBuilt;
    private long returned;
    private int fewestChunksLoaded = Integer.MAX_VALUE;
    private int chunkCycles;

    private Benchmark(Path output, int[] size, String scenario) {
        this.output = output;
        this.size = size;
        this.scenario = scenario;
    }

    public static void registerIfRequested() {
        String file = System.getProperty(PROPERTY);
        if (file == null) {
            return;
        }
        int[] size = Arrays.stream(System.getProperty(SIZE_PROPERTY, "20x20x4").split("x")).mapToInt(Integer::parseInt).toArray();
        Benchmark benchmark = new Benchmark(Path.of(file), size, System.getProperty(SCENARIO_PROPERTY, "sorting"));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> {
            benchmark.tickStarted = System.nanoTime();
            benchmark.cpuStarted = THREADS.getCurrentThreadCpuTime();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> benchmark.afterTick(event.getServer()));
    }

    private static long usedHeap() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private void afterTick(MinecraftServer server) {
        long took = System.nanoTime() - tickStarted;
        long cpu = THREADS.getCurrentThreadCpuTime() - cpuStarted;
        tick++;
        int idleStart = BUILD_AT + SETTLE_TICKS;
        int loadStart = idleStart + IDLE_TICKS;
        int drainStart = loadStart + LOAD_TICKS;
        if (tick == BUILD_AT - 10) {
            base = new BenchmarkBase(server.overworld(), ORIGIN, size[0], size[1], size[2], scenario.equals("mixed"));
            base.prepare();
        } else if (tick == BUILD_AT) {
            heapEmpty = usedHeap();
            base.build();
            LOGGER.info("benchmark base built: {} filtered chests, {} pipes", base.filteredChests(), base.pipes());
        } else if (tick == idleStart) {
            heapBuilt = usedHeap();
        } else if (tick > idleStart && tick <= loadStart) {
            idle[tick - idleStart - 1] = took;
        } else if (tick > loadStart && tick <= drainStart) {
            load[tick - loadStart - 1] = took;
            cpuUnderLoad += cpu;
            underLoad(tick - loadStart);
        } else if (tick == drainStart + 1) {
            // everything is loaded again, nothing new comes in, and what is under way gets time to arrive
            base.forceChunks(true, false);
            returned = base.emptyInputs();
        } else if (tick > drainStart && tick < drainStart + DRAIN_TICKS) {
            if (tick % 20 == 0) {
                base.refill(false);
                base.collectSorted();
                base.collectOrders();
            }
        } else if (tick == drainStart + DRAIN_TICKS) {
            base.collectSorted();
            base.collectOrders();
            report();
            server.halt(false);
        }
    }

    private void underLoad(int sinceStart) {
        if (sinceStart % 20 == 0) {
            base.refill(true);
        }
        if (sinceStart % 200 == 0) {
            base.collectSorted();
            base.collectOrders();
        }
        if (scenario.equals("mixed")) {
            if (sinceStart % 5 == 0) {
                base.placeOrder();
            }
            if (sinceStart % 100 == 0) {
                base.consumeSupplies();
            }
        }
        if (scenario.equals("chunks")) {
            // the far half of the base goes away for half of every cycle
            if (sinceStart % CHUNK_CYCLE == 0) {
                base.forceChunks(false, true);
                chunkCycles++;
            } else if (sinceStart % CHUNK_CYCLE == CHUNK_CYCLE / 2) {
                fewestChunksLoaded = Math.min(fewestChunksLoaded, base.loadedChunks());
                base.forceChunks(true, true);
            }
        }
    }

    private static String summary(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double average = Arrays.stream(sorted).average().orElse(0) / 1e6;
        long slow = Arrays.stream(sorted).filter(time -> time > 50_000_000L).count();
        return String.format("avg %.2f ms, p95 %.2f ms, max %.2f ms, %d of %d ticks over 50 ms", average,
                sorted[(int) (sorted.length * 0.95)] / 1e6, sorted[sorted.length - 1] / 1e6, slow, sorted.length);
    }

    private void report() {
        long inPipes = base.itemsInPipes();
        long dropped = base.itemsDropped();
        long unaccounted = base.supplied - base.delivered - returned - inPipes - dropped;
        List<String> lines = new ArrayList<>(List.of(
                "scenario: " + scenario + ", " + size[0] + " rows x " + size[1] + " chests, " + size[2] + " inputs, "
                        + base.pipes() + " pipes",
                "tick time idle: " + summary(idle),
                "tick time under load: " + summary(load),
                String.format("processor time per tick under load: %.2f ms", cpuUnderLoad / 1e6 / LOAD_TICKS),
                String.format("items sorted per second: %.0f", base.delivered / (LOAD_TICKS / 20.0)),
                "items in the wrong chest: " + base.missorted,
                "items dropped: " + dropped,
                "items still in pipes after draining: " + inPipes,
                "items unaccounted for: " + unaccounted + " (of " + base.supplied + " fed in)",
                String.format("heap: %.0f MB empty, %.0f MB with the base, %.1f kB per pipe", heapEmpty / 1e6, heapBuilt / 1e6,
                        (heapBuilt - heapEmpty) / 1e3 / Math.max(1, base.pipes())),
                String.format("heap at the end: %.0f MB", usedHeap() / 1e6)));
        if (scenario.equals("mixed")) {
            lines.add("orders placed: " + base.ordersPlaced + ", refused: " + base.ordersRefused);
            base.refusals.forEach((reason, count) -> lines.add("  refused " + count + " times: " + reason));
            lines.add("ordered items not delivered, or delivered without an order: " + base.ordersOutstanding());
            lines.add("crafting orders still open: " + base.craftingOrdersOpen());
            lines.add("items delivered to suppliers: " + base.suppliedToSuppliers);
        }
        if (scenario.equals("chunks")) {
            lines.add("chunk cycles: " + chunkCycles + ", fewest of " + base.chunks().size() + " chunks loaded: " + fewestChunksLoaded);
        }
        lines.addAll(notes);
        String report = String.join("\n", lines) + "\n";
        LOGGER.info("benchmark result\n{}", report);
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, report);
        } catch (IOException e) {
            LOGGER.error("could not write the benchmark result", e);
        }
    }
}
