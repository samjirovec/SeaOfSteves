package io.github.samjirovec.seaofsteves.ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.samjirovec.seaofsteves.physics.ShipStats;
import io.github.samjirovec.seaofsteves.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks that make up a ship, stored relative to the ship's pivot.
 *
 * <p>A block at relative position {@code r} has its centre at
 * {@code entityPos + rotate(r.x, r.z) + (0, r.y + 0.5, 0)}; the entity sits at the centre of the
 * bottom face of the pivot block. Block states are stored in the orientation they had when the
 * ship was assembled, when the ship's heading was {@link #baseYaw}.
 *
 * @param blocks    every block of the ship
 * @param helm      relative position of the ship's wheel
 * @param baseYaw   heading (yaw) of the ship when it was assembled
 * @param waterline relative y of the water surface the hull floats on
 */
public record ShipStructure(List<ShipBlock> blocks, BlockPos helm, float baseYaw, int waterline) {
	public static final ShipStructure EMPTY = new ShipStructure(List.of(), BlockPos.ZERO, 0f, 0);

	public record ShipBlock(BlockPos pos, BlockState state) {
		public static final Codec<ShipBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(ShipBlock::pos),
				BlockState.CODEC.fieldOf("state").forGetter(ShipBlock::state)
		).apply(i, ShipBlock::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, ShipBlock> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, ShipBlock::pos,
				ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), ShipBlock::state,
				ShipBlock::new);
	}

	public static final Codec<ShipStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
			ShipBlock.CODEC.listOf().fieldOf("blocks").forGetter(ShipStructure::blocks),
			BlockPos.CODEC.fieldOf("helm").forGetter(ShipStructure::helm),
			Codec.FLOAT.fieldOf("base_yaw").forGetter(ShipStructure::baseYaw),
			Codec.INT.fieldOf("waterline").forGetter(ShipStructure::waterline)
	).apply(i, ShipStructure::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShipStructure> STREAM_CODEC = StreamCodec.composite(
			ShipBlock.STREAM_CODEC.apply(ByteBufCodecs.list()), ShipStructure::blocks,
			BlockPos.STREAM_CODEC, ShipStructure::helm,
			ByteBufCodecs.FLOAT, ShipStructure::baseYaw,
			ByteBufCodecs.VAR_INT, ShipStructure::waterline,
			ShipStructure::new);

	public static final EntityDataSerializer<ShipStructure> SERIALIZER = EntityDataSerializer.forValueType(STREAM_CODEC);

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	/** Relative position -> block state. */
	public java.util.Map<BlockPos, BlockState> asMap() {
		java.util.Map<BlockPos, BlockState> map = new java.util.HashMap<>(blocks.size() * 2);
		for (ShipBlock b : blocks) map.put(b.pos(), b.state());
		return map;
	}

	public ShipStats stats() {
		int sails = 0;
		double mass = 0;
		for (ShipBlock b : blocks) {
			if (b.state().is(ModBlocks.SAIL)) sails++;
			mass += BlockWeights.weightOf(b.state());
		}
		return ShipStats.of(blocks.size(), sails, mass);
	}

	/** Blocks with nothing of the ship directly beneath them: the parts of the hull touching water. */
	public List<BlockPos> hullBottom() {
		Set<BlockPos> all = new HashSet<>();
		for (ShipBlock b : blocks) all.add(b.pos());
		List<BlockPos> bottom = new ArrayList<>();
		for (ShipBlock b : blocks) {
			if (!all.contains(b.pos().below())) bottom.add(b.pos());
		}
		return bottom;
	}

	/** Horizontal radius (from the pivot) that contains every block, for bounding boxes. */
	public double horizontalRadius() {
		double r = 0;
		for (ShipBlock b : blocks) {
			double dx = Math.abs(b.pos().getX()) + 0.5, dz = Math.abs(b.pos().getZ()) + 0.5;
			r = Math.max(r, Math.sqrt(dx * dx + dz * dz));
		}
		return r;
	}

	public int height() {
		int max = 0;
		for (ShipBlock b : blocks) max = Math.max(max, b.pos().getY() + 1);
		return max;
	}

	/** Where passengers stand: the driver behind the wheel, then open deck spots nearest the middle. */
	public List<BlockPos> seats(net.minecraft.core.Direction helmFacing) {
		Set<BlockPos> all = new HashSet<>();
		for (ShipBlock b : blocks) all.add(b.pos());
		List<BlockPos> seats = new ArrayList<>();
		seats.add(helm.relative(helmFacing.getOpposite()));
		blocks.stream()
				.map(ShipBlock::pos)
				.filter(p -> !all.contains(p.above()) && !all.contains(p.above(2)))
				.map(BlockPos::above)
				.filter(p -> !p.equals(seats.getFirst()) && !p.equals(helm))
				.sorted(Comparator.comparingInt(p -> p.getX() * p.getX() + p.getZ() * p.getZ()))
				.limit(7)
				.forEach(seats::add);
		return seats;
	}
}
