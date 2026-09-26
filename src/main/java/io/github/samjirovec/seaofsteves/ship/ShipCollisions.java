package io.github.samjirovec.seaofsteves.ship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Makes ship hulls solid. Every entity movement asks the level for nearby entity collision
 * shapes; a mixin routes that through {@link #withShipHulls} so ship blocks are added.
 *
 * <p>Loaded ships are tracked per side (client and integrated server run on different threads,
 * each only touches its own set).
 */
public final class ShipCollisions {
	private static final Set<ShipEntity> CLIENT_SHIPS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<ShipEntity> SERVER_SHIPS = Collections.newSetFromMap(new WeakHashMap<>());

	private ShipCollisions() {
	}

	static void track(ShipEntity ship) {
		(ship.level().isClientSide() ? CLIENT_SHIPS : SERVER_SHIPS).add(ship);
	}

	/** Loaded, live ships in a level. */
	public static List<ShipEntity> shipsIn(Level level) {
		Set<ShipEntity> ships = level.isClientSide() ? CLIENT_SHIPS : SERVER_SHIPS;
		if (ships.isEmpty()) return List.of();
		List<ShipEntity> result = new ArrayList<>(ships.size());
		for (Iterator<ShipEntity> it = ships.iterator(); it.hasNext(); ) {
			ShipEntity ship = it.next();
			if (ship.isRemoved()) {
				it.remove();
			} else if (ship.level() == level) {
				result.add(ship);
			}
		}
		return result;
	}

	public static List<VoxelShape> withShipHulls(Level level, Entity entity, AABB area, List<VoxelShape> shapes) {
		if (entity instanceof ShipEntity) return shapes;
		if (entity != null && entity.getVehicle() instanceof ShipEntity) return shapes;
		// Players move themselves on their own client; the server takes their word for it.
		// Adding hulls on the server would make it disagree with a client that sees the ship a
		// few ticks behind, and snap the player back.
		if (entity instanceof Player && !level.isClientSide()) return shapes;

		List<VoxelShape> result = null;
		for (ShipEntity ship : shipsIn(level)) {
			if (!ship.getHullBounds().intersects(area)) continue;
			for (AABB box : ship.getHullBoxes()) {
				if (box.intersects(area)) {
					if (result == null) result = new ArrayList<>(shapes);
					result.add(Shapes.create(box));
				}
			}
		}
		return result == null ? shapes : result;
	}

	/** Whether an entity is on or right next to any ship's hull (with some slack for network lag). */
	public static boolean isNearHull(Entity entity, double slack) {
		AABB area = entity.getBoundingBox().inflate(slack, 0, slack).expandTowards(0, -slack - 0.6, 0);
		for (ShipEntity ship : shipsIn(entity.level())) {
			if (!ship.getHullBounds().intersects(area)) continue;
			for (AABB box : ship.getHullBoxes()) {
				if (box.intersects(area)) return true;
			}
		}
		return false;
	}
}
