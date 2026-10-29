package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 층상 도시 레이아웃 (v0.8 = 빌드 1b).
 * (params, seed, x, z) -> 컬럼 사양을 돌려주는 순수 함수 집합. 청크 경계와 무관하게 항상 같은 결과.
 *
 *  - 판: 월드 원점 중심의 단일 초대형 원판(반지름 Params.radius), 두께 THICK.
 *  - 조각: 불규칙 다각형(Voronoi). 일부는 제거, 일부 경계는 균열(군데군데 몰림).
 *  - 높이: 넓은 범위의 완만한 기울기 + 조각별 작은 흔들림 + 조각별 기울어짐 + 드문 단층.
 *  - 잔해 비탈: 낙차 3 이상인 경계의 낮은 쪽에 불규칙하게 쌓인 돌무더기(반 블록 슬랩으로 걸어 오를 수 있음).
 *  - 기둥: 굵은 원형 기둥 격자. 일부는 기울어짐. 판이 없는 자리의 기둥은 제각각 부러져 있고 철근이 솟아 있음.
 * 지면은 없다. 인공 계단은 없다.
 */
public final class CityLayout {
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 512;

    public static final int LEVEL_BASE = 128;      // 최하층 판 윗면 기준 y
    public static final int THICK = 12;            // 판 두께
    public static final int Y0 = LEVEL_BASE - THICK + 1;   // 기둥이 판에 닿는 기준 높이(판 아랫면)
    public static final int PILLAR_SPACING = 384;
    public static final int PILLAR_R = 32;
    public static final int TILT_MARGIN = 13;      // 기울어진 기둥이 옆으로 밀려나는 최대 거리 + 여유
    public static final int PROTECT_R = 800;       // 중심 보호 반경: 조각이 제거되지 않음

    /** 설정 파일에서 읽어 온 값. */
    public record Params(int radius, int spawnX, int spawnZ, int fragmentSize, int heightRange,
                         int bigStep, double bigProb, double removalBase, double removalRim,
                         double crackHalfMax, double crackChance, double terrainAmp, double tiltMax) {
        public static final Params DEFAULT = new Params(80000, 300, 0, 96, 2, 8, 0.03, 0.12, 0.35, 2.0, 0.2, 10.0, 0.06);
    }

    /** 기둥 하나의 고유 속성(좌표와 시드로 결정). */
    static final class Pillar {
        final int cx, cz;
        final double slopeX, slopeZ;   // 아래로 내려갈수록 밀려나는 비율
        final int brk;                 // 부러진 깊이
        final double planeA, planeB;   // 파단면 기울기
        Pillar(int cx, int cz, double slopeX, double slopeZ, int brk, double planeA, double planeB) {
            this.cx = cx; this.cz = cz; this.slopeX = slopeX; this.slopeZ = slopeZ;
            this.brk = brk; this.planeA = planeA; this.planeB = planeB;
        }
    }

    /**
     * deckTop < 0 이면 판 없음. talus: 잔해 비탈(baseTop 위쪽이 돌무더기). slab: 맨 위에 반 블록 슬랩.
     * pillar != null 이면 기둥 후보. pillarTop: 기둥 맨 위 y. rebarTop: 철근 맨 위 y(없으면 -1).
     */
    public record Column(int deckTop, int deckBottom, int baseTop, boolean talus, boolean slab,
                         int pillarTop, int rebarTop, Pillar pillar) {
        public boolean hasDeck() { return deckTop >= 0; }
        public boolean hasPillar() { return pillar != null; }
        public int topSolid() {
            int t = deckTop + (slab ? 1 : 0);
            if (pillar != null) t = Math.max(t, Math.max(pillarTop, rebarTop));
            return t;
        }
        public int bottomSolid() { return pillar != null ? MIN_Y : deckBottom; }
        /** 서 있을 수 있는 y(슬랩이면 슬랩 블록 위치). */
        public int standY() { return deckTop + 1 + (slab ? 1 : 0); }
    }

    private static final Column NONE = new Column(-1, -1, -1, false, false, -1, -1, null);

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState RUBBLE_SLAB = Blocks.COBBLESTONE_SLAB.defaultBlockState();
    private static final BlockState JOINT = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState DECK_TOP = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState DECK_BODY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState REBAR = Blocks.IRON_BARS.defaultBlockState();

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

    // ---------------- 조각 ----------------
    private static final class Frag {
        final int gx, gz;
        final double sx, sz;
        final boolean removed;
        final int off;
        final double tx, tz;

        Frag(Params p, long seed, int gx, int gz, double sx, double sz) {
            this.gx = gx; this.gz = gz; this.sx = sx; this.sz = sz;

            double dist = Math.sqrt(sx * sx + sz * sz);
            boolean rem = false;
            if (dist >= PROTECT_R) {
                double n = 0.65 * noise(seed, sx, sz, 3072.0, 21) + 0.35 * noise(seed, sx, sz, 768.0, 22);
                double t = Math.max(0.0, Math.min(1.0, (dist / p.radius() - 0.75) / 0.25));
                double rim = t * t * (3 - 2 * t);
                double prob = p.removalBase() * (0.2 + 1.6 * n) + p.removalRim() * rim;
                rem = hash01(seed, gx, gz, 16) < prob;
            }
            this.removed = rem;

            // 넓은 범위의 완만한 기울기 + 작은 흔들림 + 드문 단층
            double base = p.terrainAmp() * ((2 * noise(seed, sx, sz, 1200.0, 41) - 1)
                    + 0.35 * (2 * noise(seed, sx, sz, 400.0, 42) - 1));
            double tri = hash01(seed, gx, gz, 13) + hash01(seed, gx, gz, 14) - 1.0;
            int o = (int) Math.round(base + tri * p.heightRange());
            if (hash01(seed, gx, gz, 15) < p.bigProb()) {
                int sign = hash01(seed, gx, gz, 17) < 0.5 ? -1 : 1;
                o += sign * (int) Math.round(p.bigStep() * (0.75 + 0.75 * hash01(seed, gx, gz, 18)));
            }
            this.off = o;

            double ttx = 0, ttz = 0;
            if (hash01(seed, gx, gz, 19) < 0.6) {
                double ang = hash01(seed, gx, gz, 20) * 2 * Math.PI;
                double mag = p.tiltMax() * (0.3 + 0.7 * hash01(seed, gx, gz, 21));
                ttx = Math.cos(ang) * mag;
                ttz = Math.sin(ang) * mag;
            }
            this.tx = ttx; this.tz = ttz;
        }

        /** 이 조각 평면을 (x, z)까지 연장했을 때의 윗면 y. */
        int heightAt(int x, int z) {
            return LEVEL_BASE + off + (int) Math.round(tx * (x + 0.5 - sx) + tz * (z + 0.5 - sz));
        }
    }

    private static long pairKey(long seed, int agx, int agz, int bgx, int bgz) {
        long ia = ((long) agx << 32) ^ (agz & 0xFFFFFFFFL);
        long ib = ((long) bgx << 32) ^ (bgz & 0xFFFFFFFFL);
        long lo = Math.min(ia, ib), hi = Math.max(ia, ib);
        return hash(seed, (int) (lo ^ (lo >>> 32)), (int) (hi ^ (hi >>> 32)), 31);
    }

    /** 균열 여부. 균열은 군데군데 몰리고(노이즈), 대부분 폭이 좁다. */
    private static boolean isCrack(Params p, long seed, long k, Frag a, Frag b, double e) {
        double u = (k >>> 11) * (1.0 / (1L << 53));
        double chance = p.crackChance() * (0.3 + 1.4 * noise(seed, (a.sx + b.sx) / 2, (a.sz + b.sz) / 2, 1536.0, 71));
        if (u >= chance) return false;
        double w = ((k >>> 40) & 0xFFFF) / 65535.0;
        double half = 0.5 + w * w * (p.crackHalfMax() - 0.5);
        return e < half;
    }

    // ---------------- 기둥 ----------------
    private static Pillar pillar(long seed, int cx, int cz) {
        double sx = 0, sz = 0;
        if (hash01(seed, cx, cz, 91) < 0.2) {
            double deg = 2.0 + 3.0 * hash01(seed, cx, cz, 92);
            double m = Math.tan(Math.toRadians(deg));
            double ang = hash01(seed, cx, cz, 93) * 2 * Math.PI;
            sx = Math.cos(ang) * m;
            sz = Math.sin(ang) * m;
        }
        double r = hash01(seed, cx, cz, 94);
        double u = hash01(seed, cx, cz, 95);
        int brk = r < 0.4 ? (int) (u * 9) : (r < 0.8 ? 8 + (int) (u * 23) : 30 + (int) (u * 41));
        double pa = hash01(seed, cx, cz, 96) * 2 * Math.PI;
        double pm = 0.15 + 0.45 * hash01(seed, cx, cz, 97);
        return new Pillar(cx, cz, sx, sz, brk, Math.cos(pa) * pm, Math.sin(pa) * pm);
    }

    /** 높이 y에서 기둥 중심까지 거리의 제곱. */
    private static double footprintD2(Pillar pl, int x, int z, int y) {
        double sh = Math.max(0, Y0 - y);
        double dx = x - (pl.cx + pl.slopeX * sh), dz = z - (pl.cz + pl.slopeZ * sh);
        return dx * dx + dz * dz;
    }

    private static boolean inFootprint(Pillar pl, int x, int z, int y) {
        return footprintD2(pl, x, z, y) <= (double) PILLAR_R * PILLAR_R;
    }

    private static final int[] SAMPLE_Y = {Y0, 96, 72, 48, 24, 0};

    private static boolean anyFootprint(Pillar pl, int x, int z) {
        for (int y : SAMPLE_Y) {
            if (inFootprint(pl, x, z, y)) return true;
        }
        return false;
    }

    /** 판이 없는 자리의 기둥이 부러진 높이: 기둥마다 깊이가 다르고 파단면이 기울어져 있고 울퉁불퉁하다. */
    private static int breakY(long seed, Pillar pl, int x, int z) {
        double plane = pl.planeA * (x - pl.cx) + pl.planeB * (z - pl.cz);
        double jag = noise(seed, x, z, 5.0, 51);
        int by = Y0 - 1 - pl.brk - (int) Math.round(Math.max(0.0, plane) + jag * 6.0);
        return Math.max(6, by);
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

        int deckTop = -1, deckBottom = -1, baseTop = -1;
        boolean talus = false, slab = false;
        if (inDisc) {
            Frag fa = new Frag(p, seed, agx, agz, asx, asz);
            if (!fa.removed) {
                Frag fb = new Frag(p, seed, bgx, bgz, bsx, bsz);
                boolean bPresent = !fb.removed;
                double d1 = Math.sqrt(q1), d2 = Math.sqrt(q2);
                double dab = Math.hypot(asx - bsx, asz - bsz);
                double e = dab < 1e-6 ? 1e9 : (d2 * d2 - d1 * d1) / (2.0 * dab);
                long key = pairKey(seed, agx, agz, bgx, bgz);

                boolean crack = bPresent && isCrack(p, seed, key, fa, fb, e);
                if (!crack) {
                    int topA = fa.heightAt(wx, wz);
                    int top = topA;
                    baseTop = topA;
                    if (bPresent && dab >= 1e-6) {
                        int topB = fb.heightAt(wx, wz);
                        int d = topB - topA;
                        if (d >= 3) {
                            // 높은 쪽 절벽 아래에 쌓인 잔해 비탈: 경계를 따라 부채꼴로 군데군데
                            double ux = -(bsz - asz) / dab, uz = (bsx - asx) / dab;
                            double t = (px - (asx + bsx) / 2) * ux + (pz - (asz + bsz) / 2) * uz;
                            int keyLo = (int) (key ^ (key >>> 32));
                            double shift = ((key >>> 11) & 0xFFFF) / 65535.0 * 24.0;
                            double tt = (t + shift) / 24.0;
                            int seg = (int) Math.floor(tt);
                            double f = tt - seg;
                            if (hash01(seed, keyLo, seg, 7) < 0.55) {
                                double taper = Math.pow(Math.max(Math.sin(Math.PI * f), 0.0), 0.7);
                                double jit = 0.75 + 0.5 * noise(seed, wx, wz, 9.0, 61);
                                double leff = 2.0 * d * taper * jit;
                                if (e < leff) {
                                    double v = topA + d * (1.0 - e / leff);
                                    v += (noise(seed, wx, wz, 5.0, 62) - 0.5) * 0.8;
                                    v = Math.max(topA, Math.min(topB, v));
                                    double hq = Math.floor(v * 2) / 2;
                                    int yb = (int) Math.floor(hq);
                                    slab = (hq - yb) >= 0.5;
                                    top = yb;
                                    talus = top > topA || slab;
                                }
                            }
                        }
                    }
                    deckTop = top;
                    deckBottom = baseTop - THICK + 1;
                }
            }
        }

        Pillar pil = null;
        int pillarTop = -1, rebarTop = -1;
        int pcx = Math.floorDiv(wx + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        int pcz = Math.floorDiv(wz + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        long ex = wx - pcx, ez = wz - pcz;
        long lim = PILLAR_R + TILT_MARGIN;
        if (ex * ex + ez * ez <= lim * lim && (long) pcx * pcx + (long) pcz * pcz <= R * R) {
            Pillar cand = pillar(seed, pcx, pcz);
            if (anyFootprint(cand, wx, wz)) {
                pil = cand;
                if (deckTop >= 0) {
                    pillarTop = deckBottom - 1;
                } else {
                    pillarTop = breakY(seed, pil, wx, wz);
                    // 부러진 윗면에서 솟은 철근
                    if (inFootprint(pil, wx, wz, pillarTop) && footprintD2(pil, wx, wz, pillarTop) <= (PILLAR_R - 3.0) * (PILLAR_R - 3.0)
                            && hash01(seed, wx, wz, 81) < 0.03) {
                        rebarTop = pillarTop + 2 + rand(seed, wx, wz, 82, 5);
                    }
                }
            }
        }

        if (deckTop < 0 && pil == null) return NONE;
        return new Column(deckTop, deckBottom, baseTop, talus, slab, pillarTop, rebarTop, pil);
    }

    private static BlockState rubble(long seed, int x, int y, int z) {
        int r = rand(seed, x * 31 + y, z, 3, 10);
        if (r < 4) return COBBLE;
        if (r < 7) return ANDESITE;
        if (r < 9) return STONE;
        return CRACKED;
    }

    public static BlockState block(long seed, Column c, int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT) return AIR;
        if (c.hasDeck()) {
            if (y >= c.deckBottom() && y <= c.deckTop()) {
                if (c.talus() && y > c.baseTop()) return rubble(seed, x, y, z);
                if (y == c.deckTop()) {
                    if (Math.floorMod(x, 64) == 0 || Math.floorMod(z, 64) == 0) return JOINT;
                    int r = rand(seed, x, z, 2, 16);
                    if (r < 2) return ANDESITE;
                    if (r == 2) return COBBLE;
                    return DECK_TOP;
                }
                return DECK_BODY;
            }
            if (c.slab() && y == c.deckTop() + 1) return RUBBLE_SLAB;
        }
        Pillar pl = c.pillar();
        if (pl != null) {
            if (y <= c.pillarTop()) {
                double d2 = footprintD2(pl, x, z, y);
                if (d2 <= (double) PILLAR_R * PILLAR_R) {
                    // 바깥 2블록은 군데군데 파여 있음
                    double inner = PILLAR_R - 2.0;
                    if (d2 > inner * inner && hash01(seed, x, z, y + 1000) < 0.10) return AIR;
                    return PILLAR;
                }
            } else if (y <= c.rebarTop()) {
                return REBAR;
            }
        }
        return AIR;
    }

    /**
     * (x, z)에서 가장 가까운 남아 있는 판 컬럼을 찾는다. 결과 {x, z, 서 있을 y}, 없으면 null.
     * 가까운 곳은 촘촘히, 먼 곳은 성기게 훑는다.
     */
    public static int[] nearestDeck(Params p, long seed, int x, int z, int maxR) {
        Column c0 = column(p, seed, x, z);
        if (c0.hasDeck()) return new int[]{x, z, c0.standY()};
        int r = 4;
        while (r <= maxR) {
            int step = r <= 64 ? 4 : (r <= 512 ? 16 : 64);
            int n = Math.max(8, Math.min(256, (int) Math.ceil(2 * Math.PI * r / step)));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                int tx = x + (int) Math.round(r * Math.cos(a));
                int tz = z + (int) Math.round(r * Math.sin(a));
                Column c = column(p, seed, tx, tz);
                if (c.hasDeck()) return new int[]{tx, tz, c.standY()};
            }
            r += step;
        }
        return null;
    }
}
