package io.github.samjirovec.seaofsteves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import io.github.samjirovec.seaofsteves.ship.ShipStructure;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;

/** Draws every block of a ship, rotated with the ship's heading and gently bobbing on the waves. */
public class ShipRenderer extends EntityRenderer<ShipEntity, ShipRenderState> {
	public ShipRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public ShipRenderState createRenderState() {
		return new ShipRenderState();
	}

	@Override
	public void extractRenderState(ShipEntity ship, ShipRenderState state, float partialTick) {
		super.extractRenderState(ship, state, partialTick);
		state.relativeYaw = ship.getRelativeYaw(partialTick);
		state.bob = Mth.sin((ship.tickCount + partialTick) * 0.07f) * 0.04f;
		state.count = 0;

		if (!(ship.level() instanceof ClientLevel level)) return;
		Vec3 pos = ship.getPosition(partialTick);
		for (ShipStructure.ShipBlock block : ship.getStructure().blocks()) {
			if (block.state().getRenderShape() != RenderShape.MODEL) continue;
			BlockPos rel = block.pos();
			Vec3 centre = ShipEntity.localToWorld(pos, state.relativeYaw, rel.getX(), rel.getY() + 0.5, rel.getZ());
			BlockPos worldPos = BlockPos.containing(centre);

			int index = state.count++;
			MovingBlockRenderState moving = state.slot(index);
			state.offsets.set(index, rel);
			moving.blockState = block.state();
			moving.blockPos = worldPos;
			moving.randomSeedPos = rel;
			moving.biome = level.getBiome(worldPos);
			moving.cardinalLighting = level.cardinalLighting();
			moving.lightEngine = level.getLightEngine();
		}
	}

	@Override
	public void submit(ShipRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0, state.bob, 0);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.relativeYaw));
		for (int i = 0; i < state.count; i++) {
			BlockPos rel = state.offsets.get(i);
			poseStack.pushPose();
			poseStack.translate(rel.getX() - 0.5, rel.getY(), rel.getZ() - 0.5);
			collector.submitMovingBlock(poseStack, state.blocks.get(i), state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
