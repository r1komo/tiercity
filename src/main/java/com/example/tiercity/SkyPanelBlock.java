package com.example.tiercity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 인공 하늘 패널. 항상 최대 밝기로 그려지고(그림자 없음), 월드 시간에 맞춰 낮/노을/밤/새벽 그림으로 바뀐다.
 * 가끔 스스로 시간을 확인하고(랜덤 틱), 바뀌면 이웃 패널에게 차례로 전파해서 화면이 갱신되듯 번져 나간다.
 */
public class SkyPanelBlock extends Block {
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);   // 0 낮, 1 노을, 2 밤, 3 새벽

    public SkyPanelBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PHASE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    public static int phaseFor(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t < 11500) return 0;
        if (t < 13500) return 1;
        if (t < 22500) return 2;
        return 3;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refresh(state, level, pos, random);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refresh(state, level, pos, random);
    }

    private void refresh(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int phase = phaseFor(level.getDayTime());
        if (state.getValue(PHASE) == phase) return;
        level.setBlock(pos, state.setValue(PHASE, phase), Block.UPDATE_CLIENTS);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n)) continue;
            if (!level.getBlockState(n).is(this)) continue;
            if (level.getBlockTicks().hasScheduledTick(n, this)) continue;
            level.scheduleTick(n, this, 2 + random.nextInt(3));
        }
    }
}
