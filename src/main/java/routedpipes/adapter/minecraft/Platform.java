package routedpipes.adapter.minecraft;

import java.util.ServiceLoader;
import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// what only the loader can do, each loader ships one through META-INF/services
public interface Platform {

    static Platform get() {
        return Holder.INSTANCE;
    }

    ItemStorage items();

    FluidStorage fluids();

    EnergyAccess energy();

    boolean isModLoaded(String modId);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    // kept with the player, also through death
    boolean guideBookGiven(Player player);

    void markGuideBookGiven(Player player);

    // vanilla keeps both constructors to itself, every loader opens them up
    <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory);

    <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks);

    final class Holder {
        private static final Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("no loader ships a routed pipes platform"));

        private Holder() {
        }
    }
}
