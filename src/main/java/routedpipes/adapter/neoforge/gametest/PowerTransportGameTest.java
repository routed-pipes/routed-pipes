package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.FirewallSettings;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// a provider on top of the pipe at 0 1 1, the supplying pipe with its battery further along
@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class PowerTransportGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos FIRST = new BlockPos(0, 1, 1);
    private static final BlockPos PROVIDER = new BlockPos(0, 2, 1);
    private static final int BATTERY = 30_000;

    private static EnergyStorage batteryAbove(GameTestHelper helper, BlockPos pipe) {
        return TestBattery.place(helper, pipe.above(), BATTERY);
    }

    private static void assertNothingArrives(GameTestHelper helper, PowerProviderBlockEntity provider, EnergyStorage battery,
            PipeBlockEntity supplier) {
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(supplier.networkStatus().orElseThrow().energy() > 0, "the supplying pipe should see the junctions");
            helper.assertTrue(battery.getEnergyStored() == 0, "the battery holds " + battery.getEnergyStored());
            helper.assertTrue(provider.stored() == 1_000_000, "the provider holds " + provider.stored());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void energyGoesAlongAStraightLineOfPipes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        PowerSupplierGameTest.pipe(helper, FIRST, UpgradeKind.POWER_TRANSPORTATION);
        helper.setBlock(new BlockPos(1, 1, 1), RoutedPipes.TRANSPORT_PIPE.get());
        helper.setBlock(new BlockPos(2, 1, 1), RoutedPipes.TRANSPORT_PIPE.get());
        BlockPos last = new BlockPos(3, 1, 1);
        PowerSupplierGameTest.pipe(helper, last, UpgradeKind.POWER_SUPPLIER_RF);
        EnergyStorage battery = batteryAbove(helper, last);

        helper.succeedWhen(() -> helper.assertTrue(battery.getEnergyStored() == BATTERY,
                "the battery holds " + battery.getEnergyStored() + ", the provider " + provider.stored()));
    }

    @GameTest(template = EMPTY)
    public static void energyGoesThroughSeveralRoutedPipesWithTheUpgrade(GameTestHelper helper) {
        GameTestPower.supply(helper);
        GameTestPower.provider(helper, PROVIDER, 1_000_000);
        PowerSupplierGameTest.pipe(helper, FIRST, UpgradeKind.POWER_TRANSPORTATION);
        PowerSupplierGameTest.pipe(helper, new BlockPos(1, 1, 1), UpgradeKind.POWER_TRANSPORTATION);
        BlockPos last = new BlockPos(2, 1, 1);
        PowerSupplierGameTest.pipe(helper, last, UpgradeKind.POWER_SUPPLIER_RF);
        EnergyStorage battery = batteryAbove(helper, last);

        helper.succeedWhen(() -> helper.assertTrue(battery.getEnergyStored() == BATTERY, "the battery holds " + battery.getEnergyStored()));
    }

    @GameTest(template = EMPTY)
    public static void aRoutedPipeWithoutTheUpgradeStopsTheEnergy(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        PowerSupplierGameTest.pipe(helper, FIRST, UpgradeKind.POWER_TRANSPORTATION);
        PowerSupplierGameTest.pipe(helper, new BlockPos(1, 1, 1));
        BlockPos last = new BlockPos(2, 1, 1);
        PipeBlockEntity supplier = PowerSupplierGameTest.pipe(helper, last, UpgradeKind.POWER_SUPPLIER_RF);

        assertNothingArrives(helper, provider, batteryAbove(helper, last), supplier);
    }

    @GameTest(template = EMPTY)
    public static void thePipeAtTheProviderNeedsTheUpgradeToo(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        PowerSupplierGameTest.pipe(helper, FIRST);
        BlockPos last = new BlockPos(1, 1, 1);
        PipeBlockEntity supplier = PowerSupplierGameTest.pipe(helper, last, UpgradeKind.POWER_SUPPLIER_RF);

        assertNothingArrives(helper, provider, batteryAbove(helper, last), supplier);
    }

    // items would get there, the energy does not
    @GameTest(template = EMPTY)
    public static void energyDoesNotGoRoundCorners(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        PowerSupplierGameTest.pipe(helper, FIRST, UpgradeKind.POWER_TRANSPORTATION);
        helper.setBlock(new BlockPos(1, 1, 1), RoutedPipes.TRANSPORT_PIPE.get());
        BlockPos last = new BlockPos(1, 1, 0);
        PipeBlockEntity supplier = PowerSupplierGameTest.pipe(helper, last, UpgradeKind.POWER_SUPPLIER_RF);

        assertNothingArrives(helper, provider, batteryAbove(helper, last), supplier);
    }

    // a firewall stops power until somebody allows it, the supplying pipe has a junction on its own side
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aFirewallStopsTheEnergyUntilItAllowsPower(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PowerProviderBlockEntity provider = GameTestPower.provider(helper, PROVIDER, 1_000_000);
        PowerSupplierGameTest.pipe(helper, FIRST, UpgradeKind.POWER_TRANSPORTATION);
        BlockPos wall = new BlockPos(1, 1, 1);
        helper.setBlock(wall, RoutedPipes.pipe(PipeKind.FIREWALL));
        PipeBlockEntity firewall = helper.getBlockEntity(wall);
        firewall.installUpgrade(new ItemStack(RoutedPipes.UPGRADES.get(UpgradeKind.POWER_TRANSPORTATION).get()));
        BlockPos last = new BlockPos(2, 1, 1);
        GameTestPower.junction(helper, new BlockPos(2, 1, 2), 10_000);
        PowerSupplierGameTest.pipe(helper, last, UpgradeKind.POWER_SUPPLIER_RF);
        EnergyStorage battery = batteryAbove(helper, last);

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(battery.getEnergyStored() == 0, "the firewall let through " + battery.getEnergyStored());
            helper.assertTrue(provider.stored() == 1_000_000, "the provider holds " + provider.stored());
            firewall.firewallSettings().data().set(FirewallSettings.POWER, 0);
        });
        helper.succeedWhen(() -> helper.assertTrue(battery.getEnergyStored() == BATTERY, "the battery holds " + battery.getEnergyStored()));
    }
}
