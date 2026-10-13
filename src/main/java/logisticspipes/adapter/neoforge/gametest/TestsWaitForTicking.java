package logisticspipes.adapter.neoforge.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

// dev only: on a game test server every test waits until the blocks of its structure really tick
//
// the framework starts a test once its chunks may tick entities, but block entities need the entities of the chunk
// loaded from disk too, and that comes a few ticks later on a busy machine. a test that places pipes and asks
// five ticks later then finds pipes that never had their first tick
public final class TestsWaitForTicking {

    private TestsWaitForTicking() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            if (event.getServer() instanceof GameTestServer server) {
                wrapAll(server);
            }
        });
    }

    // the server builds its batches from this list right after the event
    @SuppressWarnings("unchecked")
    private static void wrapAll(GameTestServer server) {
        try {
            Field field = GameTestServer.class.getDeclaredField("testFunctions");
            field.setAccessible(true);
            ((List<TestFunction>) field.get(server)).replaceAll(TestsWaitForTicking::wrapped);
        } catch (ReflectiveOperationException e) {
            // better no tests than tests that fail now and then
            throw new IllegalStateException("could not make the game tests wait for their chunks", e);
        }
    }

    private static TestFunction wrapped(TestFunction test) {
        return new TestFunction(test.batchName(), test.testName(), test.structureName(), test.rotation(), test.maxTicks(),
                test.setupTicks(), test.required(), test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(), test.skyAccess(),
                helper -> runWhenTicking(helper, test.function()));
    }

    // delays inside the body count from the tick it runs in, so its timing stays what it was
    private static void runWhenTicking(GameTestHelper helper, Consumer<GameTestHelper> body) {
        if (blocksTick(helper)) {
            body.accept(helper);
        } else {
            helper.runAfterDelay(1, () -> runWhenTicking(helper, body));
        }
    }

    // what a chunk needs before its block entities tick
    private static boolean blocksTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // the far corner of the bounds is the first block outside
        return BoundingBox.fromCorners(BlockPos.containing(helper.getBounds().getMinPosition()),
                BlockPos.containing(helper.getBounds().getMaxPosition()).offset(-1, -1, -1)).intersectingChunks()
                .allMatch(chunk -> ticks(level, chunk));
    }

    private static boolean ticks(ServerLevel level, ChunkPos chunk) {
        return level.getChunk(chunk.x, chunk.z).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                && level.areEntitiesLoaded(chunk.toLong()) && level.shouldTickBlocksAt(chunk.toLong());
    }
}
