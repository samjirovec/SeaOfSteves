package io.github.samjirovec.seaofsteves.mixin;

import io.github.samjirovec.seaofsteves.ship.ShipCollisions;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The server kicks players who float with no blocks around them for too long ("flying is not
 * enabled"). It only looks at world blocks, so a player standing on a ship's deck counts as
 * standing on something.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
	@Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
	private void seaofsteves$standingOnShip(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ShipCollisions.isNearHull(entity, 1.5)) cir.setReturnValue(false);
	}
}
