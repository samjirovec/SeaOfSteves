package io.github.samjirovec.seaofsteves.client;

import io.github.samjirovec.seaofsteves.SeaOfSteves;
import io.github.samjirovec.seaofsteves.registry.ModEntities;
import io.github.samjirovec.seaofsteves.ship.ShipCollisions;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class SeaOfStevesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.SHIP, ShipRenderer::new);
		ShipControls.init();

		// Players move themselves on their own client, so the client carries the local player
		// along with any ship they're standing on, after every entity (ships included) has ticked.
		ClientTickEvents.END_LEVEL_TICK.register(level -> {
			LocalPlayer player = Minecraft.getInstance().player;
			for (ShipEntity ship : ShipCollisions.shipsIn(level)) {
				if (player != null && player.level() == level) ship.carry(player);
				ship.finishCarrying();
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SeaOfSteves.id("sailing_hud"), new SailingHud());
	}
}
