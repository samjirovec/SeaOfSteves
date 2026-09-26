package io.github.samjirovec.seaofsteves.network;

import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ShipControlPayload.TYPE, ShipControlPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AnchorPayload.TYPE, AnchorPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShipControlPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof ShipEntity ship && ship.isCaptain(context.player())) {
				ship.setTrimInput(payload.trim());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(AnchorPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof ShipEntity ship && ship.isCaptain(context.player())) {
				ship.tryDisassemble(context.player());
			}
		});
	}
}
