package logisticspipes.adapter.neoforge;

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

// never loaded on dedicated servers
@Mod(value = LogisticsPipes.MODID, dist = Dist.CLIENT)
public class LogisticsPipesClient {

    public LogisticsPipesClient(IEventBus modEventBus) {
        modEventBus.addListener(LogisticsPipesClient::registerScreens);
        modEventBus.addListener(LogisticsPipesClient::registerRenderers);
        VisualTest.registerIfRequested();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LogisticsPipes.REQUEST_MENU.get(), RequestScreen::new);
        event.register(LogisticsPipes.SUPPLIER_MENU.get(), PipeConfigScreen::new);
        event.register(LogisticsPipes.CRAFTING_MENU.get(), PipeConfigScreen::new);
        event.register(LogisticsPipes.SATELLITE_MENU.get(), PipeConfigScreen::new);
        event.register(LogisticsPipes.CRAFTING_TABLE_MENU.get(), CraftingTableScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LogisticsPipes.PIPE_BLOCK_ENTITY.get(), PipeItemRenderer::new);
    }
}
