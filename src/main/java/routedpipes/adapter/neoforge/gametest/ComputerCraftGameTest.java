package routedpipes.adapter.neoforge.gametest;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import routedpipes.adapter.neoforge.RoutedPipes;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// the peripherals of routed pipes, only there in the run that has CC: Tweaked installed
// the checks are in a class of their own because this one has to load without the mod
@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class ComputerCraftGameTest {

    // a computer starts up in its own time, not in ticks
    private static final int SCRIPT_TICKS = 1200;

    @GameTestGenerator
    public static Collection<TestFunction> peripherals() {
        if (!ModList.get().isLoaded("computercraft")) {
            return List.of();
        }
        return List.of(
                test("computercraft.routed_pipes_are_peripherals", ComputerCraftChecks::routedPipesArePeripherals, 100),
                test("computercraft.messages_reach_attached_computers", ComputerCraftChecks::messagesReachAttachedComputers, 100),
                test("computercraft.a_script_talks_to_the_pipe", ComputerCraftChecks::aScriptTalksToThePipe, SCRIPT_TICKS),
                test("computercraft.a_script_requests_items", ComputerCraftChecks::aScriptRequestsItems, SCRIPT_TICKS),
                test("computercraft.a_locked_pipe_keeps_computers_out", ComputerCraftChecks::aLockedPipeKeepsComputersOut, 100),
                test("computercraft.the_remote_control_upgrade_hands_out_pipe_objects",
                        ComputerCraftChecks::theRemoteControlUpgradeHandsOutPipeObjects, 100),
                test("computercraft.a_script_uses_a_pipe_from_afar", ComputerCraftChecks::aScriptUsesAPipeFromAfar, SCRIPT_TICKS));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body, int maxTicks) {
        return new TestFunction("defaultBatch", name, RoutedPipes.MODID + ":empty", maxTicks, 0, true, body);
    }
}
