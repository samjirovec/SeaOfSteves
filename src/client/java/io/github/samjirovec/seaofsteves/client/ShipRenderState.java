package io.github.samjirovec.seaofsteves.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;

public class ShipRenderState extends EntityRenderState {
	public float relativeYaw;
	public float pitch;
	public float roll;
	public float pivotY;
	public float baseYaw;
	public int count;
	public float trim;
	/** How far the sails are let down, 0 (furled at the yard) to 1. */
	public float canvas;
	public final List<BlockPos> offsets = new ArrayList<>();
	/** For each rendered block, the index of the rig whose sail it belongs to, or -1. */
	public final List<Integer> rigOf = new ArrayList<>();
	/** Per rig: mast x, mast z and the top edge of the sail, in ship space. */
	public final List<float[]> rigs = new ArrayList<>();
	public final List<MovingBlockRenderState> blocks = new ArrayList<>();

	MovingBlockRenderState slot(int index) {
		while (blocks.size() <= index) {
			blocks.add(new MovingBlockRenderState());
			offsets.add(BlockPos.ZERO);
			rigOf.add(-1);
		}
		return blocks.get(index);
	}
}
