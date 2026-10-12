package logisticspipes.adapter.neoforge.visualtest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.mojang.logging.LogUtils;
import logisticspipes.adapter.minecraft.CraftingTableBlockEntity;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.minecraft.client.RecipeViewerCheck;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.ModuleKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

// dev only: with -Dlogisticspipes.visualTest=<dir> the client builds a pipe scene by itself,
// saves screenshots into that directory and quits, see scripts/visual-test.sh
public final class VisualTest {

    public static final String PROPERTY = "logisticspipes.visualTest";
    // second run on the world the first run left behind, checks that what was under way still arrives
    public static final String RESUME_PROPERTY = "logisticspipes.visualTest.resume";
    private static final int RESUME_TIMEOUT = 1500;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "visual_test";
    // a requested item crosses the nine pipes of the scene in about fifteen ticks
    private static final int FLICKER_FRAMES = 12;

    private static final BlockPos REQUEST = new BlockPos(0, -59, 2);
    private static final BlockPos SUPPLIER = new BlockPos(2, -59, 6);
    private static final BlockPos CRAFTING = new BlockPos(0, -59, 13);
    private static final BlockPos SATELLITE = new BlockPos(0, -57, 13);
    private static final BlockPos CHASSIS = new BlockPos(0, -59, 0);
    private static final BlockPos TABLE = new BlockPos(0, -58, 17);
    private static final BlockPos TABLE_PIPE = new BlockPos(0, -59, 17);

    private record Step(int waitAfter, Runnable action) {
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final File output;
    private final Deque<Step> steps = new ArrayDeque<>();
    private boolean worldRequested;
    private boolean scripted;
    private volatile boolean resumeChecked;
    private int wait;

    private VisualTest(File output) {
        this.output = output;
    }

    public static void registerIfRequested() {
        String directory = System.getProperty(PROPERTY);
        if (directory != null) {
            VisualTest test = new VisualTest(new File(directory));
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> test.tick());
        }
    }

    private void tick() {
        try {
            advance();
        } catch (RuntimeException | IOException e) {
            LOGGER.error("visual test failed", e);
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
        if (!scripted) {
            if (minecraft.player != null && minecraft.screen == null) {
                scripted = true;
                script();
                wait = 60;
            }
            return;
        }
        if (wait-- > 0) {
            return;
        }
        Step step = steps.poll();
        if (step == null) {
            LOGGER.info("visual test finished");
            minecraft.stop();
            return;
        }
        step.action().run();
        wait = step.waitAfter();
    }

    private void createWorld() throws IOException {
        minecraft.options.pauseOnLostFocus = false;
        if (Boolean.getBoolean(RESUME_PROPERTY)) {
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> {
                LOGGER.error("restart check failed: the world could not be opened again");
                minecraft.stop();
            });
            return;
        }
        deleteRecursively(output.toPath());
        Files.createDirectories(output.toPath());
        deleteRecursively(minecraft.getLevelSource().getBaseDir().resolve(WORLD));
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                minecraft.screen);
    }

    // after the restart the chest at the request pipe has to fill up with what was ordered before quitting
    private void resumeScript() {
        for (int waited = 0; waited < RESUME_TIMEOUT; waited += 20) {
            boolean last = waited + 20 >= RESUME_TIMEOUT;
            step(20, () -> onServer(server -> {
                if (resumeChecked) {
                    return;
                }
                Container chest = (Container) server.overworld().getBlockEntity(REQUEST.above());
                int cobblestone = chest.countItem(Items.COBBLESTONE);
                int ingots = chest.countItem(Items.IRON_INGOT);
                if (cobblestone == 33 && ingots == 2) {
                    resumeChecked = true;
                    LOGGER.info("restart check passed");
                    minecraft.execute(steps::clear);
                } else if (last) {
                    LOGGER.error("restart check failed: chest holds {} cobblestone and {} iron ingots, expected 33 and 2",
                            cobblestone, ingots);
                }
            }));
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    private void script() {
        if (Boolean.getBoolean(RESUME_PROPERTY)) {
            resumeScript();
            return;
        }
        // request - transport - basic - transport - provider in a row, a supplier on a side branch,
        // and a furnace with crafting pipe below and satellite on top
        step(0, () -> {
            minecraft.options.hideGui = true;
            command("gamerule doDaylightCycle false");
            command("gamerule doWeatherCycle false");
            command("time set noon");
            command("weather clear");
            command("setblock 0 -59 2 logisticspipes:request_pipe");
            command("fill 0 -59 3 0 -59 5 logisticspipes:transport_pipe");
            command("setblock 0 -59 6 logisticspipes:basic_logistics_pipe");
            command("fill 0 -59 7 0 -59 10 logisticspipes:transport_pipe");
            command("setblock 0 -59 11 logisticspipes:provider_pipe");
            command("setblock 0 -58 11 chest{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:64},"
                    + "{Slot:1b,id:\"minecraft:diamond\",count:12},{Slot:2b,id:\"minecraft:raw_iron\",count:32},"
                    + "{Slot:3b,id:\"minecraft:coal\",count:64}]}");
            command("setblock 1 -59 6 logisticspipes:transport_pipe");
            command("setblock 2 -59 6 logisticspipes:supplier_pipe");
            command("setblock 2 -58 6 chest");
            command("setblock 0 -59 12 logisticspipes:transport_pipe");
            command("setblock 0 -59 13 logisticspipes:crafting_pipe");
            command("setblock 0 -58 13 furnace{Items:[{Slot:1b,id:\"minecraft:coal\",count:8}]}");
            command("setblock 0 -58 2 chest");
            command("setblock 0 -57 13 logisticspipes:satellite_pipe");
            command("fill 1 -59 13 1 -57 13 logisticspipes:transport_pipe");
            command("fill 0 -59 14 0 -59 16 logisticspipes:transport_pipe");
            command("setblock 0 -59 17 logisticspipes:crafting_pipe");
            command("setblock 0 -58 17 logisticspipes:logistics_crafting_table");
            command("setblock 1 -59 2 logisticspipes:power_junction");
            command("setblock 0 -59 1 logisticspipes:transport_pipe");
            command("setblock 0 -59 21 logisticspipes:logistics_crafting_table");
            command("setblock 0 -59 23 logisticspipes:crafting_pipe");
            command("setblock 0 -59 0 logisticspipes:chassis_pipe_mk5");
            command("setblock 0 -58 0 chest");
        });
        step(20, () -> onServer(server -> {
            PipeBlockEntity crafting = pipe(server, CRAFTING);
            crafting.config().setItem(PipeBlockEntity.SATELLITE_ROW, new ItemStack(Items.RAW_IRON, 1));
            crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT, 1));
            crafting.setSatelliteId(3);
            pipe(server, SATELLITE).setSatelliteId(3);
            CraftingTableBlockEntity table = (CraftingTableBlockEntity) server.overworld().getBlockEntity(TABLE);
            table.pattern().setItem(0, new ItemStack(Items.OAK_PLANKS));
            table.pattern().setItem(3, new ItemStack(Items.OAK_PLANKS));
            table.storage().setItem(0, new ItemStack(Items.OAK_PLANKS, 20));
            pipe(server, TABLE_PIPE).importRecipe();
            ((PowerJunctionBlockEntity) server.overworld().getBlockEntity(new BlockPos(1, -59, 2)))
                    .receive(PowerJunctionBlockEntity.CAPACITY, false);
            PipeBlockEntity chassis = pipe(server, CHASSIS);
            chassis.modules().setItem(0, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get()));
            chassis.modules().setItem(1, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.EXTRACTOR).get()));
            chassis.modules().setItem(2, new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.PROVIDER).get()));
        }));

        shotFrom("pipes_overview", "7.5 -58 7.5 90 12");
        shotFrom("pipes_close", "2.5 -60 4.5 90 0");
        shotFrom("pipes_junction", "3.5 -58.5 8.5 120 15");
        shotFrom("pipes_vertical", "4.5 -59.5 13.5 90 -5");
        shotFrom("crafting_table", "3.5 -59 18.5 110 15");
        shotFrom("chassis", "2.5 -59.5 0.5 90 5");

        // one item, one tick per frame, it has to be visible in every frame while it crosses pipe borders
        step(10, () -> command("tp @p 3.5 -60 8.5 90 0"));
        step(10, () -> command("tick freeze"));
        step(2, () -> shot("flicker_base"));
        step(2, () -> onServer(server -> pipe(server, REQUEST)
                .request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 1)))));
        for (int frame = 0; frame < FLICKER_FRAMES; frame++) {
            String name = String.format("flicker_%02d", frame);
            step(4, () -> command("tick step 1"));
            step(1, () -> shot(name));
        }
        step(40, () -> command("tick unfreeze"));

        step(0, () -> minecraft.options.hideGui = false);
        // with a block info mod installed this shows its tooltip for a pipe
        shotFrom("hud_looking_at_pipe", "2.5 -60 0.5 90 0");
        screenShot("gui_request", REQUEST, "2.5 -60 2.5 90 0");
        // the same screen again with a search typed in
        step(10, () -> onServer(server -> pipe(server, REQUEST).openRequestScreen(server.getPlayerList().getPlayers().get(0), true)));
        step(10, () -> {
            if (minecraft.screen != null) {
                minecraft.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .findFirst().ifPresent(box -> box.setValue("dia"));
            }
        });
        step(2, () -> shot("gui_request_search"));
        step(5, () -> onServer(server -> server.getPlayerList().getPlayers().get(0).closeContainer()));
        screenShot("gui_supplier", SUPPLIER, "4.5 -60 6.5 90 0");
        screenShot("gui_basic", new BlockPos(0, -59, 6), "2.5 -60 6.5 90 0");
        screenShot("gui_crafting", CRAFTING, "2.5 -60 12.5 90 0");
        screenShot("gui_satellite", SATELLITE, "2.5 -60 12.5 90 0");
        screenShot("gui_chassis", CHASSIS, "2.5 -60 0.5 90 0");
        step(5, () -> command("item replace entity @p weapon.mainhand with logisticspipes:module_item_sink"));
        step(15, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ItemStack module = player.getMainHandItem();
            ModuleItem.configureSink(module, java.util.List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.COAL)), true);
            module.use(server.overworld(), player, InteractionHand.MAIN_HAND);
        }));
        step(2, () -> shot("gui_module_item_sink"));
        step(5, () -> onServer(server -> server.getPlayerList().getPlayers().get(0).closeContainer()));
        screenShot("gui_crafting_table", TABLE, "2.5 -60 17.5 90 0");
        screenShot("gui_crafting_imported", TABLE_PIPE, "2.5 -60 17.5 90 0");

        recipeViewerCheck();

        // left under way on purpose: the client quits while these are in the pipes and in the furnace
        step(3, () -> onServer(server -> {
            PipeBlockEntity request = pipe(server, REQUEST);
            boolean ordered = request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 32))).started()
                    && request.request(McItemType.batchOf(new ItemStack(Items.IRON_INGOT, 2))).started();
            LOGGER.info("orders before restart {}", ordered ? "placed" : "REFUSED");
        }));
    }

    // with a recipe viewer installed: let it fill an empty crafting table and an empty crafting pipe,
    // then look on the server whether the recipes arrived
    private void recipeViewerCheck() {
        BlockPos table = new BlockPos(0, -59, 21);
        BlockPos craftingPipe = new BlockPos(0, -59, 23);
        String[] problem = {null};
        for (BlockPos target : new BlockPos[] {table, craftingPipe}) {
            step(10, () -> command("tp @p 2.5 -60 " + (target.getZ() + 0.5) + " 90 0"));
            step(15, () -> onServer(server -> {
                ServerLevel level = server.overworld();
                level.getBlockState(target).useWithoutItem(level, server.getPlayerList().getPlayers().get(0),
                        new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            }));
            step(15, () -> RecipeViewerCheck.run(minecraft.screen).filter(text -> !text.isEmpty()).ifPresent(text -> problem[0] = text));
            step(5, () -> onServer(server -> server.getPlayerList().getPlayers().get(0).closeContainer()));
        }
        step(5, () -> {
            if (RecipeViewerCheck.run(null).isEmpty()) {
                return;
            }
            onServer(server -> {
                ItemStack crafted = ((CraftingTableBlockEntity) server.overworld().getBlockEntity(table)).result();
                PipeBlockEntity pipe = pipe(server, craftingPipe);
                boolean smelting = pipe.config().getItem(0).is(Items.RAW_IRON) && pipe.config().getItem(PipeBlockEntity.OUTPUT_SLOT).is(Items.IRON_INGOT);
                boolean dragged = pipe.config().getItem(2).is(Items.DIAMOND);
                if (problem[0] == null && crafted.is(Items.STICK) && smelting && dragged) {
                    LOGGER.info("recipe viewer check passed");
                } else {
                    LOGGER.error("recipe viewer check failed: {} table makes {}, pipe recipe set {}, dragged marker set {}",
                            problem[0] == null ? "" : problem[0] + ",", crafted, smelting, dragged);
                }
            });
        });
    }

    private void step(int waitAfter, Runnable action) {
        steps.add(new Step(waitAfter, action));
    }

    private void shotFrom(String name, String position) {
        step(15, () -> command("tp @p " + position));
        step(2, () -> shot(name));
    }

    private void screenShot(String name, BlockPos pipe, String position) {
        step(10, () -> command("tp @p " + position));
        step(15, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            level.getBlockState(pipe).useWithoutItem(level, player,
                    new BlockHitResult(Vec3.atCenterOf(pipe), Direction.UP, pipe, false));
        }));
        step(2, () -> shot(name));
        step(5, () -> onServer(server -> server.getPlayerList().getPlayers().get(0).closeContainer()));
    }

    private void shot(String name) {
        Screenshot.grab(output, name + ".png", minecraft.getMainRenderTarget(), message -> { });
    }

    private void command(String command) {
        onServer(server -> server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    private void onServer(Consumer<MinecraftServer> action) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        server.execute(() -> action.accept(server));
    }

    private static PipeBlockEntity pipe(MinecraftServer server, BlockPos pos) {
        return (PipeBlockEntity) server.overworld().getBlockEntity(pos);
    }
}
