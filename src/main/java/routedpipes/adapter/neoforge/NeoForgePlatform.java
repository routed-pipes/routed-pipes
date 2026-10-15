package routedpipes.adapter.neoforge;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import routedpipes.adapter.minecraft.EnergyAccess;
import routedpipes.adapter.minecraft.FluidStorage;
import routedpipes.adapter.minecraft.ItemStorage;
import routedpipes.adapter.minecraft.Platform;
import routedpipes.adapter.minecraft.content.Content;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class NeoForgePlatform implements Platform {

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Content.MODID);
    // kept when the player dies
    static final Supplier<AttachmentType<Boolean>> GUIDE_BOOK_GIVEN = ATTACHMENTS.register("guide_book_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private final ItemStorage items = new CapabilityItemStorage();
    private final FluidStorage fluids = new CapabilityFluidStorage();
    private final EnergyAccess energy = new CapabilityEnergyAccess();

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
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean guideBookGiven(Player player) {
        return player.getData(GUIDE_BOOK_GIVEN);
    }

    @Override
    public void markGuideBookGiven(Player player) {
        player.setData(GUIDE_BOOK_GIVEN, true);
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
