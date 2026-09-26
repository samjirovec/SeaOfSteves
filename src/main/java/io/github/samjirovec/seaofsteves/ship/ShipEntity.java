package io.github.samjirovec.seaofsteves.ship;

import java.util.ArrayList;
import java.util.List;

import io.github.samjirovec.seaofsteves.block.ShipWheelBlock;
import io.github.samjirovec.seaofsteves.physics.SailPhysics;
import io.github.samjirovec.seaofsteves.physics.ShipStats;
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
 * <p>The server simulates the ship; clients only render it and show the HUD from the synced data.
 */
public class ShipEntity extends Entity {
	private static final EntityDataAccessor<ShipStructure> DATA_STRUCTURE = SynchedEntityData.defineId(ShipEntity.class, ShipStructure.SERIALIZER);
	private static final EntityDataAccessor<Float> DATA_SAIL_DEPLOY = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SAIL_TRIM = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_WIND_DIR = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_WIND_STRENGTH = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_AGROUND = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BOOLEAN);

	private static final float SAIL_RATE = 0.02f;
	private static final float TRIM_RATE = 2.5f;
	private static final int MAX_PASSENGERS = 8;

	private ShipStats stats = ShipStats.EMPTY;
	private List<BlockPos> hullBottom = List.of();
	private List<BlockPos> seats = List.of();
	private double radius = 0.5;
	private int height = 1;

	private int trimInput;
	private float lastYaw;

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
	}

	// ---------------------------------------------------------------------------------------------
	// Structure

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
		hullBottom = structure.hullBottom();
		radius = Math.max(0.5, structure.horizontalRadius());
		height = Math.max(1, structure.height());
		BlockState helm = structure.blocks().stream()
				.filter(b -> b.pos().equals(structure.helm()))
				.map(ShipStructure.ShipBlock::state)
				.findFirst().orElse(null);
		Direction facing = helm != null && helm.hasProperty(ShipWheelBlock.FACING) ? helm.getValue(ShipWheelBlock.FACING) : Direction.SOUTH;
		seats = structure.isEmpty() ? List.of() : structure.seats(facing);
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

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		// Rotation-independent box that always contains the whole ship.
		double r = radius;
		return new AABB(pos.x - r, pos.y, pos.z - r, pos.x + r, pos.y + height, pos.z + r);
	}

	/** World position of the centre of a block of the ship, for a given ship position and relative yaw. */
	public static Vec3 localToWorld(Vec3 shipPos, double relYawDeg, double x, double y, double z) {
		double rad = Math.toRadians(relYawDeg);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		return new Vec3(shipPos.x + x * cos - z * sin, shipPos.y + y, shipPos.z + x * sin + z * cos);
	}

	// ---------------------------------------------------------------------------------------------
	// Synced state for the HUD

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
		super.tick();

		if (!(level() instanceof ServerLevel level)) {
			interpolationHandler.interpolate();
			return;
		}
		if (getStructure().isEmpty()) {
			discard();
			return;
		}

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

		double rad = Math.toRadians(newYaw);
		Vec3 motion = new Vec3(-Math.sin(rad) * speed, 0, Math.cos(rad) * speed);
		Vec3 target = position().add(motion);

		boolean aground = false;
		if (canOccupy(level, target, newYaw)) {
			moveShip(target, newYaw);
		} else if (canOccupy(level, position(), newYaw)) {
			moveShip(position(), newYaw);
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
		BlockPos anchor = BlockPos.containing(getX(), getY(), getZ());

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

		// Put everyone on deck where their seat was.
		List<Entity> riders = new ArrayList<>(getPassengers());
		List<Vec3> spots = new ArrayList<>();
		for (int i = 0; i < riders.size(); i++) {
			BlockPos seat = i < seats.size() ? seats.get(i) : structure.helm();
			spots.add(Vec3.atBottomCenterOf(anchor.offset(rotate(seat, quarterTurns))));
		}
		ejectPassengers();
		for (int i = 0; i < riders.size(); i++) {
			Vec3 spot = spots.get(i);
			riders.get(i).teleportTo(spot.x, spot.y + 0.05, spot.z);
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
	// Passengers

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() || player.getVehicle() == this) return InteractionResult.PASS;
		if (!canAddPassenger(player)) return InteractionResult.PASS;
		if (!level().isClientSide()) {
			return player.startRiding(this) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < Math.min(MAX_PASSENGERS, Math.max(1, seats.size()));
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		if (!hasPassenger(passenger)) return;
		int index = getPassengers().indexOf(passenger);
		BlockPos seat = index >= 0 && index < seats.size() ? seats.get(index) : getStructure().helm();
		Vec3 p = localToWorld(position(), getYRot() - getStructure().baseYaw(), seat.getX(), seat.getY(), seat.getZ());
		moveFunction.accept(passenger, p.x, p.y, p.z);

		// Turn riders with the ship.
		float delta = getYRot() - lastYaw;
		if (delta != 0f) {
			passenger.setYRot(passenger.getYRot() + delta);
			passenger.setYHeadRot(passenger.getYHeadRot() + delta);
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		int index = getPassengers().indexOf(passenger);
		BlockPos seat = index >= 0 && index < seats.size() ? seats.get(index) : getStructure().helm();
		return localToWorld(position(), getYRot() - getStructure().baseYaw(), seat.getX(), seat.getY() + 0.1, seat.getZ());
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.read("structure", ShipStructure.CODEC).ifPresent(this::setStructure);
		entityData.set(DATA_SAIL_DEPLOY, input.getFloatOr("sail_deploy", 0f));
		entityData.set(DATA_SAIL_TRIM, input.getFloatOr("sail_trim", 0f));
		entityData.set(DATA_SPEED, input.getFloatOr("speed", 0f));
	}
}
