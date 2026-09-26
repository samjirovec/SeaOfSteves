package io.github.samjirovec.seaofsteves.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;

public class ShipRenderState extends EntityRenderState {
	public float relativeYaw;
	public float bob;
	public int count;
	public final List<BlockPos> offsets = new ArrayList<>();
	public final List<MovingBlockRenderState> blocks = new ArrayList<>();

	MovingBlockRenderState slot(int index) {
		while (blocks.size() <= index) {
			blocks.add(new MovingBlockRenderState());
			offsets.add(BlockPos.ZERO);
		}
		return blocks.get(index);
	}
}
