package logisticspipes.adapter.neoforge.gametest;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHooks;

// vanilla has no block with a tank, so on the test server tinted glass is one
public final class TestTank {

    public static final int CAPACITY = 16000;
    private static final Map<BlockPos, FluidTank> TANKS = new HashMap<>();

    private TestTank() {
    }

    public static void registerIfTesting(RegisterCapabilitiesEvent event) {
        if (GameTestHooks.isGametestServer()) {
            event.registerBlock(Capabilities.FluidHandler.BLOCK,
                    (level, pos, state, blockEntity, side) -> TANKS.computeIfAbsent(pos.immutable(), key -> new FluidTank(CAPACITY)),
                    Blocks.TINTED_GLASS);
        }
    }

    // an empty tank, or one that holds that much of the fluid
    static FluidTank place(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount) {
        FluidTank tank = new FluidTank(CAPACITY);
        if (amount > 0) {
            tank.fill(new FluidStack(fluid, amount), FluidAction.EXECUTE);
        }
        // before the block, so that the pipes next to it already find this tank
        TANKS.put(helper.absolutePos(pos), tank);
        helper.setBlock(pos, Blocks.TINTED_GLASS);
        return tank;
    }
}
