package io.github.samjirovec.seaofsteves.client;

import io.github.samjirovec.seaofsteves.SeaOfSteves;
import io.github.samjirovec.seaofsteves.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class SeaOfStevesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SHIP, ShipRenderer::new);
		ShipControls.init();
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SeaOfSteves.id("sailing_hud"), new SailingHud());
	}
}
