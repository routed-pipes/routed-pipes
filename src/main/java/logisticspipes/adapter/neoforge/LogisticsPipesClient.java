package logisticspipes.adapter.neoforge;

import java.util.List;

import logisticspipes.adapter.minecraft.FluidContents;
import logisticspipes.adapter.minecraft.client.ChassisScreen;
import logisticspipes.adapter.minecraft.client.ClientMenuLink;
import logisticspipes.adapter.minecraft.client.CraftingTableScreen;
import logisticspipes.adapter.minecraft.client.DiskScreen;
import logisticspipes.adapter.minecraft.client.FirewallScreen;
import logisticspipes.adapter.minecraft.client.FluidConfigScreen;
import logisticspipes.adapter.minecraft.client.FluidRequestScreen;
import logisticspipes.adapter.minecraft.client.MacroEditScreen;
import logisticspipes.adapter.minecraft.client.NamesScreen;
import logisticspipes.adapter.minecraft.client.PipeConfigScreen;
import logisticspipes.adapter.minecraft.client.PipeItemRenderer;
import logisticspipes.adapter.minecraft.client.RemoteOrdererKey;
import logisticspipes.adapter.minecraft.client.RequestScreen;
import logisticspipes.adapter.minecraft.client.RequestTableScreen;
import logisticspipes.adapter.minecraft.client.TubeItemRenderer;
import logisticspipes.adapter.minecraft.client.SecurityStationScreen;
import logisticspipes.adapter.minecraft.client.StatisticsTableScreen;
import logisticspipes.adapter.minecraft.client.UpgradeScreen;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.adapter.neoforge.visualtest.ClientBenchmark;
import logisticspipes.adapter.neoforge.visualtest.VisualTest;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

// never loaded on dedicated servers
@Mod(value = LogisticsPipes.MODID, dist = Dist.CLIENT)
public class LogisticsPipesClient {

    public LogisticsPipesClient(IEventBus modEventBus) {
        modEventBus.addListener(LogisticsPipesClient::registerScreens);
        modEventBus.addListener(LogisticsPipesClient::registerRenderers);
        modEventBus.addListener(LogisticsPipesClient::registerItemColors);
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
        event.register(LogisticsPipes.UPGRADE_MENU.get(), UpgradeScreen::new);
        event.register(LogisticsPipes.SECURITY_STATION_MENU.get(), SecurityStationScreen::new);
        event.register(LogisticsPipes.STATISTICS_TABLE_MENU.get(), StatisticsTableScreen::new);
        LogisticsPipes.FLUID_CONFIG_MENUS.values().forEach(menu -> event.register(menu.get(), FluidConfigScreen::new));
        event.register(LogisticsPipes.FLUID_REQUEST_MENU.get(), FluidRequestScreen::new);
    }

    // the window of a fluid container shows the colour of what is in it
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 && stack.get(FluidContents.TYPE) != null
                ? IClientFluidTypeExtensions.of(stack.get(FluidContents.TYPE).fluid()).getTintColor() | 0xFF000000 : -1,
                LogisticsPipes.FLUID_CONTAINER.get());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LogisticsPipes.PIPE_BLOCK_ENTITY.get(), PipeItemRenderer::new);
        event.registerBlockEntityRenderer(LogisticsPipes.TUBE_BLOCK_ENTITY.get(), TubeItemRenderer::new);
    }
}
