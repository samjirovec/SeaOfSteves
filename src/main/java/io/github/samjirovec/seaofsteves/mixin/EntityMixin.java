package io.github.samjirovec.seaofsteves.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.samjirovec.seaofsteves.ship.ShipCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds ship hulls to the collision shapes entities move against (walking, falling, stepping up). */
@Mixin(Entity.class)
abstract class EntityMixin {
	@WrapOperation(method = "collide", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<VoxelShape> seaofsteves$addShipHulls(Level level, Entity entity, AABB area, Operation<List<VoxelShape>> original) {
		return ShipCollisions.withShipHulls(level, entity, area, original.call(level, entity, area));
	}

	@WrapOperation(method = "collectAllColliders", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private static List<VoxelShape> seaofsteves$addShipHullsStatic(Level level, Entity entity, AABB area, Operation<List<VoxelShape>> original) {
		return ShipCollisions.withShipHulls(level, entity, area, original.call(level, entity, area));
	}
}
