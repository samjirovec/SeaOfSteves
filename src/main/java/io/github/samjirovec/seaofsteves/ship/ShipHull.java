package io.github.samjirovec.seaofsteves.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Collision boxes for a ship's blocks. Boxes are stored in ship space (relative to the pivot, as
 * built) and turned into world-space boxes for the ship's current position and heading.
 *
 * <p>Minecraft collision boxes can't rotate, so at headings that aren't a multiple of 90 degrees
 * each box is replaced by the axis-aligned box around its rotated footprint (up to ~0.2 blocks
 * wider at 45 degrees). That keeps decks gap-free to walk on.
 */
public final class ShipHull {
	private final List<AABB> localBoxes;

	private double cachedX = Double.NaN, cachedY, cachedZ, cachedYaw;
	private List<AABB> worldBoxes = List.of();
	private AABB worldBounds = new AABB(0, 0, 0, 0, 0, 0);

	public ShipHull(ShipStructure structure) {
		List<AABB> boxes = new ArrayList<>();
		for (ShipStructure.ShipBlock block : structure.blocks()) {
			BlockPos r = block.pos();
			for (AABB box : block.state().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
				boxes.add(box.move(r.getX() - 0.5, r.getY(), r.getZ() - 0.5));
			}
		}
		this.localBoxes = List.copyOf(boxes);
	}

	public boolean isEmpty() {
		return localBoxes.isEmpty();
	}

	/** World-space collision boxes for the ship at {@code pos}, rotated {@code relYaw} degrees from how it was built. */
	public List<AABB> worldBoxes(Vec3 pos, double relYaw) {
		if (pos.x != cachedX || pos.y != cachedY || pos.z != cachedZ || relYaw != cachedYaw) {
			recompute(pos, relYaw);
		}
		return worldBoxes;
	}

	public AABB worldBounds(Vec3 pos, double relYaw) {
		worldBoxes(pos, relYaw);
		return worldBounds;
	}

	private void recompute(Vec3 pos, double relYaw) {
		double rad = Math.toRadians(relYaw);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		// Snap tiny rounding errors at right angles so boxes line up exactly.
		if (Math.abs(cos) < 1e-9) cos = 0;
		if (Math.abs(sin) < 1e-9) sin = 0;

		List<AABB> out = new ArrayList<>(localBoxes.size());
		double bMinX = Double.MAX_VALUE, bMinY = Double.MAX_VALUE, bMinZ = Double.MAX_VALUE;
		double bMaxX = -Double.MAX_VALUE, bMaxY = -Double.MAX_VALUE, bMaxZ = -Double.MAX_VALUE;
		for (AABB b : localBoxes) {
			double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (int i = 0; i < 4; i++) {
				double x = (i & 1) == 0 ? b.minX : b.maxX;
				double z = (i & 2) == 0 ? b.minZ : b.maxZ;
				double wx = x * cos - z * sin, wz = x * sin + z * cos;
				minX = Math.min(minX, wx);
				maxX = Math.max(maxX, wx);
				minZ = Math.min(minZ, wz);
				maxZ = Math.max(maxZ, wz);
			}
			AABB world = new AABB(pos.x + minX, pos.y + b.minY, pos.z + minZ, pos.x + maxX, pos.y + b.maxY, pos.z + maxZ);
			out.add(world);
			bMinX = Math.min(bMinX, world.minX); bMinY = Math.min(bMinY, world.minY); bMinZ = Math.min(bMinZ, world.minZ);
			bMaxX = Math.max(bMaxX, world.maxX); bMaxY = Math.max(bMaxY, world.maxY); bMaxZ = Math.max(bMaxZ, world.maxZ);
		}
		worldBoxes = out;
		worldBounds = out.isEmpty() ? new AABB(pos, pos) : new AABB(bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ);
		cachedX = pos.x;
		cachedY = pos.y;
		cachedZ = pos.z;
		cachedYaw = relYaw;
	}

	/** Whether an entity with this bounding box is standing on (or just above) one of the boxes. */
	public static boolean standsOn(AABB entity, List<AABB> boxes, double tolerance) {
		for (AABB b : boxes) {
			if (b.maxX > entity.minX + 1e-3 && b.minX < entity.maxX - 1e-3
					&& b.maxZ > entity.minZ + 1e-3 && b.minZ < entity.maxZ - 1e-3
					&& b.maxY >= entity.minY - tolerance && b.maxY <= entity.minY + 0.35) {
				return true;
			}
		}
		return false;
	}

	public static boolean intersectsAny(AABB entity, List<AABB> boxes) {
		for (AABB b : boxes) {
			if (b.intersects(entity)) return true;
		}
		return false;
	}
}
