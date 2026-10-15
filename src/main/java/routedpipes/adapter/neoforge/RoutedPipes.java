package routedpipes.adapter.neoforge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import routedpipes.adapter.minecraft.GuideBookGift;
import routedpipes.adapter.minecraft.ShownHudPanels;
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
import routedpipes.adapter.neoforge.benchmark.Benchmark;
import routedpipes.adapter.neoforge.gametest.NeoForgeGameTestPlatform;
import routedpipes.adapter.neoforge.gametest.TestsWaitForTicking;
import routedpipes.domain.logistics.PipeKind;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

// composition root, the only place that knows every layer
@Mod(Content.MODID)
public class RoutedPipes {

    private static final String OLD_MODID = "logisticspipes";
    private static final Logger LOGGER = LogUtils.getLogger();
    // the registries the original mod had entries in
    private static final Set<ResourceKey<?>> OLD_REGISTRIES = Set.of(Registries.BLOCK, Registries.ITEM,
            Registries.BLOCK_ENTITY_TYPE, Registries.DATA_COMPONENT_TYPE);

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Content.MODID);

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.routedpipes"))
                .icon(() -> new ItemStack(Content.pipe(PipeKind.BASIC)))
                .displayItems((parameters, output) -> Content.creativeTabItems().forEach(output::accept))
                .build());
    }

    public RoutedPipes(IEventBus modEventBus) {
        Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
        Content.REGISTRATIONS.entries().forEach(entry -> register(registers, entry));
        registers.forEach((registry, register) -> {
            if (OLD_REGISTRIES.contains(registry)) {
                keepOldIds(register);
            }
            register.register(modEventBus);
        });
        TABS.register(modEventBus);
        keepOldIds(NeoForgePlatform.ATTACHMENTS);
        NeoForgePlatform.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(RoutedPipes::registerCapabilities);
        modEventBus.addListener(RoutedPipes::registerPayloads);
        NeoForge.EVENT_BUS.addListener(RoutedPipes::buildCreativeTabs);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (!event.getEntity().level().isClientSide) {
                Content.HUD_WATCH.tick(event.getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent event) ->
                GuideBookGift.crafted(event.getEntity(), event.getCrafting()));
        SecurityEvents.register();
        ComputerCraftSupport.registerIfInstalled(modEventBus);
        Benchmark.registerIfRequested();
        NeoForgeGameTestPlatform.registerIfTesting(modEventBus);
        TestsWaitForTicking.register();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(Map<ResourceKey<?>, DeferredRegister<?>> registers, Registered<?> entry) {
        DeferredRegister register = registers.computeIfAbsent(entry.registry(),
                registry -> DeferredRegister.create((ResourceKey<? extends Registry<Object>>) registry, Content.MODID));
        entry.bind((Supplier) register.register(entry.id().getPath(), () -> entry.factory().get()));
    }

    // the mod had the id of the original once, worlds saved then still name everything by it
    private static void keepOldIds(DeferredRegister<?> register) {
        register.getEntries().forEach(entry -> register.addAlias(
                ResourceLocation.fromNamespaceAndPath(OLD_MODID, entry.getId().getPath()), entry.getId()));
    }

    // the sink for creative tabs asks the tabs what they hold, and on a server nobody else fills them
    private static void buildCreativeTabs(ServerStartedEvent event) {
        try {
            CreativeModeTabs.tryRebuildTabContents(event.getServer().getWorldData().enabledFeatures(), false,
                    event.getServer().registryAccess());
        } catch (RuntimeException e) {
            LOGGER.warn("Could not fill the creative tabs, the creative tab sink will take nothing", e);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetMarkerPayload.TYPE, SetMarkerPayload.CODEC, (payload, context) ->
                MenuRequests.apply(context.player(), payload.containerId(), payload.slot(), payload.stack()));
        registrar.playToServer(ChooseMarkerPayload.TYPE, ChooseMarkerPayload.CODEC, (payload, context) ->
                MenuRequests.choose(context.player(), payload.containerId(), payload.slot(), payload.choices()));
        registrar.playToServer(SetMenuTextPayload.TYPE, SetMenuTextPayload.CODEC, (payload, context) ->
                MenuRequests.applyText(context.player(), payload.containerId(), payload.field(), payload.text()));
        registrar.playToServer(AskIngredientsPayload.TYPE, AskIngredientsPayload.CODEC, (payload, context) ->
                MenuRequests.askIngredients(context.player(), payload.containerId(), payload.question(), payload.choices()));
        registrar.playToServer(OpenRemoteOrdererPayload.TYPE, OpenRemoteOrdererPayload.CODEC, (payload, context) ->
                RemoteOrdererItem.openFromInventory(context.player()));
        registrar.playToClient(HudPanelsPayload.TYPE, HudPanelsPayload.CODEC, (payload, context) ->
                ShownHudPanels.apply(payload.reset(), payload.changed(), payload.gone()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Content.PIPE_BLOCK_ENTITY.get(),
                (pipe, side) -> side == null ? null : new PipeItemHandler(pipe, side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, Content.PIPE_BLOCK_ENTITY.get(),
                (pipe, side) -> side == null || pipe.fluidPipe() == null || !pipe.fluidPipe().receives()
                        ? null : new PipeFluidHandler(pipe.fluidPipe(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Content.CRAFTING_TABLE_BLOCK_ENTITY.get(),
                (table, side) -> new CraftingTableItemHandler(table));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, Content.POWER_JUNCTION_BLOCK_ENTITY.get(),
                (junction, side) -> new PowerJunctionEnergy(junction));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, Content.POWER_PROVIDER_BLOCK_ENTITY.get(),
                (provider, side) -> new PowerProviderEnergy(provider));
    }
}
