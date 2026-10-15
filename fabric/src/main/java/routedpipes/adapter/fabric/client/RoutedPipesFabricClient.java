package routedpipes.adapter.fabric.client;

import java.util.List;

import routedpipes.adapter.minecraft.FluidContents;
import routedpipes.adapter.minecraft.GuideBookItem;
import routedpipes.adapter.minecraft.ShownHudPanels;
import routedpipes.adapter.minecraft.SignCreatorItem;
import routedpipes.adapter.minecraft.client.ChannelScreen;
import routedpipes.adapter.minecraft.client.ChassisScreen;
import routedpipes.adapter.minecraft.client.ClientMenuLink;
import routedpipes.adapter.minecraft.client.CraftingTableScreen;
import routedpipes.adapter.minecraft.client.DiskScreen;
import routedpipes.adapter.minecraft.client.FirewallScreen;
import routedpipes.adapter.minecraft.client.FluidConfigScreen;
import routedpipes.adapter.minecraft.client.FluidRequestScreen;
import routedpipes.adapter.minecraft.client.FrequencyCardScreen;
import routedpipes.adapter.minecraft.client.GuideBookScreen;
import routedpipes.adapter.minecraft.client.HudRenderer;
import routedpipes.adapter.minecraft.client.HudSettingsScreen;
import routedpipes.adapter.minecraft.client.MacroEditScreen;
import routedpipes.adapter.minecraft.client.NamesScreen;
import routedpipes.adapter.minecraft.client.PipeConfigScreen;
import routedpipes.adapter.minecraft.client.PipeItemRenderer;
import routedpipes.adapter.minecraft.client.PlacementGhost;
import routedpipes.adapter.minecraft.client.ProgramCompilerScreen;
import routedpipes.adapter.minecraft.client.RemoteOrdererKey;
import routedpipes.adapter.minecraft.client.RequestScreen;
import routedpipes.adapter.minecraft.client.RequestTableScreen;
import routedpipes.adapter.minecraft.client.SecurityStationScreen;
import routedpipes.adapter.minecraft.client.SignItemScreen;
import routedpipes.adapter.minecraft.client.StatisticsTableScreen;
import routedpipes.adapter.minecraft.client.TubeItemRenderer;
import routedpipes.adapter.minecraft.client.UpgradeScreen;
import routedpipes.adapter.minecraft.client.visualtest.VisualTest;
import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.minecraft.network.AskIngredientsPayload;
import routedpipes.adapter.minecraft.network.ChooseMarkerPayload;
import routedpipes.adapter.minecraft.network.HudPanelsPayload;
import routedpipes.adapter.minecraft.network.OpenRemoteOrdererPayload;
import routedpipes.adapter.minecraft.network.SetMarkerPayload;
import routedpipes.adapter.minecraft.network.SetMenuTextPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

// never loaded on dedicated servers
public final class RoutedPipesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerScreens();
        BlockEntityRenderers.register(Content.PIPE_BLOCK_ENTITY.get(), PipeItemRenderer::new);
        BlockEntityRenderers.register(Content.TUBE_BLOCK_ENTITY.get(), TubeItemRenderer::new);
        // the window of a fluid container shows the colour of what is in it
        ColorProviderRegistry.ITEM.register((stack, layer) -> layer == 1 && stack.get(FluidContents.TYPE) != null
                ? FluidVariantRendering.getColor(FluidVariant.of(stack.get(FluidContents.TYPE).fluid())) | 0xFF000000 : -1,
                Content.FLUID_CONTAINER.get());
        // the model of the sign creator asks for the kind of sign it makes
        ItemProperties.register(Content.SIGN_CREATOR.get(), ResourceLocation.fromNamespaceAndPath(Content.MODID, "sign_mode"),
                (stack, level, entity, seed) -> SignCreatorItem.modeOf(stack).ordinal());
        KeyBindingHelper.registerKeyBinding(RemoteOrdererKey.MAPPING);
        HudRenderCallback.EVENT.register((graphics, delta) -> HudRenderer.drawCrosshair(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(client -> RemoteOrdererKey.tick());
        // the panels are see-through and clear the depth, so they go last
        WorldRenderEvents.LAST.register(context -> HudRenderer.render(context.matrixStack(), context.camera()));
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, target) -> {
            if (target instanceof BlockHitResult hit && context.consumers() != null) {
                PlacementGhost.draw(hit, context.matrixStack(), context.consumers(), context.camera().getPosition());
            }
            return true;
        });
        ClientPlayNetworking.registerGlobalReceiver(HudPanelsPayload.TYPE, (payload, context) ->
                ShownHudPanels.apply(payload.reset(), payload.changed(), payload.gone()));
        ClientMenuLink.setSender(new ClientMenuLink.Sender() {
            @Override
            public void setMarker(int containerId, int slot, ItemStack stack) {
                ClientPlayNetworking.send(new SetMarkerPayload(containerId, slot, stack));
            }

            @Override
            public void chooseMarker(int containerId, int slot, List<ItemStack> choices) {
                ClientPlayNetworking.send(new ChooseMarkerPayload(containerId, slot, choices));
            }

            @Override
            public void setText(int containerId, int field, String text) {
                ClientPlayNetworking.send(new SetMenuTextPayload(containerId, field, text));
            }

            @Override
            public void openRemoteOrderer() {
                ClientPlayNetworking.send(new OpenRemoteOrdererPayload());
            }

            @Override
            public void askIngredients(int containerId, int question, List<List<ItemStack>> choices) {
                ClientPlayNetworking.send(new AskIngredientsPayload(containerId, question, choices));
            }
        });
        GuideBookItem.setOpener(() -> Minecraft.getInstance().setScreen(new GuideBookScreen()));
        PreparableModelLoadingPlugin.register(NeoForgeModels::load, NeoForgeModels::plugin);
        CutoutBlocks.register();
        VisualTest.registerIfRequested(tick -> ClientTickEvents.END_CLIENT_TICK.register(client -> tick.run()));
    }

    private static void registerScreens() {
        MenuScreens.register(Content.REQUEST_MENU.get(), RequestScreen<RequestMenu>::new);
        MenuScreens.register(Content.DISK_MENU.get(), DiskScreen::new);
        MenuScreens.register(Content.MACRO_EDIT_MENU.get(), MacroEditScreen::new);
        Content.CONFIG_MENUS.values().forEach(menu -> MenuScreens.register(menu.get(), PipeConfigScreen::new));
        MenuScreens.register(Content.CRAFTING_TABLE_MENU.get(), CraftingTableScreen::new);
        MenuScreens.register(Content.CHASSIS_MENU.get(), ChassisScreen::new);
        MenuScreens.register(Content.NAMES_MENU.get(), NamesScreen::new);
        MenuScreens.register(Content.REQUEST_TABLE_MENU.get(), RequestTableScreen::new);
        MenuScreens.register(Content.FIREWALL_MENU.get(), FirewallScreen::new);
        MenuScreens.register(Content.CONNECTOR_MENU.get(), ChannelScreen::new);
        MenuScreens.register(Content.CHANNEL_MENU.get(), ChannelScreen::new);
        MenuScreens.register(Content.FREQUENCY_CARD_MENU.get(), FrequencyCardScreen::new);
        MenuScreens.register(Content.SIGN_ITEM_MENU.get(), SignItemScreen::new);
        MenuScreens.register(Content.UPGRADE_MENU.get(), UpgradeScreen::new);
        MenuScreens.register(Content.SECURITY_STATION_MENU.get(), SecurityStationScreen::new);
        MenuScreens.register(Content.STATISTICS_TABLE_MENU.get(), StatisticsTableScreen::new);
        MenuScreens.register(Content.PROGRAM_COMPILER_MENU.get(), ProgramCompilerScreen::new);
        Content.FLUID_CONFIG_MENUS.values().forEach(menu -> MenuScreens.register(menu.get(), FluidConfigScreen::new));
        MenuScreens.register(Content.FLUID_REQUEST_MENU.get(), FluidRequestScreen::new);
        MenuScreens.register(Content.HUD_SETTINGS_MENU.get(), HudSettingsScreen::new);
    }
}
