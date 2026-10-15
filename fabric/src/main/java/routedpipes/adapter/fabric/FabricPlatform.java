package routedpipes.adapter.fabric;

import java.util.function.BiFunction;

import routedpipes.adapter.minecraft.EnergyAccess;
import routedpipes.adapter.minecraft.FluidStorage;
import routedpipes.adapter.minecraft.ItemStorage;
import routedpipes.adapter.minecraft.Platform;
import routedpipes.adapter.minecraft.content.Content;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class FabricPlatform implements Platform {

    // kept when the player dies
    @SuppressWarnings("UnstableApiUsage")
    static final AttachmentType<Boolean> GUIDE_BOOK_GIVEN = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(Content.MODID, "guide_book_given"),
            builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath());

    private final ItemStorage items = new TransferItemStorage();
    private final FluidStorage fluids = new TransferFluidStorage();
    private final EnergyAccess energy = new RebornEnergyAccess();

    @Override
    public ItemStorage items() {
        return items;
    }

    @Override
    public FluidStorage fluids() {
        return fluids;
    }

    @Override
    public EnergyAccess energy() {
        return energy;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public boolean guideBookGiven(Player player) {
        return player.getAttachedOrElse(GUIDE_BOOK_GIVEN, false);
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void markGuideBookGiven(Player player) {
        player.setAttached(GUIDE_BOOK_GIVEN, true);
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory) {
        return new MenuType<>(factory::apply, FeatureFlags.DEFAULT_FLAGS);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::apply, blocks).build(null);
    }
}
