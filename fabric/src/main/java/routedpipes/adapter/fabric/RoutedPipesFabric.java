package routedpipes.adapter.fabric;

import routedpipes.adapter.fabric.gametest.FabricGameTestPlatform;
import routedpipes.adapter.minecraft.PipeProtection;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.RemoteOrdererItem;
import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.content.Registered;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.adapter.minecraft.network.AskIngredientsPayload;
import routedpipes.adapter.minecraft.network.ChooseMarkerPayload;
import routedpipes.adapter.minecraft.network.HudPanelsPayload;
import routedpipes.adapter.minecraft.network.OpenRemoteOrdererPayload;
import routedpipes.adapter.minecraft.network.SetMarkerPayload;
import routedpipes.adapter.minecraft.network.SetMenuTextPayload;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.power.ProviderStore;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import team.reborn.energy.api.EnergyStorage;

// composition root on Fabric, the only place that knows every layer
public final class RoutedPipesFabric implements ModInitializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        Content.REGISTRATIONS.entries().forEach(RoutedPipesFabric::register);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(Content.MODID, "main"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.routedpipes"))
                        .icon(() -> new ItemStack(Content.pipe(PipeKind.BASIC)))
                        .displayItems((parameters, output) -> Content.creativeTabItems().forEach(output::accept))
                        .build());
        registerLookups();
        registerPayloads();
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(Content.HUD_WATCH::tick));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // the sink for creative tabs asks the tabs what they hold, and on a server nobody else fills them
            try {
                CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), false, server.registryAccess());
            } catch (RuntimeException e) {
                LOGGER.warn("Could not fill the creative tabs, the creative tab sink will take nothing", e);
            }
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                PipeProtection.stopsHit(level, pos, player) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !PipeProtection.stopsBreak(level, pos, player, player instanceof FakePlayer));
        FabricGameTestPlatform.registerIfTesting();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(Registered<?> entry) {
        Registry registry = BuiltInRegistries.REGISTRY.get(entry.registry().location());
        Object value = Registry.register(registry, entry.id(), entry.factory().get());
        entry.bind((java.util.function.Supplier) () -> value);
    }

    private static void registerLookups() {
        ItemStorage.SIDED.registerForBlockEntity((pipe, side) -> side == null ? null : new PipeItemInput(pipe, side),
                Content.PIPE_BLOCK_ENTITY.get());
        FluidStorage.SIDED.registerForBlockEntity((pipe, side) -> side == null || pipe.fluidPipe() == null
                || !pipe.fluidPipe().receives() ? null : new PipeFluidInput(pipe.fluidPipe(), side), Content.PIPE_BLOCK_ENTITY.get());
        ItemStorage.SIDED.registerForBlockEntity((table, side) -> CraftingTableItems.of(table), Content.CRAFTING_TABLE_BLOCK_ENTITY.get());
        EnergyStorage.SIDED.registerForBlockEntity((junction, side) -> new EnergyInput(new EnergyInput.Target() {
            @Override
            public int receive(int amount, boolean simulate) {
                return junction.receive(amount, simulate);
            }

            @Override
            public int stored() {
                return junction.stored();
            }

            @Override
            public int capacity() {
                return PowerJunctionBlockEntity.CAPACITY;
            }
        }), Content.POWER_JUNCTION_BLOCK_ENTITY.get());
        EnergyStorage.SIDED.registerForBlockEntity((provider, side) -> new EnergyInput(new EnergyInput.Target() {
            @Override
            public int receive(int amount, boolean simulate) {
                return provider.receive(amount, simulate);
            }

            @Override
            public int stored() {
                return provider.storedWithInput();
            }

            @Override
            public int capacity() {
                return ProviderStore.CAPACITY;
            }
        }), Content.POWER_PROVIDER_BLOCK_ENTITY.get());
    }

    private static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SetMarkerPayload.TYPE, SetMarkerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChooseMarkerPayload.TYPE, ChooseMarkerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetMenuTextPayload.TYPE, SetMenuTextPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AskIngredientsPayload.TYPE, AskIngredientsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenRemoteOrdererPayload.TYPE, OpenRemoteOrdererPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HudPanelsPayload.TYPE, HudPanelsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetMarkerPayload.TYPE, (payload, context) ->
                MenuRequests.apply(context.player(), payload.containerId(), payload.slot(), payload.stack()));
        ServerPlayNetworking.registerGlobalReceiver(ChooseMarkerPayload.TYPE, (payload, context) ->
                MenuRequests.choose(context.player(), payload.containerId(), payload.slot(), payload.choices()));
        ServerPlayNetworking.registerGlobalReceiver(SetMenuTextPayload.TYPE, (payload, context) ->
                MenuRequests.applyText(context.player(), payload.containerId(), payload.field(), payload.text()));
        ServerPlayNetworking.registerGlobalReceiver(AskIngredientsPayload.TYPE, (payload, context) ->
                MenuRequests.askIngredients(context.player(), payload.containerId(), payload.question(), payload.choices()));
        ServerPlayNetworking.registerGlobalReceiver(OpenRemoteOrdererPayload.TYPE, (payload, context) ->
                RemoteOrdererItem.openFromInventory(context.player()));
    }
}
