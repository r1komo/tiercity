package com.example.tiercity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 거리 설비 블록들: 전봇대, 완목, 전선, 가는 배관. */
public final class FixtureBlocks {
    private FixtureBlocks() {}

    /** 전봇대 기둥(가는 콘크리트 기둥). */
    public static class Pole extends Block {
        private static final VoxelShape SHAPE = Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);

        public Pole(Properties p) { super(p); }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) { return SHAPE; }
    }

    /** 전봇대 꼭대기 완목(AXIS: 팔 방향 x/z), 양 끝에 애자. */
    public static class Crossarm extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape POST = Block.box(5.5, 0, 5.5, 10.5, 13, 10.5);
        private static final VoxelShape X = Shapes.or(POST, Block.box(0, 11, 6.5, 16, 13, 9.5));
        private static final VoxelShape Z = Shapes.or(POST, Block.box(6.5, 11, 0, 9.5, 13, 16));

        public Crossarm(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AXIS); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(AXIS, c.getHorizontalDirection().getClockWise().getAxis());
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) {
            return s.getValue(AXIS) == Direction.Axis.X ? X : Z;
        }
    }

    /** 전선 두 가닥(AXIS: 뻗는 방향 x/z, SAG: 블록 안 높이 14-3*SAG 픽셀). 부딪히지 않음. */
    public static class Wire extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final IntegerProperty SAG = IntegerProperty.create("sag", 0, Fixtures.SAG_LEVELS - 1);

        public Wire(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(SAG, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AXIS, SAG); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(AXIS, c.getHorizontalDirection().getAxis());
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) {
            double y = 14 - 3 * s.getValue(SAG);
            return Block.box(0, y - 1, 0, 16, y + 1, 16);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) { return Shapes.empty(); }
    }

    /** 가는 배관(AXIS x/y/z). 굵기는 생성자에서. */
    public static class Pipe extends RotatedPillarBlock {
        private final VoxelShape x, y, z;

        public Pipe(Properties p, double width) {
            super(p);
            double a = 8 - width / 2, b = 8 + width / 2;
            x = Block.box(0, a, a, 16, b, b);
            y = Block.box(a, 0, a, b, 16, b);
            z = Block.box(a, a, 0, b, b, 16);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) {
            switch (s.getValue(AXIS)) {
                case X: return x;
                case Z: return z;
                default: return y;
            }
        }
    }
}
