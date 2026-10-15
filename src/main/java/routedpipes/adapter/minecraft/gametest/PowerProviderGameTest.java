package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.power.ProviderStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

public class PowerProviderGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos PROVIDER = new BlockPos(1, 1, 1);

    @GameTest(template = EMPTY)
    public static void takesEnergyOfOtherModsThroughItsInputBuffer(GameTestHelper helper) {
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 0);
        OutsideEnergy energy = GameTestPlatform.get().energy(helper.getLevel(), helper.absolutePos(PROVIDER), Direction.UP).orElse(null);

        helper.assertTrue(energy != null && energy.canReceive() && !energy.canExtract(), "the provider should only take energy");
        helper.assertTrue(energy.capacity() == ProviderStore.CAPACITY, "wrong capacity " + energy.capacity());
        int accepted = energy.receive(25_000, false);
        helper.assertTrue(accepted == ProviderStore.INPUT_BUFFER, "the buffer should take 10000 but took " + accepted);
        helper.assertTrue(energy.receive(100, false) == 0, "the buffer is full until the next tick");

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(provider.stored() == ProviderStore.INPUT_BUFFER, "stored " + provider.stored());
            helper.assertTrue(energy.extract(500, false) == 0, "nothing may be taken out");
            helper.assertTrue(energy.receive(100, false) == 100, "the buffer should be free again");
            helper.assertTrue(energy.stored() == ProviderStore.INPUT_BUFFER + 100, "shows " + energy.stored());
            helper.succeed();
        });
    }

    // the original lost what a provider had stored whenever the world was loaded
    @GameTest(template = EMPTY)
    public static void keepsItsEnergyWhenSavedAndLoaded(GameTestHelper helper) {
        PowerProviderBlockEntity original = GameTestPower.provider(helper, PROVIDER, 1_234_567);
        original.receive(321, false);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PowerProviderBlockEntity loaded = GameTestPower.provider(helper, new BlockPos(1, 3, 1), 0);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.stored() == 1_234_567, "stored " + loaded.stored());
        helper.assertTrue(loaded.storedWithInput() == 1_234_567 + 321, "with the buffer " + loaded.storedWithInput());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void routedPipesConnectToIt(GameTestHelper helper) {
        GameTestPower.provider(helper, PROVIDER, 0);
        BlockPos pipe = new BlockPos(1, 2, 1);
        helper.setBlock(pipe, Content.pipe(PipeKind.BASIC));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(PipeBlock.connected(helper.getBlockState(pipe), Direction.DOWN), "the pipe connects to the provider");
            helper.succeed();
        });
    }
}
