package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

// test scenes keep their first routed pipes at x 0 or 1, a full junction beside each powers the whole network
final class GameTestPower {

    private GameTestPower() {
    }

    static void supply(GameTestHelper helper) {
        junction(helper, new BlockPos(0, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        junction(helper, new BlockPos(1, 1, 2), PowerJunctionBlockEntity.CAPACITY);
    }

    static PowerJunctionBlockEntity junction(GameTestHelper helper, BlockPos pos, int energy) {
        helper.setBlock(pos, Content.POWER_JUNCTION.get());
        PowerJunctionBlockEntity junction = helper.getBlockEntity(pos);
        junction.receive(energy, false);
        return junction;
    }

    // a power provider that already holds that much
    static PowerProviderBlockEntity provider(GameTestHelper helper, BlockPos pos, int energy) {
        helper.setBlock(pos, Content.POWER_PROVIDER_RF.get());
        PowerProviderBlockEntity provider = helper.getBlockEntity(pos);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", energy);
        provider.loadWithComponents(tag, helper.getLevel().registryAccess());
        return provider;
    }
}
