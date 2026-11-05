package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.SplittableRandom;

/**
 * 시작 지점: 최하층 판 바로 아래에 매달린 배관 공간(원작 1화).
 * 240x240, 바닥 y=70, 천장은 판 아랫면. 굵고 가는 배관이 사방으로 얽혀 있고 희미한 등이 매달려 있다.
 * 스폰 지점에서 계단실 입구까지는 통로가 비어 있고, 계단실은 판을 뚫고 지상까지 나선으로 오른다.
 * (설정, 시드)만으로 결과가 정해진다.
 */
final class StartComplex {
    private StartComplex() {}

    /** 설정의 startInPipeHall. 생성기/이벤트가 설정에서 넣어 준다. */
    static volatile boolean enabled = true;

    static final int HALF = 120;
    static final int F0 = 70;                                     // 바닥(윗면) y
    static final int CEIL = CityLayout.layerBottom(0) - 1;        // 천장 바로 아래 y(97)
    static final int SHAFT_DX = 60, SHAFT_DZ = 40;                // 스폰 설정 좌표 기준 계단실 중심
    static final int SPAWN_DX = -40;                              // 스폰 설정 좌표 기준 시작 지점
    static final int SH_R = 5;                                    // 계단실 벽(체비쇼프 거리)
    static final int CLEAR_ABOVE = 30;                            // 지상 출구 위를 비우는 높이

    private static final BlockState FLOOR = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState FLOOR_B = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState PATH = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState PATH_LINE = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState WALL = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState WALL_B = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState CORE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TREAD = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TREAD_SLAB = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LIGHT = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState LIGHT_DEAD = Blocks.REDSTONE_LAMP.defaultBlockState();
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState[] PIPE_BIG = {Blocks.GRAY_CONCRETE.defaultBlockState(), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
            Blocks.POLISHED_ANDESITE.defaultBlockState()};
    private static final BlockState[] PIPE_MID = {Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(),
            Blocks.GRAY_CONCRETE.defaultBlockState()};
    private static final BlockState FLANGE = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState RUST = Blocks.RAW_IRON_BLOCK.defaultBlockState();

    /** 배관 한 토막: axis 0 x, 1 y, 2 z 방향 원통. 중심선 (cx, cy, cz), 축 방향 범위 [t0, t1]. */
    static final class Seg {
        int axis, cx, cy, cz, t0, t1, r, mat;
        int minX, maxX, minY, maxY, minZ, maxZ;

        void bounds() {
            int m = r + 1;
            minX = axis == 0 ? t0 : cx - m; maxX = axis == 0 ? t1 : cx + m;
            minY = axis == 1 ? t0 : cy - m; maxY = axis == 1 ? t1 : cy + m;
            minZ = axis == 2 ? t0 : cz - m; maxZ = axis == 2 ? t1 : cz + m;
        }

        boolean hitsBox(int x0, int y0, int z0, int x1, int y1, int z1) {
            return maxX >= x0 && minX <= x1 && maxY >= y0 && minY <= y1 && maxZ >= z0 && minZ <= z1;
        }
    }

    static final class Hall {
        int cx, cz, shx, shz, spx, spz, ytop, laps;
        double pitch;
        Seg[] segs;
        int[][] lamps;                                            // {x, z, 아래 끝 y, 켜짐}
    }

    private static final ThreadLocal<HashMap<Long, Hall>> CACHE = ThreadLocal.withInitial(HashMap::new);

    static boolean active(CityLayout.Params p) {
        return enabled && p.layers() >= 1;
    }

    static boolean touches(CityLayout.Params p, int x, int z) {
        return active(p) && Math.abs(x - p.spawnX()) <= HALF && Math.abs(z - p.spawnZ()) <= HALF;
    }

    /** 지상 출구 둘레(건물, 웅덩이 등을 두지 않는 곳). */
    static boolean exitZone(CityLayout.Params p, long seed, int x, int z, int margin) {
        if (!active(p)) return false;
        int sx = p.spawnX() + SHAFT_DX, sz = p.spawnZ() + SHAFT_DZ;
        return Math.max(Math.abs(x - sx), Math.abs(z - sz)) <= SH_R + margin;
    }

    static boolean exitOverlaps(CityLayout.Params p, long seed, int x0, int z0, int x1, int z1, int margin) {
        if (!active(p)) return false;
        int sx = p.spawnX() + SHAFT_DX, sz = p.spawnZ() + SHAFT_DZ, e = SH_R + margin;
        return x1 >= sx - e && x0 <= sx + e && z1 >= sz - e && z0 <= sz + e;
    }

    /** 처음 들어오는 플레이어가 서는 곳 {x, y(발), z}. */
    static int[] spawnPos(CityLayout.Params p) {
        return new int[]{p.spawnX() + SPAWN_DX, F0 + 1, p.spawnZ() + SHAFT_DZ};
    }

    static Hall hall(CityLayout.Params p, long seed) {
        HashMap<Long, Hall> cache = CACHE.get();
        long key = CityLayout.hash(seed ^ ((long) p.hashCode() * 0xD6E8FEB86659FD93L), 0, 0, 7400);
        Hall h = cache.get(key);
        if (h == null) {
            h = build(p, seed);
            if (cache.size() > 16) cache.clear();
            cache.put(key, h);
        }
        return h;
    }

    private static Hall build(CityLayout.Params p, long seed) {
        Hall h = new Hall();
        h.cx = p.spawnX(); h.cz = p.spawnZ();
        h.shx = h.cx + SHAFT_DX; h.shz = h.cz + SHAFT_DZ;
        h.spx = h.cx + SPAWN_DX; h.spz = h.shz;
        CityLayout.Layer l = CityLayout.baseLayer(p, seed, 0, h.shx, h.shz);
        h.ytop = l != null ? l.top() : CityLayout.LEVEL_BASE;
        int rise = h.ytop - F0;
        h.laps = Math.max(1, (int) Math.ceil(rise / 10.0));
        h.pitch = rise / (double) h.laps;

        SplittableRandom r = new SplittableRandom(CityLayout.hash(seed, h.cx, h.cz, 7401));
        ArrayList<Seg> segs = new ArrayList<>();
        int lo = -HALF + 4, hi = HALF - 4;
        // 얽힌 배관: 여러 번 꺾이는 경로
        for (int i = 0; i < 130; i++) {
            double q = r.nextDouble();
            int rad = q < 0.2 ? 0 : q < 0.55 ? 1 : q < 0.85 ? 2 : 3;
            int x = h.cx + lo + r.nextInt(hi - lo), z = h.cz + lo + r.nextInt(hi - lo);
            double yq = r.nextDouble();
            int y = yq < 0.35 ? F0 + 1 + rad : yq < 0.8 ? F0 + 4 + rad + r.nextInt(Math.max(1, CEIL - F0 - 8 - 2 * rad)) : CEIL - rad;
            int axis = r.nextDouble() < 0.25 ? 1 : (r.nextBoolean() ? 0 : 2);
            int n = 2 + r.nextInt(3);
            int mat = r.nextInt(3);
            for (int s = 0; s < n; s++) {
                Seg g = new Seg();
                g.axis = axis; g.r = rad; g.mat = mat;
                g.cx = x; g.cy = y; g.cz = z;
                int len = axis == 1 ? 6 + r.nextInt(CEIL - F0) : 14 + r.nextInt(70);
                int dir = r.nextBoolean() ? 1 : -1;
                int start = axis == 0 ? x : axis == 1 ? y : z;
                int end = start + dir * len;
                int min = axis == 1 ? F0 + 1 + rad : (axis == 0 ? h.cx : h.cz) + lo;
                int max = axis == 1 ? CEIL - rad : (axis == 0 ? h.cx : h.cz) + hi;
                end = Math.max(min, Math.min(max, end));
                g.t0 = Math.min(start, end); g.t1 = Math.max(start, end);
                g.bounds();
                if (g.t1 - g.t0 < 3 || blocked(h, g)) break;
                segs.add(g);
                if (axis == 0) x = end; else if (axis == 1) y = end; else z = end;
                int na;
                do { na = r.nextInt(3); } while (na == axis);
                axis = na;
            }
        }
        // 바닥에서 천장까지 곧게 선 배관(가는 것은 쇠창살)
        for (int i = 0; i < 50; i++) {
            Seg g = new Seg();
            g.axis = 1;
            g.r = r.nextDouble() < 0.45 ? 0 : 1 + r.nextInt(2);
            g.mat = g.r == 0 ? -1 : r.nextInt(3);
            g.cx = h.cx + lo + r.nextInt(hi - lo); g.cz = h.cz + lo + r.nextInt(hi - lo);
            g.cy = F0 + 1;
            g.t0 = F0 + 1; g.t1 = CEIL;
            g.bounds();
            if (!blocked(h, g)) segs.add(g);
        }
        h.segs = segs.toArray(new Seg[0]);

        // 매달린 등: 통로를 따라 10칸마다, 그 밖은 흩어져서(일부는 꺼짐)
        ArrayList<int[]> lamps = new ArrayList<>();
        for (int x = h.spx; x <= h.shx - 8; x += 10) lamps.add(new int[]{x, h.spz, F0 + 8, 1});
        for (int a = -HALF + 12; a <= HALF - 12; a += 22) {
            for (int b = -HALF + 12; b <= HALF - 12; b += 22) {
                if (r.nextDouble() < 0.45) continue;
                int lx = h.cx + a + r.nextInt(9) - 4, lz = h.cz + b + r.nextInt(9) - 4;
                if (Math.abs(lz - h.spz) <= 5 && lx >= h.spx - 6 && lx <= h.shx) continue;
                lamps.add(new int[]{lx, lz, CEIL - 2 - r.nextInt(8), r.nextDouble() < 0.7 ? 1 : 0});
            }
        }
        h.lamps = lamps.toArray(new int[0][]);
        return h;
    }

    /** 통로, 계단실, 시작 지점 둘레를 지나가는 배관은 두지 않는다. */
    private static boolean blocked(Hall h, Seg g) {
        if (g.hitsBox(h.spx - 6, F0 + 1, h.spz - 4, h.shx - SH_R, F0 + 6, h.spz + 4)) return true;     // 통로(머리 위는 지나가도 됨)
        if (g.hitsBox(h.spx - 7, F0 + 1, h.spz - 7, h.spx + 7, CEIL, h.spz + 7)) return true;          // 시작 지점
        return g.hitsBox(h.shx - SH_R - 2, F0 + 1, h.shz - SH_R - 2, h.shx + SH_R + 2, CEIL, h.shz + SH_R + 2);   // 계단실
    }

    static int minY() { return F0 - 2; }

    static int maxY(Hall h) { return h.ytop + CLEAR_ABOVE; }

    // ---------------- 블록 ----------------
    private static final ThreadLocal<long[]> COL_KEY = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE});
    private static final ThreadLocal<Seg[][]> COL_SEGS = ThreadLocal.withInitial(() -> new Seg[1][]);

    private static Seg[] segsAt(Hall h, int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL) ^ ((long) System.identityHashCode(h) << 17);
        long[] k = COL_KEY.get();
        Seg[][] cache = COL_SEGS.get();
        if (k[0] == key && cache[0] != null) return cache[0];
        ArrayList<Seg> l = new ArrayList<>();
        for (Seg g : h.segs) if (x >= g.minX && x <= g.maxX && z >= g.minZ && z <= g.maxZ) l.add(g);
        cache[0] = l.toArray(new Seg[0]);
        k[0] = key;
        return cache[0];
    }

    /** 이 칸의 블록. null이면 손대지 않음(판, 공기 그대로). AIR이면 비움. */
    static BlockState block(long seed, Hall h, int x, int y, int z) {
        int dx = x - h.shx, dz = z - h.shz, c = Math.max(Math.abs(dx), Math.abs(dz));
        if (c <= SH_R && y > F0) {
            BlockState s = shaft(h, x, y, z, dx, dz, c);
            if (s != null) return s;
        }
        if (y < F0 - 2 || y > CEIL) return null;
        int lx = x - h.cx, lz = z - h.cz;
        if (Math.abs(lx) > HALF || Math.abs(lz) > HALF) return null;
        int edge = HALF - Math.max(Math.abs(lx), Math.abs(lz));
        if (edge == 0) return WALL;                                               // 바깥벽
        if (edge <= 2 && (Math.floorMod(Math.abs(lx) == HALF - edge ? lz : lx, 16) <= 1)) return WALL_B;   // 벽기둥
        if (y <= F0) {
            if (y < F0) return FLOOR;
            if (Math.abs(z - h.spz) <= 3 && x >= h.spx - 6 && x < h.shx - SH_R) {
                return Math.abs(z - h.spz) == 3 ? PATH_LINE : PATH;              // 통로 바닥
            }
            return CityLayout.hash01(seed, x, z, 7402) < 0.12 ? FLOOR_B : FLOOR;
        }
        // 배관
        for (Seg g : segsAt(h, x, z)) {
            if (y < g.minY || y > g.maxY) continue;
            int t = g.axis == 0 ? x : g.axis == 1 ? y : z;
            if (t < g.t0 || t > g.t1) continue;
            int da = g.axis == 0 ? y - g.cy : x - g.cx;
            int db = g.axis == 2 ? y - g.cy : z - g.cz;
            int d2 = da * da + db * db;
            double rr = g.r * g.r + 0.8 * g.r;
            if (d2 <= rr) {
                if (g.mat < 0) return BARS;
                if (CityLayout.hash01(seed, x * 7 + y, z, 7403) < 0.08) return RUST;
                return g.r >= 2 ? PIPE_BIG[g.mat] : PIPE_MID[g.mat];
            }
            if (g.r >= 1 && Math.floorMod(t - g.t0, 9) == 4) {
                double rf = (g.r + 1) * (g.r + 1) + 0.8 * (g.r + 1);
                if (d2 <= rf) return FLANGE;
            }
        }
        // 매달린 등
        for (int[] lp : h.lamps) {
            if (lp[0] != x || lp[1] != z) continue;
            if (y == lp[2]) return lp[3] == 1 ? LIGHT : LIGHT_DEAD;
            if (y > lp[2]) return CHAIN;
        }
        return null;
    }

    /** 계단실: 가운데 5x5 기둥을 감아 오르는 2칸 폭 나선 계단. 한 바퀴에 약 10칸. 판을 뚫고 지상까지. */
    private static BlockState shaft(Hall h, int x, int y, int z, int dx, int dz, int c) {
        if (y > h.ytop + CLEAR_ABOVE) return null;
        if (c <= 2) return y <= h.ytop ? CORE : AIR;
        if (c == SH_R) {
            if (y > h.ytop) return AIR;                                           // 출구 둘레 비움
            if (y > CEIL) {
                if ((dx == 0 || dz == 0) && Math.floorMod(y, 8) == 4 && y < h.ytop - 1) return LIGHT;
                return null;                                                      // 판이 벽
            }
            if (dx == -SH_R && Math.abs(dz) <= 1 && y <= F0 + 3) return AIR;      // 아래 출입구(서쪽)
            if ((dx == 0 || dz == 0) && Math.floorMod(y, 8) == 4) return LIGHT;
            return WALL;
        }
        // 디딤판(c 3, 4)
        double s = Math.atan2(dz, -dx) / (2 * Math.PI);
        if (s < 0) s += 1;
        for (int n = 0; n < h.laps; n++) {
            double S = F0 + 1 + (n + s) * h.pitch;
            if (S > h.ytop + 1 + 1e-6) break;
            double q = Math.floor(S * 2) / 2;
            int full = (int) Math.floor(q);
            if (q - full < 0.25) { if (y == full - 1) return TREAD; }
            else if (y == full) return TREAD_SLAB;
        }
        return AIR;
    }
}
