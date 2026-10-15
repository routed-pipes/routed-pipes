package routedpipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import routedpipes.adapter.minecraft.gametest.GameTestPlatform;
import routedpipes.adapter.minecraft.gametest.GameTests;
import routedpipes.adapter.minecraft.gametest.OutsideEnergy;
import routedpipes.adapter.minecraft.gametest.TestBattery;
import routedpipes.adapter.minecraft.gametest.TestTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHooks;

public final class NeoForgeGameTestPlatform implements GameTestPlatform {

    // on the test server tinted glass has a tank and a lapis block takes energy
    private static final Map<BlockPos, FluidTank> TANKS = new HashMap<>();
    private static final Map<BlockPos, EnergyStorage> BATTERIES = new HashMap<>();

    public static void registerIfTesting(IEventBus modEventBus) {
        modEventBus.addListener((RegisterGameTestsEvent event) -> GameTestRegistry.getAllTestFunctions().addAll(GameTests.functions()));
        if (!GameTestHooks.isGametestServer()) {
            return;
        }
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlock(Capabilities.FluidHandler.BLOCK,
                    (level, pos, state, blockEntity, side) -> TANKS.computeIfAbsent(pos.immutable(), key -> new FluidTank(TestTank.CAPACITY)),
                    Blocks.TINTED_GLASS);
            event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                    (level, pos, state, blockEntity, side) -> BATTERIES.computeIfAbsent(pos.immutable(), key -> new EnergyStorage(0)),
                    Blocks.LAPIS_BLOCK);
        });
    }

    private record Tank(FluidTank tank) implements TestTank {

        @Override
        public Fluid fluid() {
            return tank.getFluid().getFluid();
        }

        @Override
        public int amount() {
            return tank.getFluidAmount();
        }

        @Override
        public void drain(int amount) {
            tank.drain(amount, FluidAction.EXECUTE);
        }

        @Override
        public void fill(Fluid fluid, int amount) {
            tank.fill(new FluidStack(fluid, amount), FluidAction.EXECUTE);
        }
    }

    private record Energy(IEnergyStorage storage) implements OutsideEnergy {

        @Override
        public boolean canReceive() {
            return storage.canReceive();
        }

        @Override
        public boolean canExtract() {
            return storage.canExtract();
        }

        @Override
        public int receive(int amount, boolean simulate) {
            return storage.receiveEnergy(amount, simulate);
        }

        @Override
        public int extract(int amount, boolean simulate) {
            return storage.extractEnergy(amount, simulate);
        }

        @Override
        public int stored() {
            return storage.getEnergyStored();
        }

        @Override
        public int capacity() {
            return storage.getMaxEnergyStored();
        }
    }

    @Override
    public TestTank placeTank(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount) {
        FluidTank tank = new FluidTank(TestTank.CAPACITY);
        if (amount > 0) {
            tank.fill(new FluidStack(fluid, amount), FluidAction.EXECUTE);
        }
        // before the block, so that the pipes next to it already find this tank
        TANKS.put(helper.absolutePos(pos), tank);
        helper.setBlock(pos, Blocks.TINTED_GLASS);
        return new Tank(tank);
    }

    @Override
    public TestBattery placeBattery(GameTestHelper helper, BlockPos pos, int capacity) {
        EnergyStorage battery = new EnergyStorage(capacity);
        BATTERIES.put(helper.absolutePos(pos), battery);
        helper.setBlock(pos, Blocks.LAPIS_BLOCK);
        return battery::getEnergyStored;
    }

    @Override
    public Optional<OutsideEnergy> energy(Level level, BlockPos pos, Direction side) {
        return Optional.ofNullable(level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side)).map(Energy::new);
    }

    @Override
    public boolean hitStopped(Player player, BlockPos pos) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickBlock(player, pos, Direction.UP,
                PlayerInteractEvent.LeftClickBlock.Action.START)).isCanceled();
    }

    @Override
    public boolean breakStopped(Level level, BlockPos pos, Player player) {
        return NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled();
    }

    @Override
    public boolean machineBreakStopped(Level level, BlockPos pos) {
        return breakStopped(level, pos, FakePlayerFactory.getMinecraft((ServerLevel) level));
    }

    @Override
    public void detonate(Level level, Explosion explosion) {
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
    }

    @Override
    public void crafted(Player player, ItemStack crafted) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, crafted, new SimpleContainer(1)));
    }
}
