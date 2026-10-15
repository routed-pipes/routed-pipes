package routedpipes.adapter.neoforge;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.network.AskIngredientsPayload;
import routedpipes.adapter.minecraft.network.ChooseMarkerPayload;
import routedpipes.adapter.minecraft.network.OpenRemoteOrdererPayload;
import routedpipes.adapter.minecraft.network.SetMarkerPayload;
import routedpipes.adapter.minecraft.network.SetMenuTextPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import routedpipes.adapter.minecraft.FluidContents;
import routedpipes.adapter.minecraft.GuideBookItem;
import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.PipeMounts;
import routedpipes.adapter.minecraft.RequestTableBlock;
import routedpipes.adapter.minecraft.SignCreatorItem;
import routedpipes.adapter.minecraft.client.ChannelScreen;
import routedpipes.adapter.minecraft.client.ChassisScreen;
import routedpipes.adapter.minecraft.client.ClientMenuLink;
import routedpipes.adapter.minecraft.client.CraftingTableScreen;
import routedpipes.adapter.minecraft.client.DiskScreen;
import routedpipes.adapter.minecraft.client.FirewallScreen;
import routedpipes.adapter.minecraft.client.FluidConfigScreen;
import routedpipes.adapter.minecraft.client.FrequencyCardScreen;
import routedpipes.adapter.minecraft.client.GuideBookScreen;
import routedpipes.adapter.minecraft.client.HudRenderer;
import routedpipes.adapter.minecraft.client.HudSettingsScreen;
import routedpipes.adapter.minecraft.client.FluidRequestScreen;
import routedpipes.adapter.minecraft.client.MacroEditScreen;
import routedpipes.adapter.minecraft.client.NamesScreen;
import routedpipes.adapter.minecraft.client.PipeConfigScreen;
import routedpipes.adapter.minecraft.client.PipeItemRenderer;
import routedpipes.adapter.minecraft.client.PlacementGhost;
import routedpipes.adapter.minecraft.client.ProgramCompilerScreen;
import routedpipes.adapter.minecraft.client.RemoteOrdererKey;
import routedpipes.adapter.minecraft.client.RequestScreen;
import routedpipes.adapter.minecraft.client.RequestTableScreen;
import routedpipes.adapter.minecraft.client.TubeItemRenderer;
import routedpipes.adapter.minecraft.client.SecurityStationScreen;
import routedpipes.adapter.minecraft.client.SignItemScreen;
import routedpipes.adapter.minecraft.client.StatisticsTableScreen;
import routedpipes.adapter.minecraft.client.UpgradeScreen;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.neoforge.visualtest.ClientBenchmark;
import routedpipes.adapter.minecraft.client.visualtest.VisualTest;
import routedpipes.domain.look.Mount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

// never loaded on dedicated servers
@Mod(value = Content.MODID, dist = Dist.CLIENT)
public class RoutedPipesClient {

    public RoutedPipesClient(IEventBus modEventBus) {
        modEventBus.addListener(RoutedPipesClient::registerScreens);
        modEventBus.addListener(RoutedPipesClient::registerRenderers);
        modEventBus.addListener(RoutedPipesClient::registerItemColors);
        modEventBus.addListener(RoutedPipesClient::registerMountModels);
        modEventBus.addListener(RoutedPipesClient::addMountsToPipes);
        modEventBus.addListener(RoutedPipesClient::registerItemProperties);
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(RemoteOrdererKey.MAPPING));
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(Content.MODID, "hud_crosshair"),
                (graphics, delta) -> HudRenderer.drawCrosshair(graphics)));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> RemoteOrdererKey.tick());
        // after everything see-through, the panels are see-through themselves
        // the last stage, a panel drawn over everything clears the depth and nothing after it needs that
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
                HudRenderer.render(event.getPoseStack(), event.getCamera());
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderHighlightEvent.Block event) -> PlacementGhost.draw(event.getTarget(),
                event.getPoseStack(), event.getMultiBufferSource(), event.getCamera().getPosition()));
        ClientMenuLink.setSender(new ClientMenuLink.Sender() {
            @Override
            public void setMarker(int containerId, int slot, ItemStack stack) {
                PacketDistributor.sendToServer(new SetMarkerPayload(containerId, slot, stack));
            }

            @Override
            public void chooseMarker(int containerId, int slot, List<ItemStack> choices) {
                PacketDistributor.sendToServer(new ChooseMarkerPayload(containerId, slot, choices));
            }

            @Override
            public void setText(int containerId, int field, String text) {
                PacketDistributor.sendToServer(new SetMenuTextPayload(containerId, field, text));
            }

            @Override
            public void openRemoteOrderer() {
                PacketDistributor.sendToServer(new OpenRemoteOrdererPayload());
            }

            @Override
            public void askIngredients(int containerId, int question, List<List<ItemStack>> choices) {
                PacketDistributor.sendToServer(new AskIngredientsPayload(containerId, question, choices));
            }
        });
        GuideBookItem.setOpener(() -> Minecraft.getInstance().setScreen(new GuideBookScreen()));
        VisualTest.registerIfRequested(tick -> NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick.run()));
        ClientBenchmark.registerIfRequested();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Content.REQUEST_MENU.get(), RequestScreen<RequestMenu>::new);
        event.register(Content.DISK_MENU.get(), DiskScreen::new);
        event.register(Content.MACRO_EDIT_MENU.get(), MacroEditScreen::new);
        Content.CONFIG_MENUS.values().forEach(menu -> event.register(menu.get(), PipeConfigScreen::new));
        event.register(Content.CRAFTING_TABLE_MENU.get(), CraftingTableScreen::new);
        event.register(Content.CHASSIS_MENU.get(), ChassisScreen::new);
        event.register(Content.NAMES_MENU.get(), NamesScreen::new);
        event.register(Content.REQUEST_TABLE_MENU.get(), RequestTableScreen::new);
        event.register(Content.FIREWALL_MENU.get(), FirewallScreen::new);
        event.register(Content.CONNECTOR_MENU.get(), ChannelScreen::new);
        event.register(Content.CHANNEL_MENU.get(), ChannelScreen::new);
        event.register(Content.FREQUENCY_CARD_MENU.get(), FrequencyCardScreen::new);
        event.register(Content.SIGN_ITEM_MENU.get(), SignItemScreen::new);
        event.register(Content.UPGRADE_MENU.get(), UpgradeScreen::new);
        event.register(Content.SECURITY_STATION_MENU.get(), SecurityStationScreen::new);
        event.register(Content.STATISTICS_TABLE_MENU.get(), StatisticsTableScreen::new);
        event.register(Content.PROGRAM_COMPILER_MENU.get(), ProgramCompilerScreen::new);
        Content.FLUID_CONFIG_MENUS.values().forEach(menu -> event.register(menu.get(), FluidConfigScreen::new));
        event.register(Content.FLUID_REQUEST_MENU.get(), FluidRequestScreen::new);
        event.register(Content.HUD_SETTINGS_MENU.get(), HudSettingsScreen::new);
    }

    // the window of a fluid container shows the colour of what is in it
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 && stack.get(FluidContents.TYPE) != null
                ? IClientFluidTypeExtensions.of(stack.get(FluidContents.TYPE).fluid()).getTintColor() | 0xFF000000 : -1,
                Content.FLUID_CONTAINER.get());
    }

    // the model of the sign creator asks for the kind of sign it makes
    private static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(Content.SIGN_CREATOR.get(),
                ResourceLocation.fromNamespaceAndPath(Content.MODID, "sign_mode"),
                (stack, level, entity, seed) -> SignCreatorItem.modeOf(stack).ordinal()));
    }

    private static ModelResourceLocation mountModel(Mount mount) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Content.MODID, PipeMounts.modelPath(mount)));
    }

    // no block state uses the mounts, so their models have to be asked for
    private static void registerMountModels(ModelEvent.RegisterAdditional event) {
        Mount.all().forEach(mount -> event.register(mountModel(mount)));
    }

    private static void addMountsToPipes(ModelEvent.ModifyBakingResult event) {
        Map<Mount, BakedModel> mounts = new HashMap<>();
        Mount.all().forEach(mount -> mounts.put(mount, event.getModels().get(mountModel(mount))));
        // many states of a pipe share a model
        Map<BakedModel, BakedModel> mounted = new IdentityHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof PipeBlock) || block instanceof RequestTableBlock) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                        (location, model) -> mounted.computeIfAbsent(model, plain -> new MountedPipeModel(plain, mounts)));
            }
        }
    }

    // a pipe with the mounts that hold it to the solid blocks around it
    // which ones depends on those blocks, so they are picked whenever the chunk is drawn again
    private static final class MountedPipeModel extends BakedModelWrapper<BakedModel> {

        private static final ModelProperty<List<Mount>> MOUNTS = new ModelProperty<>();

        private final Map<Mount, BakedModel> mounts;

        MountedPipeModel(BakedModel pipe, Map<Mount, BakedModel> mounts) {
            super(pipe);
            this.mounts = mounts;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            ModelData pipeData = super.getModelData(level, pos, state, data);
            List<Mount> chosen = PipeMounts.at(level, pos, state);
            return chosen.isEmpty() ? pipeData : pipeData.derive().with(MOUNTS, chosen).build();
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
            List<BakedQuad> pipe = super.getQuads(state, side, random, data, renderType);
            List<Mount> chosen = data.get(MOUNTS);
            if (chosen == null) {
                return pipe;
            }
            List<BakedQuad> quads = new ArrayList<>(pipe);
            for (Mount mount : chosen) {
                BakedModel model = mounts.get(mount);
                if (renderType == null || model.getRenderTypes(state, random, ModelData.EMPTY).contains(renderType)) {
                    quads.addAll(model.getQuads(state, side, random, ModelData.EMPTY, renderType));
                }
            }
            return quads;
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Content.PIPE_BLOCK_ENTITY.get(), PipeItemRenderer::new);
        event.registerBlockEntityRenderer(Content.TUBE_BLOCK_ENTITY.get(), TubeItemRenderer::new);
    }
}
