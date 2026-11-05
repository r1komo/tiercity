package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.SplittableRandom;

/**
 * 시작 지점: 최하층 판 아래, 거대한 배관이 제멋대로 얽힌 어두운 공간(원작 1화).
 * 조명은 없다. 빛은 판이 찢어진 틈으로만 들어온다. 바닥은 무너진 잔해, 벽은 없다(가장자리 밖은 허공).
 * 스폰 근처 잔해에 묻힌 굵은 배관 하나가 위로 비스듬히 뻗어 판의 찢어진 틈을 지나 지상 위로 부러진 채 솟아 있다.
 * 그 배관 등을 타고 오르면(점프 포함) 자연스럽게 지상으로 나온다.
 * (설정, 시드)만으로 결과가 정해진다.
 */
final class StartComplex {
    private StartComplex() {}

    /** 설정의 startInPipeHall. 생성기/이벤트가 설정에서 넣어 준다. */
    static volatile boolean enabled = true;

    static final int HALF = 120;
    static final int F0 = 66;                                     // 잔해 바닥 기준 윗면 y
    static final int CEIL = CityLayout.layerBottom(0) - 1;        // 판 아랫면 바로 아래 y(97)
    static final int CLEAR_ABOVE = 30;                            // 찢어진 틈 위를 비우는 높이
    // 판이 찢어진 틈(스폰 설정 좌표 기준, 거칠게 깎임)
    static final int HX0 = -22, HX1 = 44, HZ0 = -27, HZ1 = 3;
    static final int SPAWN_DX = -72, SPAWN_DZ = 50;
    static final int ROUTE_R = 3;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState[] RUBBLE = {Blocks.GRAY_CONCRETE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()};
    private static final BlockState[] PIPE = {Blocks.GRAY_CONCRETE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(),
            Blocks.POLISHED_DEEPSLATE.defaultBlockState()};
    private static final BlockState FLANGE = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState RUST = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final BlockState REBAR = Blocks.RAW_IRON_BLOCK.defaultBlockState();

    /** 배관 한 토막: A에서 B까지의 원통(끝은 둥긂). */
    static final class Seg {
        double ax, ay, az, bx, by, bz, r, len;
        int mat;
        boolean broken;                                           // B쪽 끝이 부러져 들쭉날쭉
        boolean smooth;                                           // 오르는 길: 이음 고리 없음
        int minX, maxX, minY, maxY, minZ, maxZ;

        Seg(double ax, double ay, double az, double bx, double by, double bz, double r, int mat) {
            this.ax = ax; this.ay = ay; this.az = az; this.bx = bx; this.by = by; this.bz = bz; this.r = r; this.mat = mat;
            len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
            int m = (int) Math.ceil(r) + 2;
            minX = (int) Math.floor(Math.min(ax, bx)) - m; maxX = (int) Math.ceil(Math.max(ax, bx)) + m;
            minY = (int) Math.floor(Math.min(ay, by)) - m; maxY = (int) Math.ceil(Math.max(ay, by)) + m;
            minZ = (int) Math.floor(Math.min(az, bz)) - m; maxZ = (int) Math.ceil(Math.max(az, bz)) + m;
        }

        /** 점에서 중심선까지 거리 제곱과 축 방향 위치(블록). */
        double[] dist(double x, double y, double z) {
            double ux = bx - ax, uy = by - ay, uz = bz - az;
            double t = len < 1e-9 ? 0 : ((x - ax) * ux + (y - ay) * uy + (z - az) * uz) / (len * len);
            t = Math.max(0, Math.min(1, t));
            double px = ax + ux * t - x, py = ay + uy * t - y, pz = az + uz * t - z;
            return new double[]{px * px + py * py + pz * pz, t * len};
        }

        /** 중심선 위 점들과 다른 선분 사이 최소 거리(대략). */
        double near(Seg o) {
            double best = 1e9;
            int n = Math.max(2, (int) (o.len / 2));
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                double[] d = dist(o.ax + (o.bx - o.ax) * t, o.ay + (o.by - o.ay) * t, o.az + (o.bz - o.az) * t);
                best = Math.min(best, Math.sqrt(d[0]));
            }
            return best;
        }
    }

    static final class Hall {
        int cx, cz, ytop, spx, spz, spy;
        Seg[] route;
        Seg[] segs;
    }

    private static final ThreadLocal<HashMap<Long, Hall>> CACHE = ThreadLocal.withInitial(HashMap::new);

    static boolean active(CityLayout.Params p) {
        return enabled && p.layers() >= 1;
    }

    static boolean touches(CityLayout.Params p, int x, int z) {
        return active(p) && Math.abs(x - p.spawnX()) <= HALF && Math.abs(z - p.spawnZ()) <= HALF;
    }

    /** 찢어진 틈 둘레(건물, 웅덩이 등을 두지 않는 곳). */
    static boolean exitZone(CityLayout.Params p, long seed, int x, int z, int margin) {
        if (!active(p)) return false;
        int dx = x - p.spawnX(), dz = z - p.spawnZ();
        return dx >= HX0 - margin && dx <= HX1 + 16 + margin && dz >= HZ0 - margin && dz <= HZ1 + margin;
    }

    static boolean exitOverlaps(CityLayout.Params p, long seed, int x0, int z0, int x1, int z1, int margin) {
        if (!active(p)) return false;
        int sx = p.spawnX(), sz = p.spawnZ();
        return x1 >= sx + HX0 - margin && x0 <= sx + HX1 + 16 + margin && z1 >= sz + HZ0 - margin && z0 <= sz + HZ1 + margin;
    }

    /** 처음 들어오는 플레이어가 서는 곳 {x, y(발), z}. */
    static int[] spawnPos(CityLayout.Params p, long seed) {
        Hall h = hall(p, seed);
        return new int[]{h.spx, h.spy, h.spz};
    }

    static Hall hall(CityLayout.Params p, long seed) {
        HashMap<Long, Hall> cache = CACHE.get();
        long key = CityLayout.hash(seed ^ ((long) p.hashCode() * 0xD6E8FEB86659FD93L), 0, 0, 7410);
        Hall h = cache.get(key);
        if (h == null) {
            h = build(p, seed);
            if (cache.size() > 16) cache.clear();
            cache.put(key, h);
        }
        return h;
    }

    /** 잔해 바닥 윗면(바닥이 없으면 MIN). */
    static int floorTop(long seed, Hall h, int x, int z) {
        int dx = x - h.cx, dz = z - h.cz;
        double edge = HALF - 4 - 10 * CityLayout.noise(seed, x, z, 14.0, 7411);
        if (Math.max(Math.abs(dx), Math.abs(dz)) > edge) return Integer.MIN_VALUE;
        double n = CityLayout.noise(seed, x, z, 10.0, 7412) * 2.2 + CityLayout.noise(seed, x, z, 3.0, 7413) * 1.3;
        return F0 - 1 + (int) Math.floor(n);
    }

    private static Hall build(CityLayout.Params p, long seed) {
        Hall h = new Hall();
        h.cx = p.spawnX(); h.cz = p.spawnZ();
        CityLayout.Layer l = CityLayout.baseLayer(p, seed, 0, h.cx + HX1 + 8, h.cz - 12);
        h.ytop = l != null ? l.top() : CityLayout.LEVEL_BASE;
        h.spx = h.cx + SPAWN_DX; h.spz = h.cz + SPAWN_DZ;

        // 오르는 길: 잔해에 묻힌 끝 → 판 아래를 따라 → 찢어진 틈을 지나 지상 위로 부러진 끝
        double r = ROUTE_R;
        double y0 = F0 - 3, y1 = 88;
        double ex = HX1 - 2, xs = HX0 + 6, xe = HX1 + 12;
        double yEnd = y1 + (h.ytop - 1 - y1) * (xe - xs) / (ex - xs);
        h.route = new Seg[]{
                new Seg(h.cx - 66, y0, h.cz + 50, h.cx - 16, y1, h.cz + 50, r, 0),
                new Seg(h.cx - 16, y1, h.cz + 50, h.cx - 16, y1, h.cz - 12, r, 0),
                new Seg(h.cx - 16, y1, h.cz - 12, h.cx + xe, yEnd, h.cz - 12, r, 0)};
        h.route[2].broken = true;
        for (Seg s : h.route) s.smooth = true;
        int fy = floorTop(seed, h, h.spx, h.spz);
        h.spy = (fy == Integer.MIN_VALUE ? F0 - 1 : fy) + 1;

        SplittableRandom rng = new SplittableRandom(CityLayout.hash(seed, h.cx, h.cz, 7414));
        ArrayList<Seg> segs = new ArrayList<>();
        for (Seg s : h.route) segs.add(s);
        int lo = -HALF + 10, hi = HALF - 10;
        int tries = 0;
        while (segs.size() < 140 && tries++ < 1200) {
            double q = rng.nextDouble();
            double rad = q < 0.25 ? 2 : q < 0.55 ? 3 + rng.nextInt(2) : q < 0.85 ? 5 + rng.nextInt(2) : 7;
            double ax = h.cx + lo + rng.nextInt(hi - lo), az = h.cz + lo + rng.nextInt(hi - lo);
            double ay = F0 - rad + rng.nextDouble() * (CEIL + rad - (F0 - rad));
            // 방향: 대체로 눕거나 서 있지만 기울기는 제각각
            double kind = rng.nextDouble();
            double yaw = rng.nextDouble() * Math.PI * 2;
            double pitch = kind < 0.55 ? (rng.nextDouble() - 0.5) * 0.5 : kind < 0.8 ? (rng.nextDouble() - 0.5) * 1.4
                    : (rng.nextBoolean() ? 1 : -1) * (1.15 + rng.nextDouble() * 0.42);
            if (kind < 0.3) yaw = Math.round(yaw / (Math.PI / 2)) * (Math.PI / 2);   // 일부는 반듯한 방향
            double L = 30 + rng.nextInt(120);
            double bx = ax + Math.cos(yaw) * Math.cos(pitch) * L, bz = az + Math.sin(yaw) * Math.cos(pitch) * L;
            double by = ay + Math.sin(pitch) * L;
            // 공간 안으로 자름
            double t = 1;
            if (bx < h.cx + lo) t = Math.min(t, (h.cx + lo - ax) / (bx - ax));
            if (bx > h.cx + hi) t = Math.min(t, (h.cx + hi - ax) / (bx - ax));
            if (bz < h.cz + lo) t = Math.min(t, (h.cz + lo - az) / (bz - az));
            if (bz > h.cz + hi) t = Math.min(t, (h.cz + hi - az) / (bz - az));
            if (by < F0 - rad - 2) t = Math.min(t, (F0 - rad - 2 - ay) / (by - ay));
            if (by > CEIL + rad) t = Math.min(t, (CEIL + rad - ay) / (by - ay));
            if (t < 0.15) continue;
            bx = ax + (bx - ax) * t; by = ay + (by - ay) * t; bz = az + (bz - az) * t;
            Seg s = new Seg(ax, ay, az, bx, by, bz, rad, pickMat(rng));
            if (!clear(h, s)) continue;
            segs.add(s);
        }
        // 찢어진 틈으로 함께 솟아오른 굵은 배관 몇 개(부러진 끝)
        for (int i = 0; i < 6; i++) {
            double rad = 3 + rng.nextInt(3);
            double ax = h.cx + HX0 + 4 + rng.nextInt(HX1 - HX0 - 8), az = h.cz + (rng.nextBoolean() ? HZ0 + 3 + rng.nextInt(5) : HZ1 - 3 - rng.nextInt(5));
            double bx = ax + (rng.nextDouble() - 0.5) * 30, bz = az + (az < h.cz - 12 ? -1 : 1) * (4 + rng.nextInt(10));
            Seg s = new Seg(ax, F0 + rng.nextInt(10), az, bx, h.ytop + 3 + rng.nextInt(10), bz, rad, pickMat(rng));
            s.broken = true;
            if (clear(h, s)) segs.add(s);
        }
        h.segs = segs.toArray(new Seg[0]);
        return h;
    }

    private static int pickMat(SplittableRandom rng) {
        double m = rng.nextDouble();
        return m < 0.4 ? 0 : m < 0.62 ? 1 : m < 0.75 ? 2 : m < 0.87 ? 3 : 4;
    }

    /** 오르는 길 위(머리 공간 포함)와 스폰 둘레를 막지 않는지. */
    private static boolean clear(Hall h, Seg s) {
        for (Seg rt : h.route) {
            if (rt.near(s) < s.r + ROUTE_R + 4) return false;
            if (s.near(rt) < s.r + ROUTE_R + 4) return false;
        }
        double[] d = s.dist(h.spx, F0 + 2, h.spz);
        return Math.sqrt(d[0]) > s.r + 7;
    }

    static boolean inHole(long seed, Hall h, int x, int z) {
        int dx = x - h.cx, dz = z - h.cz;
        double rough = 4 * CityLayout.noise(seed, x, z, 7.0, 7415) + 1.5 * CityLayout.noise(seed, x, z, 2.5, 7416);
        double ex = Math.min(dx - HX0, HX1 - dx), ez = Math.min(dz - HZ0, HZ1 - dz);
        return Math.min(ex, ez) > rough;
    }

    static int minY() { return F0 - 6; }

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

    private static BlockState pipeAt(long seed, Hall h, int x, int y, int z) {
        for (Seg g : segsAt(h, x, z)) {
            if (y < g.minY || y > g.maxY) continue;
            double[] d = g.dist(x + 0.5, y + 0.5, z + 0.5);
            double rr = (g.r + 0.45) * (g.r + 0.45);
            double along = d[1];
            if (g.broken && along > g.len - 4) {
                // 부러진 끝: 들쭉날쭉하게 끊김
                double cut = g.len - 4 + 4 * CityLayout.hash01(seed, x * 7 + y, z, 7417);
                if (along > cut) continue;
            }
            if (d[0] <= rr) {
                if (!g.smooth && CityLayout.hash01(seed, x * 7 + y, z, 7418) < 0.035) return RUST;
                return PIPE[g.mat];
            }
            double rf = (g.r + 1.45) * (g.r + 1.45);
            if (g.r >= 3 && !g.smooth && d[0] <= rf && Math.floorMod((int) Math.floor(along), 11) == 5 && along > 1 && along < g.len - 1) return FLANGE;
        }
        return null;
    }

    /** 이 칸의 블록. null이면 손대지 않음(판, 공기 그대로). AIR이면 비움. */
    static BlockState block(long seed, Hall h, int x, int y, int z) {
        if (y > CEIL) {
            BlockState ps = pipeAt(seed, h, x, y, z);
            if (ps != null) return ps;
            if (inHole(seed, h, x, z) && y <= maxY(h)) {
                // 찢어진 판 가장자리에 드러난 철근
                if (y <= h.ytop && !inHole(seed, h, x + 1, z) && CityLayout.hash01(seed, x, z * 3 + y, 7419) < 0.05) return REBAR;
                return AIR;
            }
            return null;
        }
        if (y < minY()) return null;
        BlockState ps = pipeAt(seed, h, x, y, z);
        if (ps != null) return ps;
        int ft = floorTop(seed, h, x, z);
        if (ft != Integer.MIN_VALUE && y <= ft && y >= F0 - 4) {
            return RUBBLE[CityLayout.rand(seed, x * 31 + y, z, 7420, RUBBLE.length)];
        }
        return null;
    }
}
