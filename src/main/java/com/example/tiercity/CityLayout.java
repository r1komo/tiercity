package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 층상 도시 레이아웃 (v0.7: 월드 원점 중심의 단일 초대형 원판, 불규칙 조각 + 높낮이 + 제거 + 균열).
 * (params, seed, x, z) -> 컬럼 사양을 돌려주는 순수 함수 집합. 청크 경계와 무관하게 항상 같은 결과.
 *
 *  - 판: 반지름 Params.radius 의 원. 윗면 기준 y = LEVEL_BASE, 두께 THICK.
 *  - 조각: fragmentSize 격자마다 흔들린 점(site)을 두고 가장 가까운 점으로 나누는 불규칙 다각형.
 *  - 조각마다 높이 오프셋(대부분 작고 드물게 큰 단차), 일부는 제거(중앙 보호, 가장자리일수록 많이 제거).
 *  - 균열: 이웃한 두 조각 사이 일부 경계에 폭 1~3의 틈.
 *  - 기둥: 판 전체에 굵은 원형 기둥 격자(PILLAR_SPACING). 판이 없는 자리의 기둥은 판 아랫면 높이에서 끊긴다.
 * 지면은 없다. 기둥은 월드 바닥(MIN_Y)까지 내려간다.
 */
public final class CityLayout {
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 512;

    public static final int LEVEL_BASE = 128;      // 최하층 판 윗면 기준 y
    public static final int THICK = 12;            // 판 두께
    public static final int PILLAR_SPACING = 384;
    public static final int PILLAR_R = 32;
    public static final int PROTECT_R = 800;       // 중심 보호 반경: 조각이 제거되지 않음

    /** 설정 파일에서 읽어 온 값. */
    public record Params(int radius, int spawnX, int spawnZ, int fragmentSize, int heightRange,
                         int bigStep, double bigProb, double removalBase, double removalRim, double crackHalfMax) {
        public static final Params DEFAULT = new Params(80000, 300, 0, 96, 3, 10, 0.05, 0.12, 0.35, 2.0);
    }

    /** deckTop < 0 이면 판 없음, pillarTop < 0 이면 기둥 없음. */
    public record Column(int deckTop, int deckBottom, int pillarTop) {
        public boolean hasDeck() { return deckTop >= 0; }
        public boolean hasPillar() { return pillarTop >= 0; }
        public int topSolid() { return Math.max(deckTop, pillarTop); }
        public int bottomSolid() { return hasPillar() ? MIN_Y : deckBottom; }
    }

    private static final Column NONE = new Column(-1, -1, -1);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState JOINT = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState DECK_TOP = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState DECK_BODY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();

    private CityLayout() {}

    // ---------------- 해시/노이즈 ----------------
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

    private static double hash01(long seed, int a, int b, int salt) {
        return (hash(seed, a, b, salt) >>> 11) * (1.0 / (1L << 53));
    }

    /** 부드럽게 보간되는 값 노이즈 [0,1]. */
    private static double noise(long seed, double x, double z, double cell, int salt) {
        double fx = x / cell, fz = z / cell;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = hash01(seed, ix, iz, salt), b = hash01(seed, ix + 1, iz, salt);
        double c = hash01(seed, ix, iz + 1, salt), d = hash01(seed, ix + 1, iz + 1, salt);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    // ---------------- 조각 속성 ----------------
    private static boolean removed(Params p, long seed, int gx, int gz, double sx, double sz) {
        double dist = Math.sqrt(sx * sx + sz * sz);
        if (dist < PROTECT_R) return false;
        double n = 0.65 * noise(seed, sx, sz, 3072.0, 21) + 0.35 * noise(seed, sx, sz, 768.0, 22);
        double t = Math.max(0.0, Math.min(1.0, (dist / p.radius() - 0.75) / 0.25));
        double rim = t * t * (3 - 2 * t);
        double prob = p.removalBase() * (0.2 + 1.6 * n) + p.removalRim() * rim;
        return hash01(seed, gx, gz, 16) < prob;
    }

    private static int offset(Params p, long seed, int gx, int gz) {
        double tri = hash01(seed, gx, gz, 13) + hash01(seed, gx, gz, 14) - 1.0;
        int off = (int) Math.round(tri * p.heightRange());
        if (hash01(seed, gx, gz, 15) < p.bigProb()) {
            off += (hash01(seed, gx, gz, 17) < 0.5 ? -1 : 1) * p.bigStep();
        }
        return off;
    }

    /** 두 조각 사이 경계가 균열인지. e = 경계선까지의 거리. */
    private static boolean isCrack(Params p, long seed, int agx, int agz, int bgx, int bgz,
                                   double asx, double asz, double bsx, double bsz, double d1, double d2) {
        long ia = ((long) agx << 32) ^ (agz & 0xFFFFFFFFL);
        long ib = ((long) bgx << 32) ^ (bgz & 0xFFFFFFFFL);
        long lo = Math.min(ia, ib), hi = Math.max(ia, ib);
        long k = hash(seed, (int) (lo ^ (lo >>> 32)), (int) (hi ^ (hi >>> 32)), 31);
        double u = (k >>> 11) * (1.0 / (1L << 53));
        if (u >= 0.5) return false;
        double half = 0.5 + ((k >>> 40) & 0xFFFF) / 65535.0 * (p.crackHalfMax() - 0.5);
        double dab = Math.hypot(asx - bsx, asz - bsz);
        if (dab < 1e-6) return false;
        double e = (d2 * d2 - d1 * d1) / (2.0 * dab);
        return e < half;
    }

    // ---------------- 컬럼 ----------------
    public static Column column(Params p, long seed, int wx, int wz) {
        long R = p.radius();
        long r2 = (long) wx * wx + (long) wz * wz;
        boolean inDisc = r2 <= R * R;

        int S = p.fragmentSize();
        int gx0 = Math.floorDiv(wx, S), gz0 = Math.floorDiv(wz, S);
        double px = wx + 0.5, pz = wz + 0.5;

        double q1 = Double.MAX_VALUE, q2 = Double.MAX_VALUE;
        int agx = 0, agz = 0, bgx = 0, bgz = 0;
        double asx = 0, asz = 0, bsx = 0, bsz = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int gx = gx0 + i, gz = gz0 + j;
                double sx = gx * (double) S + (0.1 + 0.8 * hash01(seed, gx, gz, 1)) * S;
                double sz = gz * (double) S + (0.1 + 0.8 * hash01(seed, gx, gz, 2)) * S;
                double dx = px - sx, dz = pz - sz;
                double q = dx * dx + dz * dz;
                if (q < q1) {
                    q2 = q1; bgx = agx; bgz = agz; bsx = asx; bsz = asz;
                    q1 = q; agx = gx; agz = gz; asx = sx; asz = sz;
                } else if (q < q2) {
                    q2 = q; bgx = gx; bgz = gz; bsx = sx; bsz = sz;
                }
            }
        }

        int deckTop = -1, deckBottom = -1;
        if (inDisc && !removed(p, seed, agx, agz, asx, asz)) {
            boolean crack = false;
            if (!removed(p, seed, bgx, bgz, bsx, bsz)) {
                crack = isCrack(p, seed, agx, agz, bgx, bgz, asx, asz, bsx, bsz, Math.sqrt(q1), Math.sqrt(q2));
            }
            if (!crack) {
                deckTop = LEVEL_BASE + offset(p, seed, agx, agz);
                deckBottom = deckTop - THICK + 1;
            }
        }

        int pcx = Math.floorDiv(wx + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        int pcz = Math.floorDiv(wz + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        long ex = wx - pcx, ez = wz - pcz;
        boolean footprint = ex * ex + ez * ez <= (long) PILLAR_R * PILLAR_R
                && (long) pcx * pcx + (long) pcz * pcz <= R * R;
        int pillarTop = -1;
        if (footprint) {
            pillarTop = (deckTop >= 0 ? deckBottom : LEVEL_BASE - THICK + 1) - 1;
        }

        if (deckTop < 0 && pillarTop < 0) return NONE;
        return new Column(deckTop, deckBottom, pillarTop);
    }

    public static BlockState block(long seed, Column c, int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT) return AIR;
        if (c.hasDeck() && y >= c.deckBottom() && y <= c.deckTop()) {
            if (y == c.deckTop()) {
                if (Math.floorMod(x, 64) == 0 || Math.floorMod(z, 64) == 0) return JOINT;
                int r = rand(seed, x, z, 2, 16);
                if (r < 2) return ANDESITE;
                if (r == 2) return COBBLE;
                return DECK_TOP;
            }
            return DECK_BODY;
        }
        if (c.hasPillar() && y <= c.pillarTop()) return PILLAR;
        return AIR;
    }

    /**
     * (x, z)에서 가장 가까운 남아 있는 판 컬럼을 찾는다. 결과 {x, z, 판 윗면 y}, 없으면 null.
     * 가까운 곳은 촘촘히, 먼 곳은 성기게 훑는다.
     */
    public static int[] nearestDeck(Params p, long seed, int x, int z, int maxR) {
        Column c0 = column(p, seed, x, z);
        if (c0.hasDeck()) return new int[]{x, z, c0.deckTop()};
        int r = 4;
        while (r <= maxR) {
            int step = r <= 64 ? 4 : (r <= 512 ? 16 : 64);
            int n = Math.max(8, Math.min(256, (int) Math.ceil(2 * Math.PI * r / step)));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                int tx = x + (int) Math.round(r * Math.cos(a));
                int tz = z + (int) Math.round(r * Math.sin(a));
                Column c = column(p, seed, tx, tz);
                if (c.hasDeck()) return new int[]{tx, tz, c.deckTop()};
            }
            r += step;
        }
        return null;
    }
}
