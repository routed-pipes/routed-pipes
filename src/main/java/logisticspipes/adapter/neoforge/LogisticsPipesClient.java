package logisticspipes.adapter.neoforge;

import logisticspipes.adapter.minecraft.client.ChassisScreen;
import logisticspipes.adapter.minecraft.client.ClientMenuLink;
import logisticspipes.adapter.minecraft.client.CraftingTableScreen;
import logisticspipes.adapter.minecraft.client.NamesScreen;
import logisticspipes.adapter.minecraft.client.PipeConfigScreen;
import logisticspipes.adapter.minecraft.client.PipeItemRenderer;
import logisticspipes.adapter.minecraft.client.RemoteOrdererKey;
import logisticspipes.adapter.minecraft.client.RequestScreen;
import logisticspipes.adapter.minecraft.client.UpgradeScreen;
import logisticspipes.adapter.neoforge.visualtest.ClientBenchmark;
import logisticspipes.adapter.neoforge.visualtest.VisualTest;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
        });
        VisualTest.registerIfRequested();
        ClientBenchmark.registerIfRequested();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LogisticsPipes.REQUEST_MENU.get(), RequestScreen::new);
        LogisticsPipes.CONFIG_MENUS.values().forEach(menu -> event.register(menu.get(), PipeConfigScreen::new));
        event.register(LogisticsPipes.CRAFTING_TABLE_MENU.get(), CraftingTableScreen::new);
        event.register(LogisticsPipes.CHASSIS_MENU.get(), ChassisScreen::new);
        event.register(LogisticsPipes.NAMES_MENU.get(), NamesScreen::new);
        event.register(LogisticsPipes.UPGRADE_MENU.get(), UpgradeScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LogisticsPipes.PIPE_BLOCK_ENTITY.get(), PipeItemRenderer::new);
    }
}
