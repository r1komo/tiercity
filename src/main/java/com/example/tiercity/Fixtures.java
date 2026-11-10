package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 거리 설비 전용 블록(전봇대, 완목, 전선, 가는 배관) 상태. 생성기가 모드 블록을 넣어 주고, 비어 있으면 시험용 대체 블록을 쓴다.
 * 축 번호: 0 x, 1 y, 2 z(배관). 전선/완목은 0 x, 1 z.
 */
final class Fixtures {
    private Fixtures() {}

    static volatile BlockState pole = null;
    static volatile BlockState[] crossarm = null;        // [0] 팔이 x 방향, [1] 팔이 z 방향
    static volatile BlockState[][] wire = null;          // [축 0 x, 1 z][처짐 0..4]
    static volatile BlockState[] pipeS = null, pipeM = null, pipeL = null;   // [0 x, 1 y, 2 z]
    /** 방향 번호(벽 쪽): 0 남(+z), 1 북(-z), 2 동(+x), 3 서(-x). */
    static volatile BlockState[] clampV = null, clampH = null, anchor = null;

    static final int POLE_H = 18;                         // 기둥 17칸 + 완목
    static final int SAG_LEVELS = 5;

    static BlockState pole() { BlockState s = pole; return s != null ? s : Blocks.ANDESITE_WALL.defaultBlockState(); }

    static BlockState crossarm(int axis) { BlockState[] a = crossarm; return a != null ? a[axis] : Blocks.ANDESITE_WALL.defaultBlockState(); }

    static BlockState wire(int axis, int sag) { BlockState[][] a = wire; return a != null ? a[axis][sag] : Blocks.CHAIN.defaultBlockState(); }

    static BlockState pipeS(int axis) { BlockState[] a = pipeS; return a != null ? a[axis] : Blocks.IRON_BARS.defaultBlockState(); }

    static BlockState pipeL(int axis) { BlockState[] a = pipeL; return a != null ? a[axis] : Blocks.POLISHED_ANDESITE.defaultBlockState(); }

    static BlockState clampV(int dir) { BlockState[] a = clampV; return a != null ? a[dir] : Blocks.SMOOTH_STONE.defaultBlockState(); }

    static BlockState clampH(int dir) { BlockState[] a = clampH; return a != null ? a[dir] : Blocks.SMOOTH_STONE.defaultBlockState(); }

    static BlockState anchor(int dir) { BlockState[] a = anchor; return a != null ? a[dir] : Blocks.LIGHTNING_ROD.defaultBlockState(); }

    /** 벽이 (dx, dz) 쪽에 있을 때의 방향 번호. */
    static int dirTo(int dx, int dz) { return dz > 0 ? 0 : dz < 0 ? 1 : dx > 0 ? 2 : 3; }

    static BlockState pipeM(int axis) { BlockState[] a = pipeM; return a != null ? a[axis] : Blocks.POLISHED_ANDESITE.defaultBlockState(); }

    /** 전선 높이(블록 단위 실수) → {블록 y, 처짐 단계}. 처짐 단계 s의 전선은 블록 안 14-3s 픽셀 높이. */
    static int[] wireAt(double yAbs) {
        double px = yAbs * 16;
        int by = (int) Math.floor(px / 16);
        double within = px - by * 16;
        int sag = (int) Math.round((14 - within) / 3.0);
        if (sag < 0) sag = 0;
        if (sag >= SAG_LEVELS) sag = SAG_LEVELS - 1;
        return new int[]{by, sag};
    }

    /** 늘어진 전선: 양 끝 높이 a, b, 가운데가 d만큼 처짐. t는 0..1. */
    static double catenary(double a, double b, double d, double t) {
        return a + (b - a) * t - d * 4 * t * (1 - t);
    }
}
