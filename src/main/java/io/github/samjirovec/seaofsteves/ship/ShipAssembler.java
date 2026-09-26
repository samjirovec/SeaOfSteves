package io.github.samjirovec.seaofsteves.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.samjirovec.seaofsteves.SeaOfSteves;
import io.github.samjirovec.seaofsteves.block.ShipWheelBlock;
import io.github.samjirovec.seaofsteves.registry.ModBlocks;
import io.github.samjirovec.seaofsteves.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Turns a floating structure of blocks into a {@link ShipEntity}. */
public final class ShipAssembler {
	/** Structures bigger than this are almost certainly connected to the sea floor or shore. */
	public static final int MAX_BLOCKS = 4096;

	static final int SILENT_MOVE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	private ShipAssembler() {
	}

	public static void tryAssemble(ServerLevel level, BlockPos helmPos, Player player) {
		if (player.isPassenger()) return;

		BlockState helmState = level.getBlockState(helmPos);
		if (!(helmState.getBlock() instanceof ShipWheelBlock)) return;

		// 1. Flood fill every block connected to the wheel.
		Set<BlockPos> found = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		found.add(helmPos);
		queue.add(helmPos);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (found.contains(next) || !isShipMaterial(level.getBlockState(next))) continue;
				if (isLand(level.getBlockState(next))) {
					fail(player, Component.translatable("message.seaofsteves.touching_land", next.getX(), next.getY(), next.getZ()));
					return;
				}
				found.add(next);
				if (found.size() > MAX_BLOCKS) {
					fail(player, Component.translatable("message.seaofsteves.too_big", MAX_BLOCKS));
					return;
				}
				queue.add(next);
			}
		}

		// 2. Needs a sail, and nothing we can't carry yet.
		int sails = 0;
		for (BlockPos pos : found) {
			BlockState state = level.getBlockState(pos);
			if (state.is(ModBlocks.SAIL)) sails++;
			if (state.hasBlockEntity()) {
				fail(player, Component.translatable("message.seaofsteves.block_entity", state.getBlock().getName()));
				return;
			}
		}
		if (sails == 0) {
			fail(player, Component.translatable("message.seaofsteves.no_sail"));
			return;
		}

		// 3. Must be floating on water.
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : found) {
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
		}
		int waterSurface = findWaterSurface(level, found, minX, minY, minZ, maxX, maxY, maxZ);
		if (waterSurface == Integer.MIN_VALUE) {
			fail(player, Component.translatable("message.seaofsteves.not_on_water"));
			return;
		}

		// 4. Capture blocks relative to the pivot (centre of the footprint, bottom layer).
		BlockPos pivot = new BlockPos(Math.floorDiv(minX + maxX + 1, 2), minY, Math.floorDiv(minZ + maxZ + 1, 2));
		List<ShipStructure.ShipBlock> blocks = new ArrayList<>(found.size());
		for (BlockPos p : found) {
			BlockState state = level.getBlockState(p);
			blocks.add(new ShipStructure.ShipBlock(p.subtract(pivot), state));
		}
		Direction facing = helmState.getValue(ShipWheelBlock.FACING);
		float heading = facing.toYRot();
		ShipStructure structure = new ShipStructure(blocks, helmPos.subtract(pivot), heading, waterSurface - minY);

		// 5. Remove the blocks: fragile attachments first so nothing pops off as an item.
		List<BlockPos> order = new ArrayList<>(found);
		order.sort((a, b) -> Boolean.compare(isSturdy(level, a), isSturdy(level, b)));
		for (BlockPos p : order) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), SILENT_MOVE);
		}
		// Let the sea back in where the hull used to be.
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= waterSurface; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		for (BlockPos p : found) {
			level.updateNeighborsAt(p, Blocks.AIR);
		}

		ShipEntity ship = new ShipEntity(ModEntities.SHIP, level);
		ship.setStructure(structure);
		ship.snapTo(pivot.getX() + 0.5, pivot.getY(), pivot.getZ() + 0.5, heading, 0f);
		level.addFreshEntity(ship);
		player.startRiding(ship);

		level.playSound(null, helmPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
		player.sendOverlayMessage(Component.translatable("message.seaofsteves.assembled", found.size(), sails));
	}

	private static boolean isSturdy(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
	}

	/** Blocks that count as part of a ship. Water, air, seagrass and similar are ignored. */
	static boolean isShipMaterial(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
		if (state.canBeReplaced()) return false;
		return !state.is(Blocks.KELP) && !state.is(Blocks.KELP_PLANT) && !state.is(Blocks.BUBBLE_COLUMN);
	}

	/** Natural terrain: if the structure touches this it's attached to land or the sea floor. */
	static boolean isLand(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.BASE_STONE_NETHER) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
				|| state.is(Blocks.BEDROCK) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
				|| state.is(Blocks.MUD) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE);
	}

	/**
	 * Returns the y of the highest water around or directly under the structure, or
	 * {@link Integer#MIN_VALUE} if it isn't sitting on water.
	 */
	private static int findWaterSurface(ServerLevel level, Set<BlockPos> found, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int surface = Integer.MIN_VALUE;
		// Water in a ring around the hull.
		for (int y = minY - 1; y <= maxY; y++) {
			for (int x = minX - 1; x <= maxX + 1; x++) {
				for (int z = minZ - 1; z <= maxZ + 1; z++) {
					boolean edge = x == minX - 1 || x == maxX + 1 || z == minZ - 1 || z == maxZ + 1;
					if (!edge && y >= minY) continue;
					BlockPos p = new BlockPos(x, y, z);
					if (!found.contains(p) && level.getFluidState(p).is(FluidTags.WATER)) surface = Math.max(surface, y);
				}
			}
		}
		if (surface == Integer.MIN_VALUE) return surface;

		// And most of the bottom of the hull must actually be over water.
		int bottom = 0, wet = 0;
		for (BlockPos p : found) {
			if (found.contains(p.below())) continue;
			bottom++;
			if (level.getFluidState(p.below()).is(FluidTags.WATER) || level.getFluidState(p).is(FluidTags.WATER)) wet++;
		}
		return wet * 2 >= bottom ? surface : Integer.MIN_VALUE;
	}

	private static void fail(Player player, Component message) {
		SeaOfSteves.LOGGER.debug("Ship assembly refused for {}: {}", player.getName().getString(), message.getString());
		player.sendOverlayMessage(message);
	}
}
