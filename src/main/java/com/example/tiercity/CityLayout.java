package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 층상 도시 레이아웃 (v2 = 빌드 2a).
 * (params, seed, x, z) -> 컬럼 사양을 돌려주는 순수 함수 집합. 청크 경계와 무관하게 항상 같은 결과.
 *
 *  - 층: 월드 원점 중심의 원판이 최대 4겹. 반지름은 위로 갈수록 로그 함수꼴로 줄어든다(Params.layerRadius).
 *  - 조각: 휘어진 격자(줄마다 어긋나고 폭이 다른 사각형, 경계선이 완만히 휘어 있음). 모든 층이 같은 조각 배치를 공유한다.
 *  - 구멍: 일부는 모든 층이 공유(빛 우물), 나머지는 층마다 독립. 위층에는 스폰 위쪽에 큰 개구부.
 *  - 높이/균열/잔해 비탈: 층마다 독립. 아랫면은 조각과 무관하게 이어지고 윗면만 조각별로 오르내린다.
 *  - 기둥: 모든 층이 격자를 공유. 기둥은 y=0부터 그 위치를 덮는 가장 높은 층의 판 아랫면까지 이어지는 하나의 탑이다.
 *    아래로 갈수록 굵어지고, 일부는 위쪽 128블록에서 기울어져 있다. 판이 없는 자리의 기둥은 부러져 있고 철근 묶음이 솟아 있다.
 * 지면은 없다. 인공 계단은 없다.
 */
public final class CityLayout {
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 768;

    public static final int LEVEL_BASE = 128;      // 최하층 판 윗면 기준 y
    public static final int LAYER_SPACING = 160;   // 층 간격
    public static final int MAX_LAYERS = 4;
    public static final int THICK = 16;            // 판 기준 두께(아랫면은 이어지고 윗면만 조각별로 오르내림)
    public static final int MIN_THICK = 5;         // 가장 낮게 가라앉은 곳의 최소 두께

    public static final int PILLAR_SPACING = 384;
    public static final int PILLAR_R = 32;         // 기둥 맨 위 반지름
    public static final double PILLAR_TAPER = 0.025;   // 아래로 내려갈수록 늘어나는 반지름(블록당)
    public static final int PILLAR_MAX_R = 50;
    public static final int TILT_SPAN = 128;       // 기울어짐이 적용되는 위쪽 구간 길이
    public static final int TILT_MARGIN = 13;      // 기울어진 기둥이 옆으로 밀려나는 최대 거리 + 여유
    public static final double PIT_THRESHOLD = 0.84;   // 값이 클수록 기둥 표면 파임이 드묾
    public static final int REBAR_DIST = 19;       // 철근 묶음이 기둥 중심에서 떨어진 거리(4방향)

    public static final int PROTECT_R = 800;       // 최하층 중심 보호 반경: 조각이 제거되지 않음
    public static final int SKY_R = 600;           // 위층의 스폰 위쪽 개구부 반경
    public static final double TALUS_MAX_NEIGHBORS = 1;   // (설명용) 조각마다 비탈이 생기는 방향은 하나

    /** 설정 파일에서 읽어 온 값. radius는 최하층 반지름. */
    public record Params(int radius, int spawnX, int spawnZ, int fragmentSize, int heightRange,
                         int bigStep, double bigProb, double removalBase, double removalRim,
                         double crackHalfMax, double crackChance, double terrainAmp, double tiltMax,
                         double talusChance, int talusMaxHeight, int layers, int topRadius) {
        public static final Params DEFAULT =
                new Params(80000, 300, 0, 96, 2, 8, 0.03, 0.12, 0.35, 2.0, 0.2, 10.0, 0.06, 0.4, 5, 4, 4000);

        /** k층(0 = 최하층)의 반지름: R(k) = R0 - b*ln(1+k), 최상층에서 topRadius. */
        public double layerRadius(int k) {
            if (layers <= 1) return radius;
            double top = Math.min(topRadius, radius);
            double b = (radius - top) / Math.log(layers);
            return radius - b * Math.log(1 + k);
        }
    }

    /** 기둥 하나의 고유 속성(좌표와 시드로 결정). */
    static final class Pillar {
        final int cx, cz;
        final double slopeX, slopeZ;   // 위쪽 TILT_SPAN 구간에서 아래로 갈수록 밀려나는 비율
        final int brk;                 // 부러진 깊이
        final double planeA, planeB;   // 파단면 기울기
        final double rebarAng;         // 철근 묶음 4개의 배치 회전각
        Pillar(int cx, int cz, double slopeX, double slopeZ, int brk, double planeA, double planeB, double rebarAng) {
            this.cx = cx; this.cz = cz; this.slopeX = slopeX; this.slopeZ = slopeZ;
            this.brk = brk; this.planeA = planeA; this.planeB = planeB; this.rebarAng = rebarAng;
        }
    }

    /** 한 층의 판 한 컬럼. talus: 잔해 비탈(baseTop 위쪽이 돌무더기). slab: 맨 위에 반 블록 슬랩. */
    public record Layer(int top, int bottom, int baseTop, boolean talus, boolean slab) {
        public int maxY() { return top + (slab ? 1 : 0); }
        /** 서 있을 수 있는 y(슬랩이면 슬랩 블록 위치). */
        public int standY() { return top + 1 + (slab ? 1 : 0); }
    }

    /** layers[k] == null 이면 그 층에는 판이 없음. pillar != null 이면 기둥 후보 컬럼. */
    public record Column(Layer[] layers, int pillarTop, int rebarTop, Pillar pillar, int pillarYtop) {
        public boolean hasDeck() {
            for (Layer l : layers) if (l != null) return true;
            return false;
        }
        public boolean hasPillar() { return pillar != null; }
        public boolean isEmpty() { return pillar == null && !hasDeck(); }
        public int topSolid() {
            int t = -1;
            for (Layer l : layers) if (l != null) t = Math.max(t, l.maxY());
            if (pillar != null) t = Math.max(t, Math.max(pillarTop, rebarTop));
            return t;
        }
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState RUBBLE_SLAB = Blocks.COBBLESTONE_SLAB.defaultBlockState();
    private static final BlockState DECK_TOP = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState DECK_BODY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState REBAR = Blocks.RAW_IRON_BLOCK.defaultBlockState();

    private CityLayout() {}

    public static int layerBase(int k) { return LEVEL_BASE + k * LAYER_SPACING; }

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

    private static double noise3(long seed, double x, double y, double z, double cell, int salt) {
        double fx = x / cell, fy = y / cell, fz = z / cell;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy), iz = (int) Math.floor(fz);
        double tx = fx - ix, ty = fy - iy, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        tz = tz * tz * (3 - 2 * tz);
        double c000 = hash01(seed, ix, iz, iy * 131 + salt), c100 = hash01(seed, ix + 1, iz, iy * 131 + salt);
        double c010 = hash01(seed, ix, iz + 1, iy * 131 + salt), c110 = hash01(seed, ix + 1, iz + 1, iy * 131 + salt);
        double c001 = hash01(seed, ix, iz, (iy + 1) * 131 + salt), c101 = hash01(seed, ix + 1, iz, (iy + 1) * 131 + salt);
        double c011 = hash01(seed, ix, iz + 1, (iy + 1) * 131 + salt), c111 = hash01(seed, ix + 1, iz + 1, (iy + 1) * 131 + salt);
        double x00 = c000 * (1 - tx) + c100 * tx, x10 = c010 * (1 - tx) + c110 * tx;
        double x01 = c001 * (1 - tx) + c101 * tx, x11 = c011 * (1 - tx) + c111 * tx;
        double y0 = x00 * (1 - tz) + x10 * tz, y1 = x01 * (1 - tz) + x11 * tz;
        return y0 * (1 - ty) + y1 * ty;
    }

    // ---------------- 휘어진 격자 조각 ----------------
    private static double rowOff(long seed, int j, int S) { return hash01(seed, j, 0, 205) * S; }

    private static double rowW(long seed, int j, int S) { return S * (0.8 + 0.6 * hash01(seed, j, 0, 206)); }

    private static int cellIndex(long seed, int S, double u, int row) {
        return (int) Math.floor((u + rowOff(seed, row, S)) / rowW(seed, row, S));
    }

    private static double centerU(long seed, int S, int i, int row) {
        return (i + 0.5) * rowW(seed, row, S) - rowOff(seed, row, S);
    }

    /** 한 컬럼이 속한 조각(A), 가장 가까운 경계까지의 거리(e)와 그 너머의 조각(B). 층과 무관. */
    private static final class Hit {
        int i, j, bi, bj, dir;     // dir: 0=+x 1=-x 2=+z 3=-z (B 방향)
        double sx, sz, bsx, bsz, e, t;
    }

    private static Hit locate(long seed, int S, int wx, int wz) {
        double px = wx + 0.5, pz = wz + 0.5;
        double u = px + 0.30 * S * (2 * noise(seed, px, pz, 230.0, 201) - 1) + 0.10 * S * (2 * noise(seed, px, pz, 70.0, 203) - 1);
        double v = pz + 0.30 * S * (2 * noise(seed, px, pz, 230.0, 202) - 1) + 0.10 * S * (2 * noise(seed, px, pz, 70.0, 204) - 1);
        int j = (int) Math.floor(v / S);
        double w = rowW(seed, j, S);
        double uu = u + rowOff(seed, j, S);
        int i = (int) Math.floor(uu / w);
        double dl = uu - i * w, dr = (i + 1) * w - uu, dd = v - j * S, du = (j + 1) * S - v;
        double e = dl;
        int dir = 1;
        if (dr < e) { e = dr; dir = 0; }
        if (dd < e) { e = dd; dir = 3; }
        if (du < e) { e = du; dir = 2; }
        Hit h = new Hit();
        h.i = i; h.j = j; h.e = e; h.dir = dir;
        switch (dir) {
            case 0: h.bi = i + 1; h.bj = j; break;
            case 1: h.bi = i - 1; h.bj = j; break;
            case 2: h.bj = j + 1; h.bi = cellIndex(seed, S, u, h.bj); break;
            default: h.bj = j - 1; h.bi = cellIndex(seed, S, u, h.bj); break;
        }
        h.t = dir <= 1 ? v : u;
        h.sx = centerU(seed, S, i, j);
        h.sz = (j + 0.5) * S;
        h.bsx = centerU(seed, S, h.bi, h.bj);
        h.bsz = (h.bj + 0.5) * S;
        return h;
    }

    // ---------------- 조각 속성(층별) ----------------
    /** 구멍 여부. 절반은 모든 층이 공유(빛 우물), 절반은 층마다 독립. 최하층은 중심 보호, 위층은 스폰 위가 열려 있음. */
    private static boolean computeRemoved(Params p, long seed, int k, int i, int j, double sx, double sz, double rk) {
        double dist = Math.sqrt(sx * sx + sz * sz);
        if (k == 0) {
            if (dist < PROTECT_R) return false;
        } else if (Math.hypot(sx - p.spawnX(), sz - p.spawnZ()) < SKY_R) {
            return true;
        }
        double n = 0.65 * noise(seed, sx, sz, 3072.0, 21) + 0.35 * noise(seed, sx, sz, 768.0, 22);
        double t = Math.max(0.0, Math.min(1.0, (dist / rk - 0.75) / 0.25));
        double rim = t * t * (3 - 2 * t);
        double prob = p.removalBase() * (0.2 + 1.6 * n) + p.removalRim() * rim;
        boolean shared = hash01(seed, i, j, 16) < prob * 0.5;
        boolean indep = hash01(seed, i, j, 16 + 1000 * (k + 1)) < prob * 0.5;
        return shared || indep;
    }

    private static final class Frag {
        final boolean removed;
        final int off, base;
        final double tx, tz, sx, sz;

        Frag(Params p, long seed, int k, int i, int j, double sx, double sz, double rk) {
            this.sx = sx;
            this.sz = sz;
            this.base = layerBase(k);
            this.removed = computeRemoved(p, seed, k, i, j, sx, sz, rk);
            int sk = 1000 * k;
            double basef = p.terrainAmp() * ((2 * noise(seed, sx, sz, 1200.0, 41 + 100 * k) - 1)
                    + 0.35 * (2 * noise(seed, sx, sz, 400.0, 42 + 100 * k) - 1));
            double tri = hash01(seed, i, j, 13 + sk) + hash01(seed, i, j, 14 + sk) - 1.0;
            int o = (int) Math.round(basef + tri * p.heightRange());
            if (hash01(seed, i, j, 15 + sk) < p.bigProb()) {
                int sign = hash01(seed, i, j, 17 + sk) < 0.5 ? -1 : 1;
                o += sign * (int) Math.round(p.bigStep() * (0.75 + 0.75 * hash01(seed, i, j, 18 + sk)));
            }
            this.off = o;
            double ttx = 0, ttz = 0;
            if (hash01(seed, i, j, 19 + sk) < 0.6) {
                double ang = hash01(seed, i, j, 20 + sk) * 2 * Math.PI;
                double mag = p.tiltMax() * (0.3 + 0.7 * hash01(seed, i, j, 21 + sk));
                ttx = Math.cos(ang) * mag;
                ttz = Math.sin(ang) * mag;
            }
            this.tx = ttx;
            this.tz = ttz;
        }

        int heightAt(int x, int z) {
            return base + off + (int) Math.round(tx * (x + 0.5 - sx) + tz * (z + 0.5 - sz));
        }
    }

    private static boolean cellPresent(Params p, long seed, int k, int S, int ci, int cj, double rk) {
        double sx = centerU(seed, S, ci, cj), sz = (cj + 0.5) * S;
        return !computeRemoved(p, seed, k, ci, cj, sx, sz, rk);
    }

    /** 이웃(좌우 + 위아래 줄의 겹치는 칸) 중 남은 조각이 2개 미만이면 외딴 조각(떠 있는 섬). */
    private static boolean isolated(Params p, long seed, int k, Hit h, double rk) {
        int S = p.fragmentSize();
        int present = 0;
        if (cellPresent(p, seed, k, S, h.i - 1, h.j, rk)) present++;
        if (cellPresent(p, seed, k, S, h.i + 1, h.j, rk)) present++;
        if (present >= 2) return false;
        double w = rowW(seed, h.j, S);
        for (int dj = -1; dj <= 1; dj += 2) {
            int row = h.j + dj;
            int last = Integer.MIN_VALUE;
            for (int s = -1; s <= 1; s += 2) {
                int ci = cellIndex(seed, S, h.sx + s * w / 4.0, row);
                if (ci == last) continue;
                last = ci;
                if (cellPresent(p, seed, k, S, ci, row, rk)) {
                    present++;
                    if (present >= 2) return false;
                }
            }
        }
        return true;
    }

    private static long pairKey(long seed, int k, int ai, int aj, int bi, int bj) {
        long ia = ((long) ai << 32) ^ (aj & 0xFFFFFFFFL);
        long ib = ((long) bi << 32) ^ (bj & 0xFFFFFFFFL);
        long lo = Math.min(ia, ib), hi = Math.max(ia, ib);
        return hash(seed, (int) (lo ^ (lo >>> 32)), (int) (hi ^ (hi >>> 32)), 31 + 1000 * k);
    }

    private static boolean isCrack(Params p, long seed, int k, long key, Frag a, Frag b, double e) {
        double u = (key >>> 11) * (1.0 / (1L << 53));
        double chance = p.crackChance() * (0.3 + 1.4 * noise(seed, (a.sx + b.sx) / 2, (a.sz + b.sz) / 2, 1536.0, 71 + 100 * k));
        if (u >= chance) return false;
        double w = ((key >>> 40) & 0xFFFF) / 65535.0;
        double half = 0.5 + w * w * (p.crackHalfMax() - 0.5);
        return e < half;
    }

    /** 조각마다 비탈이 생기는 방향은 하나(4방향 중). 움푹 들어간 조각에 비탈이 사방에서 몰리는 것을 막는다. */
    private static boolean talusFacing(long seed, int k, int i, int j, int dir) {
        return dir == (int) (hash01(seed, i, j, 111 + 1000 * k) * 4) % 4;
    }

    private static Layer computeLayer(Params p, long seed, int k, Hit h, int wx, int wz) {
        double rk = p.layerRadius(k);
        double r2 = (double) wx * wx + (double) wz * wz;
        if (r2 > rk * rk) return null;
        Frag fa = new Frag(p, seed, k, h.i, h.j, h.sx, h.sz, rk);
        if (fa.removed || isolated(p, seed, k, h, rk)) return null;
        Frag fb = new Frag(p, seed, k, h.bi, h.bj, h.bsx, h.bsz, rk);
        boolean bPresent = !fb.removed;
        long key = pairKey(seed, k, h.i, h.j, h.bi, h.bj);
        if (bPresent && isCrack(p, seed, k, key, fa, fb, h.e)) return null;

        // 아랫면은 조각과 무관하게 이어진다(완만한 기복만 따라감). 윗면이 올라간 조각은 두꺼워져서 떠 보이지 않는다.
        double baseCol = p.terrainAmp() * ((2 * noise(seed, wx, wz, 1200.0, 41 + 100 * k) - 1)
                + 0.35 * (2 * noise(seed, wx, wz, 400.0, 42 + 100 * k) - 1));
        int bottom = layerBase(k) - THICK + 1 + (int) Math.round(baseCol);
        int minTop = bottom + MIN_THICK - 1;
        int topA = Math.max(fa.heightAt(wx, wz), minTop);
        int top = topA;
        boolean talus = false, slab = false;

        if (bPresent) {
            int topB = Math.max(fb.heightAt(wx, wz), minTop);
            int d = topB - topA;
            if (d >= 3 && talusFacing(seed, k, h.i, h.j, h.dir)) {
                int keyLo = (int) (key ^ (key >>> 32));
                double shift = ((key >>> 11) & 0xFFFF) / 65535.0 * 24.0;
                double tt = (h.t + shift) / 24.0;
                int seg = (int) Math.floor(tt);
                double f = tt - seg;
                if (hash01(seed, keyLo, seg, 7 + 1000 * k) < p.talusChance()) {
                    double taper = Math.pow(Math.max(Math.sin(Math.PI * f), 0.0), 0.7);
                    double jit = 0.75 + 0.5 * noise(seed, wx, wz, 9.0, 61);
                    int hp = Math.min(d, p.talusMaxHeight());   // 쌓이는 높이 상한(큰 절벽은 윗부분이 그대로 남음)
                    double leff = 2.0 * hp * taper * jit;
                    if (h.e < leff) {
                        double v = topA + hp * (1.0 - h.e / leff);
                        v += (noise(seed, wx, wz, 5.0, 62) - 0.5) * 0.8;
                        v = Math.max(topA, Math.min(topA + hp, v));
                        double hq = Math.floor(v * 2) / 2;
                        int yb = (int) Math.floor(hq);
                        slab = (hq - yb) >= 0.5;
                        top = yb;
                        talus = top > topA || slab;
                    }
                }
            }
        }
        return new Layer(top, bottom, topA, talus, slab);
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
        double ra = hash01(seed, cx, cz, 98) * 2 * Math.PI;
        return new Pillar(cx, cz, sx, sz, brk, Math.cos(pa) * pm, Math.sin(pa) * pm, ra);
    }

    /** 높이 y에서 기둥 반지름: 아래로 갈수록 굵어진다. */
    private static double radiusAt(int ytop, int y) {
        return Math.min(PILLAR_MAX_R, PILLAR_R + PILLAR_TAPER * Math.max(0, ytop - y));
    }

    private static double footprintD2(Pillar pl, int x, int z, int y, int ytop) {
        double sh = Math.min(TILT_SPAN, Math.max(0, ytop - y));
        double dx = x - (pl.cx + pl.slopeX * sh), dz = z - (pl.cz + pl.slopeZ * sh);
        return dx * dx + dz * dz;
    }

    private static boolean inFootprint(Pillar pl, int x, int z, int y, int ytop) {
        double r = radiusAt(ytop, y);
        return footprintD2(pl, x, z, y, ytop) <= r * r;
    }

    private static boolean anyFootprint(Pillar pl, int x, int z, int ytop) {
        for (int y = ytop; y > 0; y -= 32) {
            if (inFootprint(pl, x, z, y, ytop)) return true;
        }
        return inFootprint(pl, x, z, 0, ytop);
    }

    /** 판이 없는 자리의 기둥이 부러진 높이: 기둥마다 깊이가 다르고 파단면이 기울어져 있고 울퉁불퉁하다. */
    private static int breakY(long seed, Pillar pl, int x, int z, int ytop) {
        double plane = pl.planeA * (x - pl.cx) + pl.planeB * (z - pl.cz);
        double jag = noise(seed, x, z, 5.0, 51);
        int by = ytop - 1 - pl.brk - (int) Math.round(Math.max(0.0, plane) + jag * 6.0);
        return Math.max(6, by);
    }

    /**
     * 부러진 기둥의 철근: 기둥마다 정해진 4곳(기둥 중심에서 REBAR_DIST, 90도 간격)에 3x3 묶음.
     * 묶음별로 존재 여부(75%)와 높이(윗면 위로 5~14)가 정해져 있다. 없으면 -1.
     */
    private static int rebarTopAt(long seed, Pillar pl, int x, int z, int pillarTop, int ytop) {
        for (int k = 0; k < 4; k++) {
            double ang = pl.rebarAng + k * Math.PI / 2;
            int bx = pl.cx + (int) Math.round(REBAR_DIST * Math.cos(ang)) - 1;
            int bz = pl.cz + (int) Math.round(REBAR_DIST * Math.sin(ang)) - 1;
            if (x < bx || x > bx + 2 || z < bz || z > bz + 2) continue;
            if (hash01(seed, pl.cx + k * 7, pl.cz, 83) >= 0.75) return -1;
            int by = breakY(seed, pl, bx + 1, bz + 1, ytop);
            double rr = radiusAt(ytop, by) - 3.0;
            double lim = rr * rr;
            if (footprintD2(pl, bx, bz, by, ytop) > lim || footprintD2(pl, bx + 2, bz + 2, by, ytop) > lim
                    || footprintD2(pl, bx, bz + 2, by, ytop) > lim || footprintD2(pl, bx + 2, bz, by, ytop) > lim) return -1;
            int h = 5 + rand(seed, pl.cx + k * 7, pl.cz, 84, 10);
            return Math.max(by + h, pillarTop + 1);
        }
        return -1;
    }

    // ---------------- 컬럼 ----------------
    public static Column column(Params p, long seed, int wx, int wz) {
        int n = p.layers();
        Layer[] layers = new Layer[n];
        long r2 = (long) wx * wx + (long) wz * wz;
        long R0 = p.radius();
        long lim0 = R0 + PILLAR_MAX_R + TILT_MARGIN + PILLAR_SPACING;
        if (r2 > lim0 * lim0) return new Column(layers, -1, -1, null, 0);

        Hit h = locate(seed, p.fragmentSize(), wx, wz);
        for (int k = 0; k < n; k++) layers[k] = computeLayer(p, seed, k, h, wx, wz);

        Pillar pil = null;
        int pillarTop = -1, rebarTop = -1, pYtop = 0;
        int pcx = Math.floorDiv(wx + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        int pcz = Math.floorDiv(wz + PILLAR_SPACING / 2, PILLAR_SPACING) * PILLAR_SPACING;
        long ex = wx - pcx, ez = wz - pcz;
        long lim = PILLAR_MAX_R + TILT_MARGIN;
        if (ex * ex + ez * ez <= lim * lim) {
            long c2 = (long) pcx * pcx + (long) pcz * pcz;
            int topLayer = -1;
            for (int k = n - 1; k >= 0; k--) {
                double rk = p.layerRadius(k);
                if (c2 <= rk * rk) { topLayer = k; break; }
            }
            if (topLayer >= 0) {
                int ytop = layerBase(topLayer) - THICK + 1;
                Pillar cand = pillar(seed, pcx, pcz);
                if (anyFootprint(cand, wx, wz, ytop)) {
                    pil = cand;
                    pYtop = ytop;
                    Layer lt = layers[topLayer];
                    if (lt != null) {
                        pillarTop = lt.bottom() - 1;
                    } else {
                        pillarTop = breakY(seed, pil, wx, wz, ytop);
                        rebarTop = rebarTopAt(seed, pil, wx, wz, pillarTop, ytop);
                    }
                }
            }
        }
        return new Column(layers, pillarTop, rebarTop, pil, pYtop);
    }

    /** k층 하나만 계산(스폰/탐색용). 판이 없으면 null. */
    public static Layer layerAt(Params p, long seed, int k, int wx, int wz) {
        double rk = p.layerRadius(k);
        if ((double) wx * wx + (double) wz * wz > rk * rk) return null;
        Hit h = locate(seed, p.fragmentSize(), wx, wz);
        return computeLayer(p, seed, k, h, wx, wz);
    }

    /** 시험용: (x, z)의 조각 격자 좌표 {i, j, bi, bj}. */
    static int[] debugCell(Params p, long seed, int wx, int wz) {
        Hit h = locate(seed, p.fragmentSize(), wx, wz);
        return new int[]{h.i, h.j, h.bi, h.bj, h.dir};
    }

    // ---------------- 블록 ----------------
    private static BlockState rubble(long seed, int x, int y, int z) {
        int r = rand(seed, x * 31 + y, z, 3, 10);
        if (r < 4) return COBBLE;
        if (r < 7) return ANDESITE;
        if (r < 9) return STONE;
        return CRACKED;
    }

    public static BlockState deckBlock(long seed, Layer l, int x, int y, int z) {
        if (y < l.bottom() || y > l.maxY()) return AIR;
        if (l.slab() && y == l.top() + 1) return RUBBLE_SLAB;
        if (l.talus() && y > l.baseTop()) return rubble(seed, x, y, z);
        if (y == l.top()) {
            int r = rand(seed, x, z, 2, 16);
            if (r < 2) return ANDESITE;
            if (r == 2) return COBBLE;
            return DECK_TOP;
        }
        return DECK_BODY;
    }

    public static BlockState pillarBlock(long seed, Column c, int x, int y, int z) {
        Pillar pl = c.pillar();
        if (pl == null || y < MIN_Y || y >= MIN_Y + HEIGHT) return AIR;
        int ytop = c.pillarYtop();
        if (y <= c.pillarTop()) {
            double d2 = footprintD2(pl, x, z, y, ytop);
            double r = radiusAt(ytop, y);
            if (d2 <= r * r) {
                // 표면이 덩어리째 깊게 파여 있음(드물지만 3~7블록 깊이)
                double nn = noise3(seed, x, y, z, 7.0, 101);
                if (nn > PIT_THRESHOLD) {
                    double depth = 2.0 + 5.0 * Math.min(1.0, (nn - PIT_THRESHOLD) / 0.12);
                    if (Math.sqrt(d2) > r - depth) return AIR;
                }
                return PILLAR;
            }
            return AIR;
        }
        if (y <= c.rebarTop()) return REBAR;
        return AIR;
    }

    /** 모든 것을 합친 블록(기둥 우선). 생성기는 층별/기둥별로 따로 채우고, 이 함수는 조회용. */
    public static BlockState block(long seed, Column c, int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT) return AIR;
        BlockState s = pillarBlock(seed, c, x, y, z);
        if (!s.isAir()) return s;
        for (Layer l : c.layers()) {
            if (l != null && y >= l.bottom() && y <= l.maxY()) return deckBlock(seed, l, x, y, z);
        }
        return AIR;
    }

    // ---------------- 탐색 ----------------
    private static int[] deckAt(Params p, long seed, int layer, int x, int z) {
        if (layer >= 0) {
            Layer l = layerAt(p, seed, layer, x, z);
            return l == null ? null : new int[]{x, z, l.standY(), layer};
        }
        for (int k = p.layers() - 1; k >= 0; k--) {
            Layer l = layerAt(p, seed, k, x, z);
            if (l != null) return new int[]{x, z, l.standY(), k};
        }
        return null;
    }

    /**
     * (x, z)에서 가장 가까운 남아 있는 판 컬럼을 찾는다. layer가 -1이면 모든 층(가까운 곳에서 같은 거리면 위층 우선).
     * 결과 {x, z, 서 있을 y, 층}, 없으면 null. 가까운 곳은 촘촘히, 먼 곳은 성기게 훑는다.
     */
    public static int[] nearestDeck(Params p, long seed, int x, int z, int maxR, int layer) {
        int[] d0 = deckAt(p, seed, layer, x, z);
        if (d0 != null) return d0;
        int r = 4;
        while (r <= maxR) {
            int step = r <= 64 ? 4 : (r <= 512 ? 16 : 64);
            int cnt = Math.max(8, Math.min(256, (int) Math.ceil(2 * Math.PI * r / step)));
            for (int i = 0; i < cnt; i++) {
                double a = 2 * Math.PI * i / cnt;
                int tx = x + (int) Math.round(r * Math.cos(a));
                int tz = z + (int) Math.round(r * Math.sin(a));
                int[] d = deckAt(p, seed, layer, tx, tz);
                if (d != null) return d;
            }
            r += step;
        }
        return null;
    }
}
