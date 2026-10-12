package logisticspipes.adapter.neoforge.gametest;

import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

// test scenes keep their first routed pipes at x 0 or 1, a full junction beside each powers the whole network
final class GameTestPower {

    private GameTestPower() {
    }

    static void supply(GameTestHelper helper) {
        junction(helper, new BlockPos(0, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        junction(helper, new BlockPos(1, 1, 2), PowerJunctionBlockEntity.CAPACITY);
    }

    static PowerJunctionBlockEntity junction(GameTestHelper helper, BlockPos pos, int energy) {
        helper.setBlock(pos, LogisticsPipes.POWER_JUNCTION.get());
        PowerJunctionBlockEntity junction = helper.getBlockEntity(pos);
        junction.receive(energy, false);
        return junction;
    }
}
