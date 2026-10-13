package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import routedpipes.domain.power.PowerBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// a pipe at 1 1 1 with a power provider north of it, machines go above and east
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class PowerSupplierGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final BlockPos PROVIDER = new BlockPos(1, 1, 0);
    private static final BlockPos JUNCTION = new BlockPos(1, 1, 2);
    private static final BlockPos ABOVE = new BlockPos(1, 2, 1);
    private static final BlockPos EAST = new BlockPos(2, 1, 1);

    static PipeBlockEntity pipe(GameTestHelper helper, BlockPos pos, UpgradeKind... upgrades) {
        helper.setBlock(pos, LogisticsPipes.pipe(PipeKind.BASIC));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        for (UpgradeKind upgrade : upgrades) {
            helper.assertTrue(pipe.installUpgrade(new ItemStack(LogisticsPipes.UPGRADES.get(upgrade).get())),
                    "the pipe should take the upgrade " + upgrade);
        }
        return pipe;
    }

    @GameTest(template = EMPTY)
    public static void chargesTheMachineNextToThePipeFromTheProvider(GameTestHelper helper) {
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, JUNCTION, 10_000);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        EnergyStorage battery = TestBattery.place(helper, ABOVE, 50_000);
        PipeBlockEntity pipe = pipe(helper, PIPE, UpgradeKind.POWER_SUPPLIER_RF);

        helper.succeedWhen(() -> {
            helper.assertTrue(battery.getEnergyStored() == 50_000, "the battery holds " + battery.getEnergyStored());
            int buffered = pipe.powerPipe().buffered();
            helper.assertTrue(buffered == PowerBuffer.CAPACITY, "the pipe should fill up again, it holds " + buffered);
            helper.assertTrue(provider.stored() == 1_000_000 - 50_000 - buffered, "the provider holds " + provider.stored());
            // 60000 fetched at 100 for one energy
            helper.assertTrue(junction.stored() == 10_000 - 600, "the junction holds " + junction.stored());
        });
    }

    @GameTest(template = EMPTY)
    public static void withoutTheUpgradeNothingIsCharged(GameTestHelper helper) {
        GameTestPower.junction(helper, JUNCTION, 10_000);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        EnergyStorage battery = TestBattery.place(helper, ABOVE, 50_000);
        pipe(helper, PIPE);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(battery.getEnergyStored() == 0, "the battery holds " + battery.getEnergyStored());
            helper.assertTrue(provider.stored() == 1_000_000, "the provider holds " + provider.stored());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void withoutEnergyOfItsOwnTheNetworkFetchesNothing(GameTestHelper helper) {
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        EnergyStorage battery = TestBattery.place(helper, ABOVE, 50_000);
        pipe(helper, PIPE, UpgradeKind.POWER_SUPPLIER_RF);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(battery.getEnergyStored() == 0, "the battery holds " + battery.getEnergyStored());
            helper.assertTrue(provider.stored() == 1_000_000, "the provider holds " + provider.stored());
            helper.succeed();
        });
    }

    // what is in the pipe is split by how much each machine is short of
    @GameTest(template = EMPTY)
    public static void machinesShareWhatThePipeHolds(GameTestHelper helper) {
        GameTestPower.junction(helper, JUNCTION, 10_000);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 3000);
        EnergyStorage small = TestBattery.place(helper, ABOVE, 1000);
        EnergyStorage big = TestBattery.place(helper, EAST, 3000);
        pipe(helper, PIPE, UpgradeKind.POWER_SUPPLIER_RF);

        helper.succeedWhen(() -> {
            helper.assertTrue(provider.stored() == 0, "the provider holds " + provider.stored());
            helper.assertTrue(small.getEnergyStored() == 750 && big.getEnergyStored() == 2250,
                    "expected 750 and 2250 but found " + small.getEnergyStored() + " and " + big.getEnergyStored());
        });
    }

    // junctions and providers take energy of other mods too, the pipe must not move it in circles
    @GameTest(template = EMPTY)
    public static void doesNotChargeTheBlocksOfTheNetwork(GameTestHelper helper) {
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, JUNCTION, 1000);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        PowerProviderBlockEntity other = GameTestPower.provider(helper, ABOVE, 0);
        PipeBlockEntity pipe = pipe(helper, PIPE, UpgradeKind.POWER_SUPPLIER_RF);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(pipe.powerPipe().buffered() == PowerBuffer.CAPACITY, "the pipe holds " + pipe.powerPipe().buffered());
            helper.assertTrue(provider.stored() == 1_000_000 - PowerBuffer.CAPACITY, "the provider holds " + provider.stored());
            helper.assertTrue(other.storedWithInput() == 0, "the empty provider got " + other.storedWithInput());
            helper.assertTrue(junction.stored() == 900, "the junction holds " + junction.stored());
            helper.succeed();
        });
    }

    // the original loaded the wrong value into the pipe
    @GameTest(template = EMPTY)
    public static void thePipeKeepsWhatItHoldsWhenSavedAndLoaded(GameTestHelper helper) {
        PipeBlockEntity original = pipe(helper, PIPE, UpgradeKind.POWER_SUPPLIER_RF);
        original.powerPipe().receive(4321);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag power = new CompoundTag();
        power.putInt("Power", saved.getInt("Power"));

        PipeBlockEntity loaded = pipe(helper, new BlockPos(1, 3, 1));
        loaded.loadWithComponents(power, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.powerPipe().buffered() == 4321, "the loaded pipe holds " + loaded.powerPipe().buffered());
        helper.succeed();
    }
}
