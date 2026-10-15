package routedpipes.adapter.minecraft.gametest;

import java.util.Optional;
import java.util.ServiceLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

// what the game tests need from the loader, each loader ships one through META-INF/services
public interface GameTestPlatform {

    static GameTestPlatform get() {
        return Holder.INSTANCE;
    }

    // vanilla has no block with a tank, an empty one or one that holds that much of the fluid
    TestTank placeTank(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount);

    // vanilla has no block that takes energy either, an empty one with that much room
    TestBattery placeBattery(GameTestHelper helper, BlockPos pos, int capacity);

    // the energy of a block as other mods see it through that side
    Optional<OutsideEnergy> energy(Level level, BlockPos pos, Direction side);

    // what the loader does when a player starts hitting a block, true if it stopped them
    boolean hitStopped(Player player, BlockPos pos);

    // a block broken for a player without a click on it, like a hammer does, true if it was stopped
    boolean breakStopped(Level level, BlockPos pos, Player player);

    // the same for a block breaker of another mod
    boolean machineBreakStopped(Level level, BlockPos pos);

    // the loader gets to take blocks off the list of what the explosion destroys
    void detonate(Level level, Explosion explosion);

    void crafted(Player player, ItemStack crafted);

    final class Holder {
        private static final GameTestPlatform INSTANCE = ServiceLoader.load(GameTestPlatform.class,
                GameTestPlatform.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("no loader ships a game test platform"));

        private Holder() {
        }
    }
}
