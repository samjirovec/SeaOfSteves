package io.github.samjirovec.seaofsteves;

import io.github.samjirovec.seaofsteves.command.SosCommand;
import io.github.samjirovec.seaofsteves.network.ModNetworking;
import io.github.samjirovec.seaofsteves.registry.ModBlocks;
import io.github.samjirovec.seaofsteves.registry.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeaOfSteves implements ModInitializer {
	public static final String MOD_ID = "seaofsteves";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModEntities.init();
		ModNetworking.init();
		SosCommand.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(ModBlocks.SHIP_WHEEL);
			output.accept(ModBlocks.SAIL);
		});

		LOGGER.info("Sea of Steves: hoist the sails!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
