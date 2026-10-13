package routedpipes.adapter.neoforge.visualtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.logging.LogUtils;
import routedpipes.adapter.neoforge.benchmark.BenchmarkBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

// dev only: with -Droutedpipes.clientBenchmark=<file> the client makes a flat world, looks down at the place
// where the base of the server benchmark will stand, and counts frames per second before the base is built,
// with the base standing idle and with items flowing through it
public final class ClientBenchmark {

    public static final String PROPERTY = "routedpipes.clientBenchmark";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "client_benchmark";
    private static final BlockPos ORIGIN = new BlockPos(0, 100, 0);
    private static final int WARM_UP = 100;
    private static final int MEASURE = 200;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output;
    private final int[] size;
    private final List<String> lines = new ArrayList<>();
    private BenchmarkBase base;
    private boolean worldRequested;
    private int tick = -1;
    private int frames;
    private long windowStarted;

    private ClientBenchmark(Path output, int[] size) {
        this.output = output;
        this.size = size;
    }

    public static void registerIfRequested() {
        String file = System.getProperty(PROPERTY);
        if (file == null) {
            return;
        }
        int[] size = Arrays.stream(System.getProperty("routedpipes.benchmark.size", "20x20x4").split("x")).mapToInt(Integer::parseInt).toArray();
        ClientBenchmark benchmark = new ClientBenchmark(Path.of(file), size);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> benchmark.tick());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> benchmark.frames++);
    }

    private void tick() {
        try {
            advance();
        } catch (RuntimeException | IOException e) {
            LOGGER.error("client benchmark failed", e);
            minecraft.stop();
        }
    }

    private void advance() throws IOException {
        if (!worldRequested) {
            if (minecraft.getOverlay() == null && minecraft.screen != null && minecraft.level == null) {
                worldRequested = true;
                createWorld();
            }
            return;
        }
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        tick++;
        int emptyEnd = WARM_UP + MEASURE;
        int idleStart = emptyEnd + WARM_UP * 2;
        int idleEnd = idleStart + MEASURE;
        int loadStart = idleEnd + WARM_UP;
        int loadEnd = loadStart + MEASURE;
        if (tick == 0) {
            minecraft.options.hideGui = true;
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.framerateLimit().set(260);
            minecraft.options.enableVsync().set(false);
            minecraft.options.renderDistance().set(12);
            onServer(server -> {
                base = new BenchmarkBase(server.overworld(), ORIGIN, size[0], size[1], size[2], false);
                base.prepare();
                BlockPos eye = base.overview();
                command(server, "gamerule doDaylightCycle false");
                command(server, "time set noon");
                // a spectator stays where it is put, anyone else would fall to the ground
                command(server, "gamemode spectator @p");
                command(server, "tp @p " + eye.getX() + " " + eye.getY() + " " + eye.getZ() + " 0 40");
            });
        } else if (tick == WARM_UP) {
            startWindow();
        } else if (tick == emptyEnd) {
            lines.add("frames per second, nothing built: " + endWindow());
            onServer(server -> base.build());
        } else if (tick == idleStart) {
            startWindow();
        } else if (tick == idleEnd) {
            lines.add("frames per second, base standing idle: " + endWindow());
        } else if (tick > idleEnd && tick <= loadEnd) {
            if (tick % 20 == 0) {
                onServer(server -> {
                    base.refill(true);
                    base.collectSorted();
                });
            }
            if (tick == loadStart) {
                startWindow();
            } else if (tick == loadStart + MEASURE / 2) {
                // proof of what was on screen while measuring
                Screenshot.grab(output.toAbsolutePath().getParent().toFile(), "client-view.png", minecraft.getMainRenderTarget(), message -> { });
            } else if (tick == loadEnd) {
                lines.add("frames per second, items flowing: " + endWindow());
                report();
                minecraft.stop();
            }
        }
    }

    private void startWindow() {
        frames = 0;
        windowStarted = System.nanoTime();
    }

    private String endWindow() {
        double seconds = (System.nanoTime() - windowStarted) / 1e9;
        return String.format("%.0f", frames / seconds);
    }

    private void report() throws IOException {
        lines.add(0, "size: " + size[0] + " rows x " + size[1] + " chests, " + size[2] + " inputs, " + base.pipes() + " pipes, window "
                + minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight());
        lines.add(1, "renderer: " + GlUtil.getRenderer());
        String report = String.join("\n", lines) + "\n";
        LOGGER.info("client benchmark result\n{}", report);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report);
    }

    private void createWorld() throws IOException {
        Path saved = minecraft.getLevelSource().getBaseDir().resolve(WORLD);
        if (Files.exists(saved)) {
            try (Stream<Path> files = Files.walk(saved)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                minecraft.screen);
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private void onServer(java.util.function.Consumer<MinecraftServer> action) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        server.execute(() -> action.accept(server));
    }
}
