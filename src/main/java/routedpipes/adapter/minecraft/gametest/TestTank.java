package routedpipes.adapter.minecraft.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluid;

// a tank on the test server, in mB
public interface TestTank {

    int CAPACITY = 16000;

    static TestTank place(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount) {
        return GameTestPlatform.get().placeTank(helper, pos, fluid, amount);
    }

    // empty if the tank is
    Fluid fluid();

    int amount();

    void drain(int amount);

    void fill(Fluid fluid, int amount);
}
