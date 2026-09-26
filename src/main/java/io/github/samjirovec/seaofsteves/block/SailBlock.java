package io.github.samjirovec.seaofsteves.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A panel of canvas. Build sails as a flat rectangle of these resting on top of a stack of
 * fences (the mast). Every sail block adds sail area; the more sail per unit of weight, the
 * faster the ship. {@link #AXIS} is the horizontal direction the panel spans.
 */
public class SailBlock extends Block {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final VoxelShape SPANS_X = Block.box(0, 0, 7, 16, 16, 9);
	private static final VoxelShape SPANS_Z = Block.box(7, 0, 0, 9, 16, 16);

	public SailBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	/** The canvas faces the player placing it. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction.Axis facing = context.getHorizontalDirection().getAxis();
		return defaultBlockState().setValue(AXIS, facing == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? SPANS_X : SPANS_Z;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}
}
