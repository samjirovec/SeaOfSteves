package io.github.samjirovec.seaofsteves.block;

import net.minecraft.world.level.block.Block;

/**
 * A sheet of canvas. Every sail block in a ship adds sail area; the more sail per unit of
 * weight, the faster the ship accelerates and the higher its top speed.
 */
public class SailBlock extends Block {
	public SailBlock(Properties properties) {
		super(properties);
	}
}
