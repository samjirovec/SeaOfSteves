package io.github.samjirovec.seaofsteves.registry;

import io.github.samjirovec.seaofsteves.SeaOfSteves;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import io.github.samjirovec.seaofsteves.ship.ShipStructure;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> SHIP_KEY = ResourceKey.create(Registries.ENTITY_TYPE, SeaOfSteves.id("ship"));

	public static final EntityType<ShipEntity> SHIP = EntityType.Builder.<ShipEntity>of(ShipEntity::new, MobCategory.MISC)
			.sized(1.0f, 1.0f)
			.clientTrackingRange(16)
			.updateInterval(1)
			.fireImmune()
			.build(SHIP_KEY);

	private ModEntities() {
	}

	public static void init() {
		FabricEntityDataRegistry.register(SeaOfSteves.id("ship_structure"), ShipStructure.SERIALIZER);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, SHIP_KEY, SHIP);
	}
}
