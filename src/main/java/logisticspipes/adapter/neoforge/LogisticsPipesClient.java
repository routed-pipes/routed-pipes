package logisticspipes.adapter.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import logisticspipes.adapter.minecraft.FluidContents;
import logisticspipes.adapter.minecraft.GuideBookItem;
import logisticspipes.adapter.minecraft.LogisticsPipeBlock;
import logisticspipes.adapter.minecraft.PipeMounts;
import logisticspipes.adapter.minecraft.RequestTableBlock;
import logisticspipes.adapter.minecraft.SignCreatorItem;
import logisticspipes.adapter.minecraft.client.ChannelScreen;
import logisticspipes.adapter.minecraft.client.ChassisScreen;
import logisticspipes.adapter.minecraft.client.ClientMenuLink;
import logisticspipes.adapter.minecraft.client.CraftingTableScreen;
import logisticspipes.adapter.minecraft.client.DiskScreen;
import logisticspipes.adapter.minecraft.client.FirewallScreen;
import logisticspipes.adapter.minecraft.client.FluidConfigScreen;
import logisticspipes.adapter.minecraft.client.FrequencyCardScreen;
import logisticspipes.adapter.minecraft.client.GuideBookScreen;
import logisticspipes.adapter.minecraft.client.FluidRequestScreen;
import logisticspipes.adapter.minecraft.client.MacroEditScreen;
import logisticspipes.adapter.minecraft.client.NamesScreen;
import logisticspipes.adapter.minecraft.client.PipeConfigScreen;
import logisticspipes.adapter.minecraft.client.PipeItemRenderer;
import logisticspipes.adapter.minecraft.client.ProgramCompilerScreen;
import logisticspipes.adapter.minecraft.client.RemoteOrdererKey;
import logisticspipes.adapter.minecraft.client.RequestScreen;
import logisticspipes.adapter.minecraft.client.RequestTableScreen;
import logisticspipes.adapter.minecraft.client.TubeItemRenderer;
import logisticspipes.adapter.minecraft.client.SecurityStationScreen;
import logisticspipes.adapter.minecraft.client.SignItemScreen;
import logisticspipes.adapter.minecraft.client.StatisticsTableScreen;
import logisticspipes.adapter.minecraft.client.UpgradeScreen;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.adapter.neoforge.visualtest.ClientBenchmark;
import logisticspipes.adapter.neoforge.visualtest.VisualTest;
import logisticspipes.domain.look.Mount;
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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

// never loaded on dedicated servers
@Mod(value = LogisticsPipes.MODID, dist = Dist.CLIENT)
public class LogisticsPipesClient {

    public LogisticsPipesClient(IEventBus modEventBus) {
        modEventBus.addListener(LogisticsPipesClient::registerScreens);
        modEventBus.addListener(LogisticsPipesClient::registerRenderers);
        modEventBus.addListener(LogisticsPipesClient::registerItemColors);
        modEventBus.addListener(LogisticsPipesClient::registerMountModels);
        modEventBus.addListener(LogisticsPipesClient::addMountsToPipes);
        modEventBus.addListener(LogisticsPipesClient::registerItemProperties);
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(RemoteOrdererKey.MAPPING));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> RemoteOrdererKey.tick());
        ClientMenuLink.setSender(new ClientMenuLink.Sender() {
            @Override
            public void setMarker(int containerId, int slot, ItemStack stack) {
                PacketDistributor.sendToServer(new SetMarkerPayload(containerId, slot, stack));
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
            public void askIngredients(int containerId, int question, List<ItemStack> ingredients, boolean order) {
                PacketDistributor.sendToServer(new AskIngredientsPayload(containerId, question, order, ingredients));
            }
        });
        GuideBookItem.setOpener(() -> Minecraft.getInstance().setScreen(new GuideBookScreen()));
        VisualTest.registerIfRequested();
        ClientBenchmark.registerIfRequested();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LogisticsPipes.REQUEST_MENU.get(), RequestScreen<RequestMenu>::new);
        event.register(LogisticsPipes.DISK_MENU.get(), DiskScreen::new);
        event.register(LogisticsPipes.MACRO_EDIT_MENU.get(), MacroEditScreen::new);
        LogisticsPipes.CONFIG_MENUS.values().forEach(menu -> event.register(menu.get(), PipeConfigScreen::new));
        event.register(LogisticsPipes.CRAFTING_TABLE_MENU.get(), CraftingTableScreen::new);
        event.register(LogisticsPipes.CHASSIS_MENU.get(), ChassisScreen::new);
        event.register(LogisticsPipes.NAMES_MENU.get(), NamesScreen::new);
        event.register(LogisticsPipes.REQUEST_TABLE_MENU.get(), RequestTableScreen::new);
        event.register(LogisticsPipes.FIREWALL_MENU.get(), FirewallScreen::new);
        event.register(LogisticsPipes.CONNECTOR_MENU.get(), ChannelScreen::new);
        event.register(LogisticsPipes.CHANNEL_MENU.get(), ChannelScreen::new);
        event.register(LogisticsPipes.FREQUENCY_CARD_MENU.get(), FrequencyCardScreen::new);
        event.register(LogisticsPipes.SIGN_ITEM_MENU.get(), SignItemScreen::new);
        event.register(LogisticsPipes.UPGRADE_MENU.get(), UpgradeScreen::new);
        event.register(LogisticsPipes.SECURITY_STATION_MENU.get(), SecurityStationScreen::new);
        event.register(LogisticsPipes.STATISTICS_TABLE_MENU.get(), StatisticsTableScreen::new);
        event.register(LogisticsPipes.PROGRAM_COMPILER_MENU.get(), ProgramCompilerScreen::new);
        LogisticsPipes.FLUID_CONFIG_MENUS.values().forEach(menu -> event.register(menu.get(), FluidConfigScreen::new));
        event.register(LogisticsPipes.FLUID_REQUEST_MENU.get(), FluidRequestScreen::new);
    }

    // the window of a fluid container shows the colour of what is in it
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 && stack.get(FluidContents.TYPE) != null
                ? IClientFluidTypeExtensions.of(stack.get(FluidContents.TYPE).fluid()).getTintColor() | 0xFF000000 : -1,
                LogisticsPipes.FLUID_CONTAINER.get());
    }

    // the model of the sign creator asks for the kind of sign it makes
    private static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(LogisticsPipes.SIGN_CREATOR.get(),
                ResourceLocation.fromNamespaceAndPath(LogisticsPipes.MODID, "sign_mode"),
                (stack, level, entity, seed) -> SignCreatorItem.modeOf(stack).ordinal()));
    }

    private static ModelResourceLocation mountModel(Mount mount) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LogisticsPipes.MODID, PipeMounts.modelPath(mount)));
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
            if (!(block instanceof LogisticsPipeBlock) || block instanceof RequestTableBlock) {
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
        event.registerBlockEntityRenderer(LogisticsPipes.PIPE_BLOCK_ENTITY.get(), PipeItemRenderer::new);
        event.registerBlockEntityRenderer(LogisticsPipes.TUBE_BLOCK_ENTITY.get(), TubeItemRenderer::new);
    }
}
