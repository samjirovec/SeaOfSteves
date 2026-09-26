package io.github.samjirovec.seaofsteves.ship;

import java.util.ArrayList;
import java.util.List;

import io.github.samjirovec.seaofsteves.block.ShipWheelBlock;
import io.github.samjirovec.seaofsteves.physics.SailPhysics;
import io.github.samjirovec.seaofsteves.physics.ShipStats;
import io.github.samjirovec.seaofsteves.physics.WaveField;
import io.github.samjirovec.seaofsteves.physics.WindField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A sailing ship: a set of blocks lifted out of the world that moves as one entity.
 *
 * <p>The server simulates the ship; clients render it and show the HUD from the synced data.
 * The ship's blocks are solid (see {@link ShipCollisions}), so players and mobs can walk its
 * decks while it sails; anything standing on it is carried along. The captain rides at the
 * wheel. The entity's own bounding box is just the wheel, which is what you right-click to take
 * the helm.
 */
public class ShipEntity extends Entity {
	private static final EntityDataAccessor<ShipStructure> DATA_STRUCTURE = SynchedEntityData.defineId(ShipEntity.class, ShipStructure.SERIALIZER);
	private static final EntityDataAccessor<Float> DATA_SAIL_DEPLOY = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SAIL_TRIM = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_WIND_DIR = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_WIND_STRENGTH = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_AGROUND = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);

	private static final float SAIL_RATE = 0.02f;
	private static final float TRIM_RATE = 2.5f;
	/** Visual pitch/roll limit. Collision stays level, so keep this small enough to walk on. */
	private static final float MAX_TILT = 4f;

	private ShipStats stats = ShipStats.EMPTY;
	private ShipHull hull = new ShipHull(ShipStructure.EMPTY);
	private List<BlockPos> hullBottom = List.of();
	private List<BlockPos> seats = List.of();
	private double radius = 0.5;
	private double halfLength = 0.5, halfBeam = 0.5;

	private int trimInput;
	private float lastYaw;

	/** Resting height of the pivot on calm water; waves move the ship above and below it. */
	private double baseY = Double.NaN;
	private double heave, heaveVel, pitch, pitchVel, roll, rollVel;
	private float prevPitch, prevRoll;

	// Where the ship was at the previous carry pass; whatever stands on deck is moved by the
	// difference. (On clients the ship is moved by interpolation outside its own tick, so the
	// start-of-tick position isn't a reliable reference.)
	private Vec3 carryFromPos;
	private float carryFromYaw;
	private List<AABB> carryFromBoxes = List.of();

	public ShipEntity(EntityType<? extends ShipEntity> type, Level level) {
		super(type, level);
		this.lastYaw = getYRot();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_STRUCTURE, ShipStructure.EMPTY);
		builder.define(DATA_SAIL_DEPLOY, 0f);
		builder.define(DATA_SAIL_TRIM, 0f);
		builder.define(DATA_WIND_DIR, 0f);
		builder.define(DATA_WIND_STRENGTH, 0f);
		builder.define(DATA_SPEED, 0f);
		builder.define(DATA_AGROUND, false);
		builder.define(DATA_PITCH, 0f);
		builder.define(DATA_ROLL, 0f);
	}

	// ---------------------------------------------------------------------------------------------
	// Structure and hull

	public ShipStructure getStructure() {
		return entityData.get(DATA_STRUCTURE);
	}

	public void setStructure(ShipStructure structure) {
		entityData.set(DATA_STRUCTURE, structure);
		onStructureChanged();
	}

	private void onStructureChanged() {
		ShipStructure structure = getStructure();
		stats = structure.stats();
		hull = new ShipHull(structure);
		hullBottom = structure.hullBottom();
		radius = Math.max(0.5, structure.horizontalRadius());
		BlockState helm = structure.blocks().stream()
				.filter(b -> b.pos().equals(structure.helm()))
				.map(ShipStructure.ShipBlock::state)
				.findFirst().orElse(null);
		Direction facing = helm != null && helm.hasProperty(ShipWheelBlock.FACING) ? helm.getValue(ShipWheelBlock.FACING) : Direction.SOUTH;
		seats = structure.isEmpty() ? List.of() : structure.seats(facing);

		// Length along the keel and width across it, for wave sampling.
		double rad = Math.toRadians(structure.baseYaw());
		double fx = -Math.sin(rad), fz = Math.cos(rad);
		double maxAlong = 0.5, maxAcross = 0.5;
		for (ShipStructure.ShipBlock b : structure.blocks()) {
			double along = b.pos().getX() * fx + b.pos().getZ() * fz;
			double across = b.pos().getX() * fz - b.pos().getZ() * fx;
			maxAlong = Math.max(maxAlong, Math.abs(along) + 0.5);
			maxAcross = Math.max(maxAcross, Math.abs(across) + 0.5);
		}
		halfLength = maxAlong;
		halfBeam = maxAcross;
		setBoundingBox(makeBoundingBox(position()));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_STRUCTURE.equals(accessor)) onStructureChanged();
	}

	public ShipStats getStats() {
		return stats;
	}

	/** Rotation of the ship relative to how it was built, in degrees. */
	public float getRelativeYaw(float partialTick) {
		return getYRot(partialTick) - getStructure().baseYaw();
	}

	public List<AABB> getHullBoxes() {
		return hull.worldBoxes(position(), getYRot() - getStructure().baseYaw());
	}

	public AABB getHullBounds() {
		return hull.worldBounds(position(), getYRot() - getStructure().baseYaw());
	}

	/** The entity's own box is just the ship's wheel: that's what players click to take the helm. */
	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		ShipStructure structure = entityData == null ? ShipStructure.EMPTY : getStructure();
		if (structure.isEmpty()) return super.makeBoundingBox(pos);
		BlockPos h = structure.helm();
		Vec3 c = localToWorld(pos, getYRot() - structure.baseYaw(), h.getX(), h.getY(), h.getZ());
		return new AABB(c.x - 0.5, c.y, c.z - 0.5, c.x + 0.5, c.y + 1.0, c.z + 0.5);
	}

	/** World position of a point in ship space, for a given ship position and relative yaw. */
	public static Vec3 localToWorld(Vec3 shipPos, double relYawDeg, double x, double y, double z) {
		double rad = Math.toRadians(relYawDeg);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		return new Vec3(shipPos.x + x * cos - z * sin, shipPos.y + y, shipPos.z + x * sin + z * cos);
	}

	private static Vec3 rotateY(Vec3 v, double deg) {
		double rad = Math.toRadians(deg);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	// ---------------------------------------------------------------------------------------------
	// Synced state for the HUD and renderer

	public float getSailDeploy() {
		return entityData.get(DATA_SAIL_DEPLOY);
	}

	public float getSailTrim() {
		return entityData.get(DATA_SAIL_TRIM);
	}

	public float getWindDirection() {
		return entityData.get(DATA_WIND_DIR);
	}

	public float getWindStrength() {
		return entityData.get(DATA_WIND_STRENGTH);
	}

	/** Forward speed in blocks per tick. */
	public float getSpeed() {
		return entityData.get(DATA_SPEED);
	}

	public boolean isAground() {
		return entityData.get(DATA_AGROUND);
	}

	/** Bow-up rocking in degrees, interpolated for rendering. */
	public float getPitch(float partialTick) {
		return Mth.lerp(partialTick, prevPitch, entityData.get(DATA_PITCH));
	}

	/** Starboard-down rocking in degrees, interpolated for rendering. */
	public float getRoll(float partialTick) {
		return Mth.lerp(partialTick, prevRoll, entityData.get(DATA_ROLL));
	}

	/** Height of the waterline above the entity position, for the renderer's rocking pivot. */
	public float getWaterlineOffset() {
		return getStructure().waterline() + 1f;
	}

	/** Called from the network handler while the captain holds a trim key: -1 port, 0 hold, 1 starboard. */
	public void setTrimInput(int direction) {
		this.trimInput = Mth.clamp(direction, -1, 1);
	}

	public boolean isCaptain(Entity entity) {
		return getFirstPassenger() == entity;
	}

	// ---------------------------------------------------------------------------------------------
	// Simulation

	@Override
	public void tick() {
		lastYaw = getYRot();
		ShipCollisions.track(this);
		super.tick();

		if (!(level() instanceof ServerLevel level)) {
			prevPitch = entityData.get(DATA_PITCH);
			prevRoll = entityData.get(DATA_ROLL);
			interpolationHandler.interpolate();
			return;
		}
		if (getStructure().isEmpty()) {
			discard();
			return;
		}
		if (Double.isNaN(baseY)) baseY = getY();

		Input input = getFirstPassenger() instanceof ServerPlayer captain ? captain.getLastClientInput() : Input.EMPTY;
		if (getPassengers().isEmpty()) trimInput = 0;

		// Sails: W lets out canvas, S reefs it in.
		float deploy = getSailDeploy();
		if (input.forward()) deploy += SAIL_RATE;
		if (input.backward()) deploy -= SAIL_RATE;
		deploy = Mth.clamp(deploy, 0f, 1f);
		entityData.set(DATA_SAIL_DEPLOY, deploy);

		float trim = SailPhysics.clampTrim(getSailTrim() + trimInput * TRIM_RATE);
		entityData.set(DATA_SAIL_TRIM, trim);

		// Wind at the ship's position.
		int weather = level.isThundering() ? 2 : level.isRaining() ? 1 : 0;
		WindField.Wind wind = WindField.sample(level.getSeed(), getX(), getZ(), level.getGameTime(), weather);
		entityData.set(DATA_WIND_DIR, wind.directionDeg());
		entityData.set(DATA_WIND_STRENGTH, wind.strength());

		double windRel = wind.directionDeg() - getYRot();
		double speed = SailPhysics.stepSpeed(getSpeed(), stats, wind.strength(), windRel, trim, deploy);

		// Rudder: A/D.
		float rudder = (input.right() ? 1f : 0f) - (input.left() ? 1f : 0f);
		float newYaw = getYRot() + rudder * (float) SailPhysics.turnRate(stats, speed);

		updateWaves(level, wind, windRel, deploy);

		double rad = Math.toRadians(newYaw);
		Vec3 target = new Vec3(getX() - Math.sin(rad) * speed, baseY + heave, getZ() + Math.cos(rad) * speed);

		boolean aground = false;
		if (canOccupy(level, target, newYaw)) {
			moveShip(target, newYaw);
		} else if (canOccupy(level, new Vec3(getX(), target.y, getZ()), newYaw)) {
			moveShip(new Vec3(getX(), target.y, getZ()), newYaw);
			speed = 0;
			aground = true;
		} else {
			speed = 0;
			aground = true;
		}

		if (aground && !isAground() && Math.abs(getSpeed()) > 0.05) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.0f, 0.5f);
		}
		entityData.set(DATA_AGROUND, aground);
		entityData.set(DATA_SPEED, (float) speed);

		// Carry mobs and items standing on deck. Players carry themselves on their own client.
		for (Entity entity : level.getEntities(this, getHullBounds().inflate(1.0, 2.0, 1.0), Entity::isLocalInstanceAuthoritative)) {
			carry(entity);
		}
		finishCarrying();
	}

	/**
	 * Heave, pitch and roll: the hull follows the wave heights under its bow, stern and sides
	 * through a damped spring, so it rises and settles gradually. Heavier ships respond slower.
	 * Under sail the ship also heels away from the wind.
	 */
	private void updateWaves(ServerLevel level, WindField.Wind wind, double windRel, float deploy) {
		double t = level.getGameTime();
		double rad = Math.toRadians(getYRot());
		double fx = -Math.sin(rad), fz = Math.cos(rad);   // bow
		double sx = -fz, sz = fx;                         // starboard
		double x = getX(), z = getZ();
		float dir = wind.directionDeg(), strength = wind.strength();

		double bow = WaveField.height(x + fx * halfLength, z + fz * halfLength, t, dir, strength);
		double stern = WaveField.height(x - fx * halfLength, z - fz * halfLength, t, dir, strength);
		double stbd = WaveField.height(x + sx * halfBeam, z + sz * halfBeam, t, dir, strength);
		double port = WaveField.height(x - sx * halfBeam, z - sz * halfBeam, t, dir, strength);
		double centre = WaveField.height(x, z, t, dir, strength);

		double inertia = Math.sqrt(Math.max(1.0, stats.mass() / 40.0));
		double stiffness = 0.08 / inertia;

		double heaveTarget = (bow + stern + port + stbd + 2 * centre) / 6.0;
		heaveVel = heaveVel * 0.88 + (heaveTarget - heave) * stiffness;
		heave = Mth.clamp(heave + heaveVel, -WaveField.MAX_AMPLITUDE, WaveField.MAX_AMPLITUDE);

		double pitchTarget = Math.toDegrees(Math.atan2(bow - stern, 2 * halfLength)) + Math.min(1.0, getSpeed() * 2.5);
		double heel = strength * deploy * Math.sin(Math.toRadians(windRel)) * 3.0;
		double rollTarget = Math.toDegrees(Math.atan2(port - stbd, 2 * halfBeam)) + heel;
		pitchVel = pitchVel * 0.85 + (pitchTarget - pitch) * stiffness;
		rollVel = rollVel * 0.85 + (rollTarget - roll) * stiffness;
		pitch = Mth.clamp(pitch + pitchVel, -MAX_TILT, MAX_TILT);
		roll = Mth.clamp(roll + rollVel, -MAX_TILT, MAX_TILT);
		entityData.set(DATA_PITCH, (float) pitch);
		entityData.set(DATA_ROLL, (float) roll);
	}

	/**
	 * Moves an entity along with the ship since the previous carry pass: if it is standing on
	 * the deck it rides along (and turns with the ship); if the hull ran into it, it gets shoved
	 * out of the way. Call once per tick per entity, after the ship has ticked and before
	 * {@link #finishCarrying()}.
	 */
	public void carry(Entity entity) {
		if (carryFromPos == null || entity == this || entity.isPassenger() || entity instanceof ShipEntity || entity.isSpectator()) return;
		AABB box = entity.getBoundingBox();
		List<AABB> now = getHullBoxes();
		boolean onDeck = ShipHull.standsOn(box, carryFromBoxes, 0.3) || ShipHull.standsOn(box, now, 0.3);
		boolean hit = !onDeck && ShipHull.intersectsAny(box.deflate(0.05), now);
		if (!onDeck && !hit) return;

		double dYaw = getYRot() - carryFromYaw;
		if (position().distanceToSqr(carryFromPos) > 16.0) return; // a resync jump, not sailing
		Vec3 moved = position().add(rotateY(entity.position().subtract(carryFromPos), dYaw));
		if (hit) {
			Vec3 out = new Vec3(moved.x - getX(), 0, moved.z - getZ());
			if (out.lengthSqr() > 1e-6) moved = moved.add(out.normalize().scale(0.15));
		}
		if (onDeck) {
			// Keep feet on top of the deck: if the hull rose into the entity (waves, tick order),
			// lift it back out, otherwise collision would ignore the deck and it would sink through.
			AABB feet = box.move(moved.subtract(entity.position()));
			double top = Double.NEGATIVE_INFINITY;
			for (AABB b : now) {
				if (b.maxX > feet.minX + 1e-3 && b.minX < feet.maxX - 1e-3 && b.maxZ > feet.minZ + 1e-3 && b.minZ < feet.maxZ - 1e-3
						&& b.maxY > feet.minY && b.maxY <= feet.minY + 0.5) {
					top = Math.max(top, b.maxY);
				}
			}
			if (top > feet.minY) moved = moved.add(0, top - feet.minY, 0);
		}
		if (moved.distanceToSqr(entity.position()) < 1e-10 && dYaw == 0) return;
		entity.setPos(moved.x, moved.y, moved.z);
		if (onDeck && dYaw != 0) {
			entity.setYRot(entity.getYRot() + (float) dYaw);
			if (entity instanceof LivingEntity living) {
				living.setYHeadRot(living.getYHeadRot() + (float) dYaw);
				living.setYBodyRot(living.yBodyRot + (float) dYaw);
			}
		}
	}

	/** Call after {@link #carry} has been applied to everything for this tick. */
	public void finishCarrying() {
		carryFromPos = position();
		carryFromYaw = getYRot();
		carryFromBoxes = getHullBoxes();
	}

	private void moveShip(Vec3 pos, float yaw) {
		setYRot(yaw % 360f);
		setPos(pos.x, pos.y, pos.z);
	}

	/**
	 * Whether the ship fits at the given position/heading: no block of it may overlap solid world
	 * blocks, and at least half of the hull bottom must be in or over water.
	 */
	private boolean canOccupy(ServerLevel level, Vec3 pos, float yaw) {
		ShipStructure structure = getStructure();
		double relYaw = yaw - structure.baseYaw();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (ShipStructure.ShipBlock b : structure.blocks()) {
			Vec3 c = localToWorld(pos, relYaw, b.pos().getX(), b.pos().getY() + 0.5, b.pos().getZ());
			cursor.set(Mth.floor(c.x), Mth.floor(c.y), Mth.floor(c.z));
			if (!level.isLoaded(cursor)) return false;
			BlockState world = level.getBlockState(cursor);
			if (!world.isAir() && !world.getCollisionShape(level, cursor).isEmpty()) return false;
		}
		int wet = 0;
		for (BlockPos p : hullBottom) {
			Vec3 c = localToWorld(pos, relYaw, p.getX(), p.getY() + 0.5, p.getZ());
			cursor.set(Mth.floor(c.x), Mth.floor(c.y), Mth.floor(c.z));
			if (level.getFluidState(cursor).is(FluidTags.WATER) || level.getFluidState(cursor.below()).is(FluidTags.WATER)) wet++;
		}
		return wet * 2 >= hullBottom.size();
	}

	// ---------------------------------------------------------------------------------------------
	// Disassembly: turn the ship back into blocks.

	public void tryDisassemble(ServerPlayer player) {
		if (!(level() instanceof ServerLevel level)) return;
		if (Math.abs(getSpeed()) > 0.12) {
			player.sendOverlayMessage(Component.translatable("message.seaofsteves.too_fast"));
			return;
		}

		ShipStructure structure = getStructure();
		int quarterTurns = Math.floorMod(Math.round((getYRot() - structure.baseYaw()) / 90f), 4);
		Rotation rotation = switch (quarterTurns) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		double restY = Double.isNaN(baseY) ? getY() : baseY;
		BlockPos anchor = BlockPos.containing(getX(), restY + 0.5, getZ());

		List<BlockPos> targets = new ArrayList<>(structure.blocks().size());
		for (ShipStructure.ShipBlock b : structure.blocks()) {
			BlockPos target = anchor.offset(rotate(b.pos(), quarterTurns));
			BlockState existing = level.getBlockState(target);
			if (!existing.isAir() && !existing.canBeReplaced()) {
				player.sendOverlayMessage(Component.translatable("message.seaofsteves.no_room", target.getX(), target.getY(), target.getZ()));
				return;
			}
			targets.add(target);
		}

		// Everyone aboard keeps their spot on deck. Players get extra slack because the server's
		// view of where they stand lags their client a little.
		Vec3 snappedPos = new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
		double snapTurn = structure.baseYaw() + quarterTurns * 90.0 - getYRot();
		List<Entity> aboard = new ArrayList<>();
		for (Entity e : level.getEntities(this, getHullBounds().inflate(1.5, 2.0, 1.5), e -> !e.isPassenger() && !(e instanceof ShipEntity))) {
			double slack = e instanceof Player ? 1.0 : 0.3;
			if (ShipHull.standsOn(e.getBoundingBox(), getHullBoxes(), slack)) aboard.add(e);
		}
		List<Vec3> aboardSpots = new ArrayList<>();
		for (Entity e : aboard) {
			Vec3 local = e.position().subtract(position());
			aboardSpots.add(snappedPos.add(rotateY(new Vec3(local.x, local.y, local.z), snapTurn)).add(0, 0.05, 0));
		}

		// Solid blocks first so torches, ladders and the like have something to hang on.
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < targets.size(); i++) order.add(i);
		order.sort((a, b) -> Boolean.compare(!isFullCube(structure.blocks().get(a).state()), !isFullCube(structure.blocks().get(b).state())));
		for (int i : order) {
			level.setBlock(targets.get(i), structure.blocks().get(i).state().rotate(rotation), ShipAssembler.SILENT_MOVE);
		}
		for (BlockPos target : targets) {
			level.updateNeighborsAt(target, level.getBlockState(target).getBlock());
		}
		drainHull(level, targets);

		// The captain steps off at the wheel.
		List<Entity> riders = new ArrayList<>(getPassengers());
		List<Vec3> riderSpots = new ArrayList<>();
		for (int i = 0; i < riders.size(); i++) {
			BlockPos seat = i < seats.size() ? seats.get(i) : structure.helm();
			riderSpots.add(Vec3.atBottomCenterOf(anchor.offset(rotate(seat, quarterTurns))));
		}
		ejectPassengers();
		for (int i = 0; i < riders.size(); i++) {
			Vec3 spot = riderSpots.get(i);
			riders.get(i).teleportTo(spot.x, spot.y + 0.05, spot.z);
		}
		for (int i = 0; i < aboard.size(); i++) {
			Vec3 spot = aboardSpots.get(i);
			aboard.get(i).teleportTo(spot.x, spot.y, spot.z);
		}

		level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0f, 0.8f);
		player.sendOverlayMessage(Component.translatable("message.seaofsteves.anchored"));
		discard();
	}

	private static boolean isFullCube(BlockState state) {
		return state.isSolidRender();
	}

	/** Removes water trapped inside the hull (below deck) after placing the ship back. */
	private void drainHull(ServerLevel level, List<BlockPos> targets) {
		java.util.Set<BlockPos> shipBlocks = new java.util.HashSet<>(targets);
		java.util.Map<Long, Integer> lowestInColumn = new java.util.HashMap<>();
		java.util.Map<Long, Integer> highestInColumn = new java.util.HashMap<>();
		for (BlockPos p : targets) {
			long key = BlockPos.asLong(p.getX(), 0, p.getZ());
			lowestInColumn.merge(key, p.getY(), Math::min);
			highestInColumn.merge(key, p.getY(), Math::max);
		}
		for (var entry : lowestInColumn.entrySet()) {
			int x = BlockPos.getX(entry.getKey()), z = BlockPos.getZ(entry.getKey());
			int top = highestInColumn.get(entry.getKey());
			for (int y = entry.getValue() + 1; y < top; y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (!shipBlocks.contains(p) && level.getBlockState(p).is(Blocks.WATER)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** Rotates a relative block position clockwise (seen from above) by quarter turns. */
	static BlockPos rotate(BlockPos p, int quarterTurns) {
		int x = p.getX(), z = p.getZ();
		for (int i = 0; i < quarterTurns; i++) {
			int nx = -z;
			z = x;
			x = nx;
		}
		return new BlockPos(x, p.getY(), z);
	}

	// ---------------------------------------------------------------------------------------------
	// The captain

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() || player.getVehicle() == this) return InteractionResult.PASS;
		if (!canAddPassenger(player)) return InteractionResult.PASS;
		if (!level().isClientSide()) {
			return player.startRiding(this) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	/** Only the captain rides; everyone else walks the deck. */
	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		if (!hasPassenger(passenger)) return;
		BlockPos seat = seats.isEmpty() ? getStructure().helm() : seats.getFirst();
		Vec3 p = localToWorld(position(), getYRot() - getStructure().baseYaw(), seat.getX(), seat.getY(), seat.getZ());
		moveFunction.accept(passenger, p.x, p.y, p.z);

		// Turn the captain with the ship.
		float delta = getYRot() - lastYaw;
		if (delta != 0f) {
			passenger.setYRot(passenger.getYRot() + delta);
			passenger.setYHeadRot(passenger.getYHeadRot() + delta);
		}
	}

	/** Stepping away from the wheel leaves you standing on deck right behind it. */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		BlockPos seat = seats.isEmpty() ? getStructure().helm() : seats.getFirst();
		return localToWorld(position(), getYRot() - getStructure().baseYaw(), seat.getX(), seat.getY() + 0.05, seat.getZ());
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		double d = 128.0 * getViewScale() + radius;
		return distance < d * d;
	}

	// ---------------------------------------------------------------------------------------------
	// Saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("structure", ShipStructure.CODEC, getStructure());
		output.putFloat("sail_deploy", getSailDeploy());
		output.putFloat("sail_trim", getSailTrim());
		output.putFloat("speed", getSpeed());
		output.putDouble("base_y", Double.isNaN(baseY) ? getY() : baseY);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.read("structure", ShipStructure.CODEC).ifPresent(this::setStructure);
		entityData.set(DATA_SAIL_DEPLOY, input.getFloatOr("sail_deploy", 0f));
		entityData.set(DATA_SAIL_TRIM, input.getFloatOr("sail_trim", 0f));
		entityData.set(DATA_SPEED, input.getFloatOr("speed", 0f));
		double saved = input.getDoubleOr("base_y", Double.NaN);
		if (!Double.isNaN(saved)) baseY = saved;
	}
}
