package routedpipes.adapter.minecraft.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

// a battery on the test server
public interface TestBattery {

    static TestBattery place(GameTestHelper helper, BlockPos pos, int capacity) {
        return GameTestPlatform.get().placeBattery(helper, pos, capacity);
    }

    int stored();
}
