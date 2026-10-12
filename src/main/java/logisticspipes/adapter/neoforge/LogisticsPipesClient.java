package logisticspipes.adapter.neoforge;

import logisticspipes.adapter.minecraft.client.ChassisScreen;
import logisticspipes.adapter.minecraft.client.ClientMarkers;
import logisticspipes.adapter.minecraft.client.CraftingTableScreen;
import logisticspipes.adapter.minecraft.client.PipeConfigScreen;
import logisticspipes.adapter.minecraft.client.PipeItemRenderer;
import logisticspipes.adapter.minecraft.client.RequestScreen;
import logisticspipes.adapter.neoforge.visualtest.VisualTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// never loaded on dedicated servers
@Mod(value = LogisticsPipes.MODID, dist = Dist.CLIENT)
public class LogisticsPipesClient {

    public LogisticsPipesClient(IEventBus modEventBus) {
        modEventBus.addListener(LogisticsPipesClient::registerScreens);
        modEventBus.addListener(LogisticsPipesClient::registerRenderers);
        ClientMarkers.setSender((containerId, slot, stack) ->
                PacketDistributor.sendToServer(new SetMarkerPayload(containerId, slot, stack)));
        VisualTest.registerIfRequested();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LogisticsPipes.REQUEST_MENU.get(), RequestScreen::new);
        LogisticsPipes.CONFIG_MENUS.values().forEach(menu -> event.register(menu.get(), PipeConfigScreen::new));
        event.register(LogisticsPipes.CRAFTING_TABLE_MENU.get(), CraftingTableScreen::new);
        event.register(LogisticsPipes.CHASSIS_MENU.get(), ChassisScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LogisticsPipes.PIPE_BLOCK_ENTITY.get(), PipeItemRenderer::new);
    }
}
