package com.molox.pointyourdestination;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.ResourceLocation;

@Mod(value = PointYourDestination.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PointYourDestination.MODID, value = Dist.CLIENT)
public class PointYourDestinationClient {
    public PointYourDestinationClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.register(new SpyglassWaypointHandler());
        NeoForge.EVENT_BUS.register(new ScrollZoomHandler());
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(PointYourDestination.MODID, "crosshair_animation"),
                CrosshairAnimationRenderer::render
        );
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeybinds.MARK_WAYPOINT);
        event.register(ModKeybinds.AUTO_ZOOM);
    }
}