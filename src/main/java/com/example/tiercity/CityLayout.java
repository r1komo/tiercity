package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 층상 도시 레이아웃 (v0.5: 원형 데크, 중앙이 두꺼운 아랫면, 코어 + 바깥 고리 기둥, 시드 연동).
 * (seed, x, z) -> 컬럼 사양을 돌려주는 순수 함수 집합이므로 청크 경계와 무관하게 항상 같은 결과가 나온다.
 *
 * 평면 구조: 주기 PERIOD = CELL(구획 플랫폼) + GAP(통로 구간).
 *  - 구획(지름 CELL의 원): 구획마다 높이(tier)가 다른 원형 데크. 윗면은 평평, 아랫면은 중앙이 두꺼운 포물선 그릇
 *  - 기둥: 구획 중앙의 굵은 코어 1개 + 반지름 400 고리에 8개. 데크와 만나는 위쪽이 넓어짐(기둥머리)
 *  - 통로(GAP): 인접 구획 중심선을 잇는 폭 15의 연결 통로, 두 구획 높이 사이를 경사로 보간, 중앙에 교각 1개
 *  - 교차부(GAP x GAP): 비어 있음
 * 지면은 없다. 기둥은 월드 바닥(MIN_Y)까지 내려가고 그 아래는 허공이다.
 */
public final class CityLayout {
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 512;

    public static final int CELL = 1024;
    public static final int GAP = 128;
    public static final int PERIOD = CELL + GAP;   // 1152 = 72청크
    public static final int MID = CELL / 2;        // 통로 중심선 오프셋 / 구획 중심
    public static final int WALK_HALF = 7;         // 통로 폭 = 2*7+1 = 15

    public static final int LEVEL_BASE = 128;      // 최저 층 데크 상면 y
    public static final int LEVEL_STEP = 16;
    public static final int LEVELS = 4;            // 인접 최대 높이차 48 / 통로 128칸 = 경사 0.375

    public static final int THICK_EDGE = 12;       // 데크 가장자리 두께
    public static final int THICK_CENTER = 72;     // 데크 중앙 두께 (아랫면이 포물선으로 두꺼워짐)
    public static final int WALK_THICK = 4;
    public static final int PARAPET_H = 2;
    public static final int EDGE_W = 2;            // 구획 가장자리 난간 두께
    public static final int RAIL_W = 2;            // 통로 난간 두께

    public static final int CORE_R = 48;           // 중앙 코어 기둥 반지름
    public static final int RING_R = 24;           // 바깥 고리 기둥 반지름
    public static final int RING_DIST = 400;       // 고리 반지름(동서남북)
    public static final int RING_DIAG = 283;       // 고리 반지름(대각선 성분, 400/√2)
    public static final int FLARE_H = 24;          // 기둥머리 높이
    public static final int FLARE_ADD = 8;         // 기둥머리에서 늘어나는 반지름
    public static final int PIER_LEN = 16;         // 통로 중앙 교각 길이

    /** {중심 dx, 중심 dz, 반지름} (구획 중심 기준) */
    private static final int[][] PILLARS = {
            {0, 0, CORE_R},
            {RING_DIST, 0, RING_R}, {-RING_DIST, 0, RING_R}, {0, RING_DIST, RING_R}, {0, -RING_DIST, RING_R},
            {RING_DIAG, RING_DIAG, RING_R}, {RING_DIAG, -RING_DIAG, RING_R},
            {-RING_DIAG, RING_DIAG, RING_R}, {-RING_DIAG, -RING_DIAG, RING_R}
    };

    /**
     * deckTop < 0 이면 데크 없음.
     * pier: 통로 교각 컬럼. pd2/pr/pcap: 가장 가까운 원형 기둥까지의 거리 제곱 / 기둥 반지름 / 기둥 꼭대기(기둥머리 기준) y.
     */
    public record Column(int deckTop, int deckBottom, boolean parapet, boolean pier, int pd2, int pr, int pcap) {
        public boolean hasPillar() {
            int r = pr + FLARE_ADD;
            return pier || (pr > 0 && pd2 <= r * r);
        }
        public int topSolid() {
            if (deckTop < 0) return MIN_Y - 1;
            return deckTop + (parapet ? PARAPET_H : 0);
        }
        public int bottomSolid() {
            return hasPillar() ? MIN_Y : deckBottom;
        }
    }

    private static final Column NONE = new Column(-1, -1, false, false, 0, 0, 0);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState JOINT = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState DECK_TOP = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState DECK_BODY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState RAIL = Blocks.POLISHED_ANDESITE.defaultBlockState();

    private CityLayout() {}

    private static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L);
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return h;
    }

    private static int rand(long seed, int a, int b, int salt, int bound) {
        return (int) ((hash(seed, a, b, salt) >>> 33) % bound);
    }

    public static int tierTop(long seed, int cellX, int cellZ) {
        return LEVEL_BASE + rand(seed, cellX, cellZ, 7, LEVELS) * LEVEL_STEP;
    }

    private static int ramp(int a, int b, int g) {
        return (int) Math.round(a + (b - a) * (g + 0.5) / GAP);
    }

    /** ratio = (중심으로부터 거리 / 반지름)². 가장자리 THICK_EDGE, 중앙 THICK_CENTER의 포물선. */
    private static int deckThick(double ratio) {
        double r = Math.min(1.0, ratio);
        return THICK_EDGE + (int) Math.round((THICK_CENTER - THICK_EDGE) * (1.0 - r));
    }

    /** 통로 중앙의 교각 하나 (통로 폭 전체). */
    private static boolean walkPier(int g, int off) {
        return g >= GAP / 2 - PIER_LEN / 2 && g < GAP / 2 + PIER_LEN / 2 && off <= WALK_HALF;
    }

    public static Column column(long seed, int wx, int wz) {
        int cx = Math.floorDiv(wx, PERIOD), px = Math.floorMod(wx, PERIOD);
        int cz = Math.floorDiv(wz, PERIOD), pz = Math.floorMod(wz, PERIOD);
        boolean inX = px < CELL, inZ = pz < CELL;

        if (inX && inZ) {
            // 원형 구획: 칸 중심 기준 반지름 CELL/2 (정수 연산을 위해 좌표를 2배로 비교)
            int dx2 = 2 * px + 1 - CELL, dz2 = 2 * pz + 1 - CELL;
            int d2 = dx2 * dx2 + dz2 * dz2;
            if (d2 >= CELL * CELL) return NONE;
            int top = tierTop(seed, cx, cz);
            int bottom = top - deckThick(d2 / (double) (CELL * CELL)) + 1;

            int inner = CELL - 2 * EDGE_W;
            boolean rim = d2 >= inner * inner;
            boolean open = (Math.abs(pz - MID) <= WALK_HALF && Math.abs(dx2) > CELL / 2)
                    || (Math.abs(px - MID) <= WALK_HALF && Math.abs(dz2) > CELL / 2);

            int dx = px - MID, dz = pz - MID;
            int bestD2 = Integer.MAX_VALUE, bestR = 0, bestCap = 0;
            for (int[] p : PILLARS) {
                int ex = dx - p[0], ez = dz - p[1];
                int e2 = ex * ex + ez * ez;
                if (e2 < bestD2) {
                    bestD2 = e2;
                    bestR = p[2];
                    double cr = (p[0] * p[0] + p[1] * p[1]) / (double) (MID * MID);
                    bestCap = top - deckThick(cr) + 1;
                }
            }
            return new Column(top, bottom, rim && !open, false, bestD2, bestR, bestCap);
        }
        if (!inX && inZ) { // X 방향 통로
            int off = Math.abs(pz - MID);
            if (off > WALK_HALF) return NONE;
            int g = px - CELL;
            int top = ramp(tierTop(seed, cx, cz), tierTop(seed, cx + 1, cz), g);
            return new Column(top, top - WALK_THICK + 1, off > WALK_HALF - RAIL_W, walkPier(g, off), 0, 0, 0);
        }
        if (inX) { // Z 방향 통로
            int off = Math.abs(px - MID);
            if (off > WALK_HALF) return NONE;
            int g = pz - CELL;
            int top = ramp(tierTop(seed, cx, cz), tierTop(seed, cx, cz + 1), g);
            return new Column(top, top - WALK_THICK + 1, off > WALK_HALF - RAIL_W, walkPier(g, off), 0, 0, 0);
        }
        return NONE;
    }

    public static BlockState block(long seed, Column c, int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT || c.deckTop() < 0) return AIR;
        if (y >= c.deckBottom() && y <= c.deckTop()) {
            if (y == c.deckTop()) {
                if (Math.floorMod(x, 64) == 0 || Math.floorMod(z, 64) == 0) return JOINT;
                int r = rand(seed, x, z, 2, 16);
                if (r < 2) return ANDESITE;
                if (r == 2) return COBBLE;
                return DECK_TOP;
            }
            return DECK_BODY;
        }
        if (c.parapet() && y > c.deckTop() && y <= c.deckTop() + PARAPET_H) return RAIL;
        if (y < c.deckBottom()) {
            if (c.pier()) return PILLAR;
            if (c.pr() > 0) {
                int t = y - (c.pcap() - FLARE_H);
                int extra = t <= 0 ? 0 : Math.min(FLARE_ADD, t * FLARE_ADD / FLARE_H);
                int r = c.pr() + extra;
                if (c.pd2() <= r * r) return PILLAR;
            }
        }
        return AIR;
    }
}
