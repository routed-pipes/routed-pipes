package routedpipes.adapter.neoforge.gametest;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHooks;

// vanilla has no block that takes energy, so on the test server a lapis block is one
public final class TestBattery {

    private static final Map<BlockPos, EnergyStorage> BATTERIES = new HashMap<>();

    private TestBattery() {
    }

    public static void registerIfTesting(RegisterCapabilitiesEvent event) {
        if (GameTestHooks.isGametestServer()) {
            event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                    (level, pos, state, blockEntity, side) -> BATTERIES.computeIfAbsent(pos.immutable(), key -> new EnergyStorage(0)),
                    Blocks.LAPIS_BLOCK);
        }
    }

    // an empty battery with that much room
    static EnergyStorage place(GameTestHelper helper, BlockPos pos, int capacity) {
        EnergyStorage battery = new EnergyStorage(capacity);
        // before the block, so that the pipes next to it already find this battery
        BATTERIES.put(helper.absolutePos(pos), battery);
        helper.setBlock(pos, Blocks.LAPIS_BLOCK);
        return battery;
    }
}
