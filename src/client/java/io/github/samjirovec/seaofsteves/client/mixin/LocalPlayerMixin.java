package io.github.samjirovec.seaofsteves.client.mixin;

import io.github.samjirovec.seaofsteves.ship.ShipCollisions;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Players move themselves on their own client, so the client carries the local player along
 * with any ship deck they stand on. Doing it right before the player's own movement means the
 * player always walks against the deck's latest position.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void seaofsteves$rideShipDecks(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		for (ShipEntity ship : ShipCollisions.shipsIn(self.level())) {
			ship.carry(self);
			ship.finishCarrying();
		}
	}
}
