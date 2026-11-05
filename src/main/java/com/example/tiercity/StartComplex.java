package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.SplittableRandom;

/**
 * 시작 지점: 최하층 판 아래 허공에 배관이 매달려 얽힌 어두운 공간(원작 1화).
 * 조명도 바닥도 벽도 없다. 빛은 판이 찢어진 틈으로만 들어온다. 배관은 판 아랫면에 매달리거나 다른 배관에서 갈라져 나온다.
 * 깊은 곳의 굵은 배관 위에서 시작하고, 그 배관이 이어져 판의 찢어진 틈을 지나 지상 위로 부러진 채 솟아 있다.
 * 그 배관 등을 타고 오르면(점프 포함) 자연스럽게 지상으로 나온다.
 * (설정, 시드)만으로 결과가 정해진다.
 */
final class StartComplex {
    private StartComplex() {}

    /** 설정의 startInPipeHall. 생성기/이벤트가 설정에서 넣어 준다. */
    static volatile boolean enabled = true;

    static final int HALF = 120;
    static final int DEPTH = 120;                                 // 판 아랫면에서 배관이 내려가는 깊이
    static final int CEIL = CityLayout.layerBottom(0) - 1;        // 판 아랫면 바로 아래 y(97)
    static final int CLEAR_ABOVE = 30;                            // 찢어진 틈 위를 비우는 높이
    // 판이 찢어진 틈(스폰 설정 좌표 기준, 거칠게 깎임)
    static final int HX0 = -22, HX1 = 44, HZ0 = -27, HZ1 = 3;
    static final int SPAWN_DX = -84, SPAWN_DZ = 60;
    static final int ROUTE_R = 3;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
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

    private static Hall build(CityLayout.Params p, long seed) {
        Hall h = new Hall();
        h.cx = p.spawnX(); h.cz = p.spawnZ();
        CityLayout.Layer l = CityLayout.baseLayer(p, seed, 0, h.cx + HX1 + 8, h.cz - 12);
        h.ytop = l != null ? l.top() : CityLayout.LEVEL_BASE;
        h.spx = h.cx + SPAWN_DX; h.spz = h.cz + SPAWN_DZ;

        // 오르는 길: 깊은 곳의 시작 배관 → 비스듬히 올라 판 아래를 따라 → 찢어진 틈을 지나 지상 위로 부러진 끝
        double r = ROUTE_R;
        double y0 = CEIL - 100, yMid = CEIL - 30, y1 = CEIL - 9;
        double ex = HX1 - 2, xs = HX0 + 6, xe = HX1 + 12;
        double yEnd = y1 + (h.ytop - 1 - y1) * (xe - xs) / (ex - xs);
        int cx = h.cx, cz = h.cz;
        h.route = new Seg[]{
                new Seg(cx - 96, y0, cz + 60, cx - 60, y0, cz + 60, r, 0),
                new Seg(cx - 60, y0, cz + 60, cx + 60, yMid, cz + 60, r, 0),
                new Seg(cx + 60, yMid, cz + 60, cx + 60, y1, cz + 24, r, 0),
                new Seg(cx + 60, y1, cz + 24, cx - 16, y1, cz + 24, r, 0),
                new Seg(cx - 16, y1, cz + 24, cx - 16, y1, cz - 12, r, 0),
                new Seg(cx - 16, y1, cz - 12, cx + xe, yEnd, cz - 12, r, 0)};
        h.route[h.route.length - 1].broken = true;
        for (Seg s : h.route) s.smooth = true;
        h.spy = (int) Math.floor(y0 + r + 0.45 - 0.5) + 1;
        while (h.spy > y0 && pipeAtRaw(h.route, h.spx, h.spy - 1, h.spz) == null) h.spy--;
        while (pipeAtRaw(h.route, h.spx, h.spy, h.spz) != null) h.spy++;

        SplittableRandom rng = new SplittableRandom(CityLayout.hash(seed, h.cx, h.cz, 7424));
        ArrayList<Seg> segs = new ArrayList<>();
        for (Seg s : h.route) segs.add(s);
        ArrayList<Seg> free = new ArrayList<>();
        int lo = -HALF + 10, hi = HALF - 10, yLo = CEIL - DEPTH;
        int tries = 0;
        while (free.size() < 70 && tries++ < 3000) {
            double q = rng.nextDouble();
            double rad = q < 0.4 ? 1 : q < 0.75 ? 2 : q < 0.9 ? 3 : q < 0.97 ? 4 : 5;
            // 시작점: 판 아랫면에 매달리거나, 이미 있는 배관에서 갈라져 나옴
            double ax, ay, az;
            if (free.isEmpty() || rng.nextDouble() < 0.4) {
                ax = h.cx + lo + rng.nextInt(hi - lo); az = h.cz + lo + rng.nextInt(hi - lo); ay = CEIL - rad + 1;
            } else {
                Seg o = free.get(rng.nextInt(free.size()));
                double t = rng.nextDouble();
                ax = o.ax + (o.bx - o.ax) * t; ay = o.ay + (o.by - o.ay) * t; az = o.az + (o.bz - o.az) * t;
            }
            double kind = rng.nextDouble();
            double yaw = rng.nextDouble() * Math.PI * 2;
            double pitch;
            if (kind < 0.3) pitch = -(1.25 + rng.nextDouble() * 0.32);                 // 아래로 늘어진 배관
            else if (kind < 0.8) pitch = (rng.nextDouble() - 0.5) * 0.4;              // 거의 수평
            else pitch = (rng.nextDouble() - 0.6) * 1.6;                              // 비스듬히
            if (kind >= 0.3 && kind < 0.6) yaw = Math.round(yaw / (Math.PI / 2)) * (Math.PI / 2);
            double L = 25 + rng.nextInt(110);
            double bx = ax + Math.cos(yaw) * Math.cos(pitch) * L, bz = az + Math.sin(yaw) * Math.cos(pitch) * L;
            double by = ay + Math.sin(pitch) * L;
            double t = 1;
            if (bx < h.cx + lo) t = Math.min(t, (h.cx + lo - ax) / (bx - ax));
            if (bx > h.cx + hi) t = Math.min(t, (h.cx + hi - ax) / (bx - ax));
            if (bz < h.cz + lo) t = Math.min(t, (h.cz + lo - az) / (bz - az));
            if (bz > h.cz + hi) t = Math.min(t, (h.cz + hi - az) / (bz - az));
            if (by < yLo) t = Math.min(t, (yLo - ay) / (by - ay));
            if (by > CEIL - rad + 1) t = Math.min(t, (CEIL - rad + 1 - ay) / (by - ay));
            if (t < 0.2) continue;
            bx = ax + (bx - ax) * t; by = ay + (by - ay) * t; bz = az + (bz - az) * t;
            Seg s = new Seg(ax, ay, az, bx, by, bz, rad, pickMat(rng));
            if (by < CEIL - 20 && rng.nextDouble() < 0.5) s.broken = true;            // 아래쪽 끝이 부러진 배관
            if (!clear(h, s)) continue;
            free.add(s);
            segs.add(s);
        }
        // 오르는 길을 받치는 줄기: 시작 배관 아래로 늘어진 배관, 경사 배관을 판에 매다는 배관
        for (double[] sp : new double[][]{{cx - 92, y0, cz + 60, -1}, {cx - 64, y0, cz + 60, -1}, {cx + 2, (y0 + yMid) / 2, cz + 60, -1}}) {
            Seg s = new Seg(sp[0], sp[1] - 2, sp[2], sp[0] + rng.nextInt(9) - 4, sp[1] - 30 - rng.nextInt(30), sp[2] + rng.nextInt(9) - 4, 2, pickMat(rng));
            s.broken = true;
            segs.add(s);
        }
        // 찢어진 틈으로 함께 솟아오른 배관 몇 개(부러진 끝)
        for (int i = 0; i < 5; i++) {
            double rad = 2 + rng.nextInt(2);
            double ax = h.cx + HX0 + 4 + rng.nextInt(HX1 - HX0 - 8), az = h.cz + (rng.nextBoolean() ? HZ0 + 3 + rng.nextInt(5) : HZ1 - 3 - rng.nextInt(5));
            double bx = ax + (rng.nextDouble() - 0.5) * 30, bz = az + (az < h.cz - 12 ? -1 : 1) * (4 + rng.nextInt(10));
            Seg s = new Seg(ax, CEIL - 40 + rng.nextInt(20), az, bx, h.ytop + 3 + rng.nextInt(10), bz, rad, pickMat(rng));
            s.broken = true;
            if (clear(h, s)) segs.add(s);
        }
        h.segs = segs.toArray(new Seg[0]);
        return h;
    }

    private static BlockState pipeAtRaw(Seg[] list, int x, int y, int z) {
        for (Seg g : list) {
            double[] d = g.dist(x + 0.5, y + 0.5, z + 0.5);
            if (d[0] <= (g.r + 0.45) * (g.r + 0.45)) return PIPE[0];
        }
        return null;
    }

    private static int pickMat(SplittableRandom rng) {
        double m = rng.nextDouble();
        return m < 0.4 ? 0 : m < 0.62 ? 1 : m < 0.75 ? 2 : m < 0.87 ? 3 : 4;
    }

    /** 오르는 길 위와 옆(머리 공간 포함), 스폰 둘레를 막지 않는지. 길 아래로 지나가는 것은 괜찮다. */
    private static boolean clear(Hall h, Seg s) {
        int n = Math.max(2, (int) (s.len / 1.5));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double px = s.ax + (s.bx - s.ax) * t, py = s.ay + (s.by - s.ay) * t, pz = s.az + (s.bz - s.az) * t;
            for (Seg rt : h.route) {
                double[] d = rt.dist(px, py, pz);
                double dist = Math.sqrt(d[0]);
                if (dist >= s.r + ROUTE_R + 5) continue;
                double ry = rt.ay + (rt.by - rt.ay) * (rt.len < 1e-9 ? 0 : d[1] / rt.len);
                if (py + s.r > ry - ROUTE_R - 1 || dist < s.r + ROUTE_R + 1) return false;
            }
            double ds = Math.sqrt((px - h.spx) * (px - h.spx) + (py - h.spy) * (py - h.spy) + (pz - h.spz) * (pz - h.spz));
            if (ds < s.r + 5) return false;
        }
        return true;
    }

    static boolean inHole(long seed, Hall h, int x, int z) {
        int dx = x - h.cx, dz = z - h.cz;
        double rough = 4 * CityLayout.noise(seed, x, z, 7.0, 7415) + 1.5 * CityLayout.noise(seed, x, z, 2.5, 7416);
        double ex = Math.min(dx - HX0, HX1 - dx), ez = Math.min(dz - HZ0, HZ1 - dz);
        return Math.min(ex, ez) > rough;
    }

    static int minY() { return CEIL - DEPTH - 8; }

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
        return pipeAt(seed, h, x, y, z);
    }
}
