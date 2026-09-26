package io.github.samjirovec.seaofsteves.registry;

import java.util.function.Function;

import io.github.samjirovec.seaofsteves.SeaOfSteves;
import io.github.samjirovec.seaofsteves.block.SailBlock;
import io.github.samjirovec.seaofsteves.block.ShipWheelBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block SAIL = register("sail", SailBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOL)
			.strength(0.8f)
			.sound(SoundType.WOOL)
			.ignitedByLava());

	public static final Block SHIP_WHEEL = register("ship_wheel", ShipWheelBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.strength(2.0f, 3.0f)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.ignitedByLava());

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, SeaOfSteves.id(name));
		Block block = factory.apply(properties.setId(blockKey));
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, SeaOfSteves.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
	}
}
