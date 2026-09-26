package io.github.samjirovec.seaofsteves.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.samjirovec.seaofsteves.block.SailBlock;
import io.github.samjirovec.seaofsteves.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds a ship's rigs. A rig is a mast (a vertical stack of at least {@link #MIN_MAST_HEIGHT}
 * fences) with a sail on top of it: a flat, filled rectangle of sail blocks whose bottom row sits
 * directly on the top fence. Sails swing around their mast when trimmed.
 */
public final class ShipRigging {
	public static final int MIN_MAST_HEIGHT = 2;

	/**
	 * @param mastTop  the top fence of the mast
	 * @param mastBase the bottom fence of the mast
	 * @param sails    every sail block of the rectangle
	 * @param sailTop  y of the top row of sail blocks
	 */
	public record Rig(BlockPos mastTop, BlockPos mastBase, List<BlockPos> sails, int sailTop) {
		public int mastHeight() {
			return mastTop.getY() - mastBase.getY() + 1;
		}
	}

	/** The rigs found, plus any sail blocks that aren't part of a proper rig. */
	public record Result(List<Rig> rigs, List<BlockPos> looseSails, String problem) {
		public boolean valid() {
			return looseSails.isEmpty();
		}
	}

	private ShipRigging() {
	}

	public static boolean isMastBlock(BlockState state) {
		return state != null && state.is(BlockTags.FENCES);
	}

	public static Result find(Map<BlockPos, BlockState> blocks) {
		List<Rig> rigs = new ArrayList<>();
		Set<BlockPos> rigged = new HashSet<>();
		String problem = null;

		for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
			BlockPos pos = entry.getKey();
			if (!isMastBlock(entry.getValue()) || isMastBlock(blocks.get(pos.above()))) continue;
			// `pos` is the top of a fence stack.
			BlockState above = blocks.get(pos.above());
			if (above == null || !above.is(ModBlocks.SAIL) || rigged.contains(pos.above())) continue;

			BlockPos base = pos;
			while (isMastBlock(blocks.get(base.below()))) base = base.below();
			if (pos.getY() - base.getY() + 1 < MIN_MAST_HEIGHT) {
				problem = "mast_too_short";
				continue;
			}

			Set<BlockPos> sails = connectedSails(blocks, pos.above());
			String shape = checkRectangle(sails, pos, blocks);
			if (shape != null) {
				problem = shape;
				continue;
			}
			int top = sails.stream().mapToInt(BlockPos::getY).max().orElse(pos.getY() + 1);
			rigs.add(new Rig(pos, base, List.copyOf(sails), top));
			rigged.addAll(sails);
		}

		List<BlockPos> loose = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
			if (entry.getValue().is(ModBlocks.SAIL) && !rigged.contains(entry.getKey())) loose.add(entry.getKey());
		}
		if (!loose.isEmpty() && problem == null) problem = "no_mast";
		return new Result(rigs, loose, problem);
	}

	private static Set<BlockPos> connectedSails(Map<BlockPos, BlockState> blocks, BlockPos start) {
		Set<BlockPos> found = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		found.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				BlockState s = blocks.get(n);
				if (s != null && s.is(ModBlocks.SAIL) && found.add(n)) queue.add(n);
			}
		}
		return found;
	}

	/** Returns null if the sails form a flat filled rectangle resting on the mast, else a problem key. */
	private static String checkRectangle(Set<BlockPos> sails, BlockPos mastTop, Map<BlockPos, BlockState> blocks) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : sails) {
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
		}
		if (minX != maxX && minZ != maxZ) return "sail_not_flat";
		long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		if (volume != sails.size()) return "sail_not_rectangle";
		if (minY != mastTop.getY() + 1) return "sail_not_on_mast";
		return null;
	}

	/** The horizontal axis a rig's canvas spans, from its layout (or the block's own facing for a 1-wide sail). */
	public static Direction.Axis spanAxis(Rig rig, Map<BlockPos, BlockState> blocks) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : rig.sails()) {
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
		}
		if (maxX > minX) return Direction.Axis.X;
		if (maxZ > minZ) return Direction.Axis.Z;
		BlockState state = blocks.get(rig.sails().getFirst());
		return state != null && state.hasProperty(SailBlock.AXIS) ? state.getValue(SailBlock.AXIS) : Direction.Axis.X;
	}
}
