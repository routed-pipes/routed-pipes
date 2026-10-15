package routedpipes.adapter.fabric.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import routedpipes.adapter.minecraft.GuideBookGift;
import routedpipes.adapter.minecraft.gametest.GameTestPlatform;
import routedpipes.adapter.minecraft.gametest.GameTests;
import routedpipes.adapter.minecraft.gametest.OutsideEnergy;
import routedpipes.adapter.minecraft.gametest.TestBattery;
import routedpipes.adapter.minecraft.gametest.TestTank;
import routedpipes.adapter.minecraft.gametest.TestsWaitForTicking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.MixinEnvironment;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

public final class FabricGameTestPlatform implements GameTestPlatform {

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    // on the test server tinted glass has a tank and a lapis block takes energy
    private static final Map<BlockPos, Tank> TANKS = new HashMap<>();
    private static final Map<BlockPos, SimpleEnergyStorage> BATTERIES = new HashMap<>();

    // the property Fabric starts its game test server with
    public static void registerIfTesting() {
        if (System.getProperty("fabric-api.gametest") == null) {
            return;
        }
        // every mixin applied now, so one that misses its target fails the run instead of a game later
        MixinEnvironment.getCurrentEnvironment().audit();
        GameTestRegistry.getAllTestFunctions().addAll(GameTests.functions());
        FluidStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) ->
                TANKS.computeIfAbsent(pos.immutable(), key -> new Tank()), Blocks.TINTED_GLASS);
        EnergyStorage.SIDED.registerForBlocks((level, pos, state, blockEntity, side) ->
                BATTERIES.computeIfAbsent(pos.immutable(), key -> new SimpleEnergyStorage(0, 0, 0)), Blocks.LAPIS_BLOCK);
        ServerLifecycleEvents.SERVER_STARTING.register(TestsWaitForTicking::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(TestsWaitForTicking::serverStopping);
    }

    private static final class Tank extends SingleVariantStorage<FluidVariant> implements TestTank {

        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return CAPACITY * DROPLETS_PER_MB;
        }

        @Override
        public Fluid fluid() {
            return variant.isBlank() ? Fluids.EMPTY : variant.getFluid();
        }

        @Override
        public int amount() {
            return (int) (amount / DROPLETS_PER_MB);
        }

        @Override
        public void drain(int drained) {
            try (Transaction transaction = Transaction.openOuter()) {
                extract(variant, drained * DROPLETS_PER_MB, transaction);
                transaction.commit();
            }
        }

        @Override
        public void fill(Fluid fluid, int filled) {
            try (Transaction transaction = Transaction.openOuter()) {
                insert(FluidVariant.of(fluid), filled * DROPLETS_PER_MB, transaction);
                transaction.commit();
            }
        }
    }

    private record Energy(EnergyStorage storage) implements OutsideEnergy {

        @Override
        public boolean canReceive() {
            return storage.supportsInsertion();
        }

        @Override
        public boolean canExtract() {
            return storage.supportsExtraction();
        }

        @Override
        public int receive(int amount, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long received = storage.insert(amount, transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return (int) received;
            }
        }

        @Override
        public int extract(int amount, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = storage.extract(amount, transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return (int) extracted;
            }
        }

        @Override
        public int stored() {
            return (int) Math.min(storage.getAmount(), Integer.MAX_VALUE);
        }

        @Override
        public int capacity() {
            return (int) Math.min(storage.getCapacity(), Integer.MAX_VALUE);
        }
    }

    @Override
    public TestTank placeTank(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount) {
        Tank tank = new Tank();
        if (amount > 0) {
            tank.fill(fluid, amount);
        }
        // before the block, so that the pipes next to it already find this tank
        TANKS.put(helper.absolutePos(pos), tank);
        helper.setBlock(pos, Blocks.TINTED_GLASS);
        return tank;
    }

    @Override
    public TestBattery placeBattery(GameTestHelper helper, BlockPos pos, int capacity) {
        SimpleEnergyStorage battery = new SimpleEnergyStorage(capacity, capacity, 0);
        BATTERIES.put(helper.absolutePos(pos), battery);
        helper.setBlock(pos, Blocks.LAPIS_BLOCK);
        return () -> (int) battery.getAmount();
    }

    @Override
    public Optional<OutsideEnergy> energy(Level level, BlockPos pos, Direction side) {
        return Optional.ofNullable(EnergyStorage.SIDED.find(level, pos, side)).map(Energy::new);
    }

    @Override
    public boolean hitStopped(Player player, BlockPos pos) {
        return AttackBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, pos, Direction.UP)
                == InteractionResult.FAIL;
    }

    @Override
    public boolean breakStopped(Level level, BlockPos pos, Player player) {
        return !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, level.getBlockState(pos),
                level.getBlockEntity(pos));
    }

    @Override
    public boolean machineBreakStopped(Level level, BlockPos pos) {
        return breakStopped(level, pos, FakePlayer.get((ServerLevel) level));
    }

    // a real explosion works out what it destroys, so this goes through the mixin
    @Override
    public void detonate(Level level, Explosion explosion) {
        explosion.explode();
    }

    @Override
    public void crafted(Player player, ItemStack crafted) {
        GuideBookGift.crafted(player, crafted);
    }
}
