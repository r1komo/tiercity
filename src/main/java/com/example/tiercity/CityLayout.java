package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 층상 도시 레이아웃. (x, z) -> 컬럼 사양을 돌려주는 순수 함수 집합이므로
 * 청크 경계와 무관하게 항상 같은 결과가 나온다.
 *
 * 평면 구조: 주기 PERIOD = CELL(구획 플랫폼) + GAP(통로 구간).
 *  - 구획(CELL x CELL): 구획마다 층(tier)이 다른 데크 + 아래 기둥 + 가장자리 난간
 *  - 통로(GAP): 인접 구획 중심선을 잇는 폭 5의 연결 통로, 두 구획 높이 사이를 경사로 보간
 *  - 교차부(GAP x GAP): 비어 있음 (바닥만 존재)
 */
public final class CityLayout {
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;

    public static final int FLOOR_Y = 40;      // 최하층 지면
    public static final int CELL = 48;
    public static final int GAP = 24;
    public static final int PERIOD = CELL + GAP;
    public static final int MID = CELL / 2;    // 통로 중심선 오프셋
    public static final int WALK_HALF = 2;     // 통로 폭 = 2*2+1 = 5

    public static final int LEVEL_BASE = 72;   // 최저 층 데크 상면 y
    public static final int LEVEL_STEP = 4;
    public static final int LEVELS = 4;        // 인접 최대 높이차 12 / 통로 24칸 = 경사 0.5 이하
    public static final int DECK_THICK = 6;
    public static final int WALK_THICK = 2;

    /** deckTop < 0 이면 데크 없음. */
    public record Column(int deckTop, int deckBottom, boolean pillar, boolean parapet) {
        public int topSolid() {
            if (deckTop < 0) return FLOOR_Y;
            return Math.max(FLOOR_Y, deckTop + (parapet ? 1 : 0));
        }
    }

    private static final Column NONE = new Column(-1, -1, false, false);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState DECK_TOP = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState DECK_BODY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState CURB = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState();

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

    private static boolean walkPillar(int g, int off) {
        return (g % 8 == 3 || g % 8 == 4) && off <= 1;
    }

    public static Column column(int wx, int wz) {
        int cx = Math.floorDiv(wx, PERIOD), px = Math.floorMod(wx, PERIOD);
        int cz = Math.floorDiv(wz, PERIOD), pz = Math.floorMod(wz, PERIOD);
        boolean inX = px < CELL, inZ = pz < CELL;

        if (inX && inZ) {
            int top = tierTop(cx, cz);
            boolean pillar = px % 12 >= 5 && px % 12 <= 6 && pz % 12 >= 5 && pz % 12 <= 6;
            boolean ex = px == 0 || px == CELL - 1;
            boolean ez = pz == 0 || pz == CELL - 1;
            boolean openX = Math.abs(pz - MID) <= WALK_HALF;
            boolean openZ = Math.abs(px - MID) <= WALK_HALF;
            boolean parapet = (ex && !openX) || (ez && !openZ);
            return new Column(top, top - DECK_THICK + 1, pillar, parapet);
        }
        if (!inX && inZ) { // X 방향 통로
            int off = Math.abs(pz - MID);
            if (off > WALK_HALF) return NONE;
            int g = px - CELL;
            int top = ramp(tierTop(cx, cz), tierTop(cx + 1, cz), g);
            return new Column(top, top - WALK_THICK + 1, walkPillar(g, off), off == WALK_HALF);
        }
        if (inX) { // Z 방향 통로
            int off = Math.abs(px - MID);
            if (off > WALK_HALF) return NONE;
            int g = pz - CELL;
            int top = ramp(tierTop(cx, cz), tierTop(cx, cz + 1), g);
            return new Column(top, top - WALK_THICK + 1, walkPillar(g, off), off == WALK_HALF);
        }
        return NONE;
    }

    public static BlockState block(Column c, int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT) return AIR;
        if (y == MIN_Y) return BEDROCK;
        if (y < FLOOR_Y) return STONE;
        if (y == FLOOR_Y) {
            return switch (rand(x, z, 1, 8)) {
                case 0 -> GRAVEL;
                case 1 -> COARSE;
                case 2 -> ANDESITE;
                case 3 -> COBBLE;
                default -> STONE;
            };
        }
        if (c.deckTop() >= 0) {
            if (y >= c.deckBottom() && y <= c.deckTop()) {
                if (y == c.deckTop()) {
                    int r = rand(x, z, 2, 16);
                    if (r < 2) return ANDESITE;
                    if (r == 2) return COBBLE;
                    return DECK_TOP;
                }
                return DECK_BODY;
            }
            if (y == c.deckTop() + 1 && c.parapet()) return CURB;
            if (c.pillar() && y < c.deckBottom()) return PILLAR;
        }
        return AIR;
    }
}
