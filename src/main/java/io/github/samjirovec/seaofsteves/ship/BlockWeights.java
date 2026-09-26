package io.github.samjirovec.seaofsteves.ship;

import io.github.samjirovec.seaofsteves.registry.ModBlocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** Rough weight of each block, used to work out how hard the sails have to work. */
public final class BlockWeights {
	private BlockWeights() {
	}

	public static double weightOf(BlockState state) {
		if (state.is(ModBlocks.SAIL)) return 0.3;
		SoundType sound = state.getSoundType();
		if (sound == SoundType.WOOL) return 0.3;
		if (sound == SoundType.WOOD || sound == SoundType.CHERRY_WOOD || sound == SoundType.BAMBOO_WOOD
				|| sound == SoundType.NETHER_WOOD || sound == SoundType.LADDER || sound == SoundType.SCAFFOLDING) return 1.0;
		if (sound == SoundType.GLASS) return 0.8;
		if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.CHAIN || sound == SoundType.COPPER) return 4.0;
		if (sound == SoundType.NETHERITE_BLOCK) return 6.0;
		if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.DEEPSLATE_BRICKS
				|| sound == SoundType.POLISHED_DEEPSLATE || sound == SoundType.NETHER_BRICKS) return 2.5;
		return 1.2;
	}
}
