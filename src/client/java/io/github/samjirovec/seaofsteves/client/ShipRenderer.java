package io.github.samjirovec.seaofsteves.client;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import io.github.samjirovec.seaofsteves.ship.ShipStructure;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws every block of a ship, turned to the ship's heading and rocking on the waves. Sails swing
 * around their masts to the current trim and furl up as canvas is taken in.
 */
public class ShipRenderer extends EntityRenderer<ShipEntity, ShipRenderState> {
	public ShipRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	/** The entity's own box is only the wheel; cull against the whole hull instead. */
	@Override
	protected AABB getBoundingBoxForCulling(ShipEntity ship, float partialTick) {
		return ship.getHullBounds().inflate(1.0);
	}

	@Override
	public ShipRenderState createRenderState() {
		return new ShipRenderState();
	}

	@Override
	public void extractRenderState(ShipEntity ship, ShipRenderState state, float partialTick) {
		super.extractRenderState(ship, state, partialTick);
		state.relativeYaw = ship.getRelativeYaw(partialTick);
		state.pitch = ship.getPitch(partialTick);
		state.roll = ship.getRoll(partialTick);
		state.pivotY = ship.getWaterlineOffset();
		state.baseYaw = ship.getStructure().baseYaw();
		state.trim = ship.getSailTrim(partialTick);
		state.canvas = 0.12f + 0.88f * ship.getSailDeploy(partialTick);
		state.count = 0;
		state.rigs.clear();

		if (!(ship.level() instanceof ClientLevel level)) return;
		Vec3 pos = ship.getPosition(partialTick);
		List<ShipEntity.Rig> rigs = ship.getRigs();
		for (ShipEntity.Rig rig : rigs) {
			BlockPos mast = rig.layout().mastTop();
			state.rigs.add(new float[] {mast.getX(), mast.getZ(), rig.layout().sailTop() + 1});
		}

		for (ShipStructure.ShipBlock block : ship.getStructure().blocks()) {
			if (block.state().getRenderShape() != RenderShape.MODEL) continue;
			int rigIndex = -1;
			for (int r = 0; r < rigs.size(); r++) {
				if (rigs.get(r).sails().contains(block.pos())) {
					rigIndex = r;
					break;
				}
			}
			add(state, level, pos, block.pos(), block.state(), rigIndex);
		}

		// The mast runs up through the middle of its sail to a short tip above it.
		Map<BlockPos, BlockState> states = rigs.isEmpty() ? Map.of() : ship.getStructure().asMap();
		for (ShipEntity.Rig rig : rigs) {
			BlockPos top = rig.layout().mastTop();
			BlockState post = states.getOrDefault(top, Blocks.OAK_FENCE.defaultBlockState()).getBlock().defaultBlockState();
			for (int y = top.getY() + 1; y <= rig.layout().sailTop() + 1; y++) {
				add(state, level, pos, new BlockPos(top.getX(), y, top.getZ()), post, -1);
			}
		}
	}

	private static void add(ShipRenderState state, ClientLevel level, Vec3 shipPos, BlockPos rel, BlockState blockState, int rigIndex) {
		Vec3 centre = ShipEntity.localToWorld(shipPos, state.relativeYaw, rel.getX(), rel.getY() + 0.5, rel.getZ());
		BlockPos worldPos = BlockPos.containing(centre);
		int index = state.count++;
		MovingBlockRenderState moving = state.slot(index);
		state.offsets.set(index, rel);
		state.rigOf.set(index, rigIndex);
		moving.blockState = blockState;
		moving.blockPos = worldPos;
		moving.randomSeedPos = rel;
		moving.biome = level.getBiome(worldPos);
		moving.cardinalLighting = level.cardinalLighting();
		moving.lightEngine = level.getLightEngine();
	}

	@Override
	public void submit(ShipRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-state.relativeYaw)));

		// Rock on the waves around the waterline. In ship space the bow points along the heading
		// the ship was built with; pitch turns about the beam, roll about the keel.
		double b = Math.toRadians(state.baseYaw);
		float fx = (float) -Math.sin(b), fz = (float) Math.cos(b);   // bow
		float sx = -fz, sz = fx;                                     // starboard
		poseStack.translate(0, state.pivotY, 0);
		poseStack.mulPose(new Matrix4f()
				.rotate((float) Math.toRadians(state.pitch), sx, 0, sz)
				.rotate((float) Math.toRadians(state.roll), fx, 0, fz));
		poseStack.translate(0, -state.pivotY, 0);
		for (int i = 0; i < state.count; i++) {
			BlockPos rel = state.offsets.get(i);
			poseStack.pushPose();
			int rig = state.rigOf.get(i);
			if (rig >= 0) {
				// Swing the sail around its mast to the trim angle, and furl it up toward its top
				// edge as canvas is taken in.
				float[] r = state.rigs.get(rig);
				poseStack.translate(r[0], r[2], r[1]);
				poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-state.trim)));
				poseStack.scale(1f, state.canvas, 1f);
				poseStack.translate(-r[0], -r[2], -r[1]);
			}
			poseStack.translate(rel.getX() - 0.5, rel.getY(), rel.getZ() - 0.5);
			collector.submitMovingBlock(poseStack, state.blocks.get(i), state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
