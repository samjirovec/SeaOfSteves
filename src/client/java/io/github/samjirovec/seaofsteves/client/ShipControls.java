package io.github.samjirovec.seaofsteves.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.samjirovec.seaofsteves.SeaOfSteves;
import io.github.samjirovec.seaofsteves.network.AnchorPayload;
import io.github.samjirovec.seaofsteves.network.ShipControlPayload;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLScancode;

/** Extra keys for sailing: trimming the sails and dropping anchor. W/S/A/D come from vanilla movement input. */
public final class ShipControls {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SeaOfSteves.id("sailing"));

	public static final KeyMapping TRIM_PORT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.seaofsteves.trim_port", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_LEFT, CATEGORY));
	public static final KeyMapping TRIM_STARBOARD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.seaofsteves.trim_starboard", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_RIGHT, CATEGORY));
	public static final KeyMapping ANCHOR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.seaofsteves.anchor", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_R, CATEGORY));

	private static int lastTrim;

	private ShipControls() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ShipControls::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null || !(client.player.getVehicle() instanceof ShipEntity ship) || !ship.isCaptain(client.player)) {
			lastTrim = 0;
			while (ANCHOR.consumeClick()) {
				// Swallow presses while not sailing.
			}
			return;
		}

		int trim = (TRIM_STARBOARD.isDown() ? 1 : 0) - (TRIM_PORT.isDown() ? 1 : 0);
		if (trim != lastTrim) {
			ClientPlayNetworking.send(new ShipControlPayload(trim));
			lastTrim = trim;
		}

		while (ANCHOR.consumeClick()) {
			ClientPlayNetworking.send(AnchorPayload.INSTANCE);
		}
	}
}
