package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 층상 도시 레이아웃 (v0.4: 거대 스케일, 바닥 없음, 원형 데크, 두꺼운 기둥).
 * (x, z) -> 컬럼 사양을 돌려주는 순수 함수 집합이므로 청크 경계와 무관하게 항상 같은 결과가 나온다.
 *
 * 평면 구조: 주기 PERIOD = CELL(구획 플랫폼) + GAP(통로 구간).
 *  - 구획(지름 CELL의 원): 구획마다 높이(tier)가 다른 원형 데크 + 아래 두꺼운 기둥 + 가장자리 난간
 *  - 통로(GAP): 인접 구획 중심선을 잇는 폭 15의 연결 통로, 두 구획 높이 사이를 경사로 보간
 *  - 교차부(GAP x GAP): 비어 있음
 * 지면은 없다. 기둥은 월드 바닥(MIN_Y)까지 내려가고 그 아래는 허공이다.
 */
public final class CityLayout {
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 512;

    public static final int CELL = 1024;
    public static final int GAP = 128;
    public static final int PERIOD = CELL + GAP;   // 1152 = 72청크
    public static final int MID = CELL / 2;        // 통로 중심선 오프셋
    public static final int WALK_HALF = 7;         // 통로 폭 = 2*7+1 = 15

    public static final int LEVEL_BASE = 128;      // 최저 층 데크 상면 y
    public static final int LEVEL_STEP = 16;
    public static final int LEVELS = 4;            // 인접 최대 높이차 48 / 통로 128칸 = 경사 0.375
    public static final int DECK_THICK = 12;
    public static final int WALK_THICK = 4;
    public static final int PARAPET_H = 2;
    public static final int EDGE_W = 2;            // 구획 가장자리 난간 두께
    public static final int RAIL_W = 2;            // 통로 난간 두께
    public static final int PILLAR_SPACING = 256;  // 두꺼운 기둥 격자 간격(구획 중심 기준)
    public static final int PILLAR_R = 20;         // 일반 기둥 반지름 (지름 약 41)
    public static final int CORE_R = 40;           // 구획 중앙 코어 기둥 반지름
    public static final int PIER_LEN = 16;         // 통로 중앙 교각 길이

    /** deckTop < 0 이면 데크 없음. */
    public record Column(int deckTop, int deckBottom, boolean pillar, boolean parapet) {
        public int topSolid() {
            if (deckTop < 0) return MIN_Y - 1;
            return deckTop + (parapet ? PARAPET_H : 0);
        }
        public int bottomSolid() {
            return pillar ? MIN_Y : deckBottom;
        }
    }

    private static final Column NONE = new Column(-1, -1, false, false);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState JOINT = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState DECK_TOP = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState DECK_BODY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState RAIL = Blocks.POLISHED_ANDESITE.defaultBlockState();

    private CityLayout() {}

    private static long hash(int a, int b, int salt) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return h;
    }

    private static int rand(int a, int b, int salt, int bound) {
        return (int) ((hash(a, b, salt) >>> 33) % bound);
    }

    public static int tierTop(int cellX, int cellZ) {
        return LEVEL_BASE + rand(cellX, cellZ, 7, LEVELS) * LEVEL_STEP;
    }

    private static int ramp(int a, int b, int g) {
        return (int) Math.round(a + (b - a) * (g + 0.5) / GAP);
    }

    /** 구획 중심 기준 256 간격 격자 위의 원형 기둥. 원 가장자리에 너무 가까운 격자점은 제외. 중앙은 굵은 코어. */
    private static boolean deckPillar(int px, int pz) {
        int dx = px - MID, dz = pz - MID;
        int gx = Math.floorDiv(dx + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        int gz = Math.floorDiv(dz + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        int lim = CELL / 2 - 64;
        if (gx * gx + gz * gz > lim * lim) return false;
        int r = (gx == 0 && gz == 0) ? CORE_R : PILLAR_R;
        int ex = dx - gx, ez = dz - gz;
        return ex * ex + ez * ez <= r * r;
    }

    /** 통로 중앙의 교각 하나 (통로 폭 전체). */
    private static boolean walkPillar(int g, int off) {
        return g >= GAP / 2 - PIER_LEN / 2 && g < GAP / 2 + PIER_LEN / 2 && off <= WALK_HALF;
    }

    public static Column column(int wx, int wz) {
        int cx = Math.floorDiv(wx, PERIOD), px = Math.floorMod(wx, PERIOD);
        int cz = Math.floorDiv(wz, PERIOD), pz = Math.floorMod(wz, PERIOD);
        boolean inX = px < CELL, inZ = pz < CELL;

        if (inX && inZ) {
            // 원형 구획: 칸 중심 기준 반지름 CELL/2 (정수 연산을 위해 좌표를 2배로 비교)
            int dx2 = 2 * px + 1 - CELL, dz2 = 2 * pz + 1 - CELL;
            int d2 = dx2 * dx2 + dz2 * dz2;
            if (d2 >= CELL * CELL) return NONE;
            int top = tierTop(cx, cz);
            int inner = CELL - 2 * EDGE_W;
            boolean rim = d2 >= inner * inner;
            boolean open = (Math.abs(pz - MID) <= WALK_HALF && Math.abs(dx2) > CELL / 2)
                    || (Math.abs(px - MID) <= WALK_HALF && Math.abs(dz2) > CELL / 2);
            return new Column(top, top - DECK_THICK + 1, deckPillar(px, pz), rim && !open);
        }
        if (!inX && inZ) { // X 방향 통로
            int off = Math.abs(pz - MID);
            if (off > WALK_HALF) return NONE;
            int g = px - CELL;
            int top = ramp(tierTop(cx, cz), tierTop(cx + 1, cz), g);
            return new Column(top, top - WALK_THICK + 1, walkPillar(g, off), off > WALK_HALF - RAIL_W);
        }
        if (inX) { // Z 방향 통로
            int off = Math.abs(px - MID);
            if (off > WALK_HALF) return NONE;
            int g = pz - CELL;
            int top = ramp(tierTop(cx, cz), tierTop(cx, cz + 1), g);
            return new Column(top, top - WALK_THICK + 1, walkPillar(g, off), off > WALK_HALF - RAIL_W);
        }
        return NONE;
    }

    public static BlockState block(Column c, int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT || c.deckTop() < 0) return AIR;
        if (y >= c.deckBottom() && y <= c.deckTop()) {
            if (y == c.deckTop()) {
                if (Math.floorMod(x, 64) == 0 || Math.floorMod(z, 64) == 0) return JOINT;
                int r = rand(x, z, 2, 16);
                if (r < 2) return ANDESITE;
                if (r == 2) return COBBLE;
                return DECK_TOP;
            }
            return DECK_BODY;
        }
        if (c.parapet() && y > c.deckTop() && y <= c.deckTop() + PARAPET_H) return RAIL;
        if (c.pillar() && y < c.deckBottom()) return PILLAR;
        return AIR;
    }
}
