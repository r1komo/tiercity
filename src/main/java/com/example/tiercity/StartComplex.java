package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.SplittableRandom;

/**
 * 시작 지점: 최하층 판 아래에 매달린 거대한 배관 공간(원작 1화).
 * 둘레는 판에서 늘어진 부서진 콘크리트 벽으로 막혀 있고, 바닥은 없다(아래는 끝없는 어둠).
 * 안에는 탱크, 칸막이벽 조각, 기계 상자가 매달려 있고, 배관이 판에 매달리거나 서로 갈라져 나오며 얽혀 있다.
 * 깊은 곳의 굵은 배관 위에서 시작한다. 탈 수 있는 굵은 배관이 여러 갈래로 엮여 있고(막다른 길도 있음),
 * 그중 한 갈래는 판 바로 아래의 정비용 발판으로 이어진다. 발판의 계단실이 판을 뚫고 지상의 작은 출입구 건물로 나온다.
 * 조명은 없다. (설정, 시드)만으로 결과가 정해진다.
 */
final class StartComplex {
    private StartComplex() {}

    /** 설정의 startInPipeHall. 생성기/이벤트가 설정에서 넣어 준다. */
    static volatile boolean enabled = true;

    static final int HALF = 120;
    static final int DEPTH = 120;                                 // 판 아랫면에서 배관이 내려가는 깊이
    static final int CEIL = CityLayout.layerBottom(0) - 1;        // 판 아랫면 바로 아래 y
    static final int LANDING = CEIL - 14;                         // 정비용 발판 윗면 y
    static final int LANDING_R = 9;
    static final int SDX = 40, SDZ = -20;                         // 계단실 중심(스폰 설정 좌표 기준)
    static final int SH_R = 5;                                    // 계단실 벽(체비쇼프 거리)
    static final int HUT_R = 6;                                   // 지상 출입구 건물 벽
    static final int SPAWN_DX = -84, SPAWN_DZ = 60;
    static final int ROUTE_R = 3;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState[] PIPE = {Blocks.GRAY_CONCRETE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(),
            Blocks.POLISHED_DEEPSLATE.defaultBlockState()};
    private static final BlockState FLANGE = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState RUST = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final BlockState WALL = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState WALL_CRACK = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState RIB = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TANK = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState TANK_BAND = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState BOX = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState BOX_EDGE = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState LANDING_FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState RAIL = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    private static final BlockState CORE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TREAD = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TREAD_SLAB = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState();
    private static final BlockState HUT_WALL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState HUT_FRAME = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState HUT_ROOF = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState HUT_EAVE = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();

    /** 배관 한 토막: A에서 B까지의 원통(끝은 둥긂). */
    static final class Seg {
        double ax, ay, az, bx, by, bz, r, len;
        int mat;
        boolean broken;                                           // B쪽 끝이 부러져 들쭉날쭉
        boolean walk;                                             // 타고 다니는 배관: 이음 고리는 아래쪽 반만(등은 매끈)
        int minX, maxX, minY, maxY, minZ, maxZ;

        Seg(double ax, double ay, double az, double bx, double by, double bz, double r, int mat) {
            this.ax = ax; this.ay = ay; this.az = az; this.bx = bx; this.by = by; this.bz = bz; this.r = r; this.mat = mat;
            len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
            int m = (int) Math.ceil(r) + 2;
            minX = (int) Math.floor(Math.min(ax, bx)) - m; maxX = (int) Math.ceil(Math.max(ax, bx)) + m;
            minY = (int) Math.floor(Math.min(ay, by)) - m; maxY = (int) Math.ceil(Math.max(ay, by)) + m;
            minZ = (int) Math.floor(Math.min(az, bz)) - m; maxZ = (int) Math.ceil(Math.max(az, bz)) + m;
        }

        /** 점에서 중심선까지 거리 제곱, 축 방향 위치(블록), 가장 가까운 중심선 점의 y. */
        double[] dist(double x, double y, double z) {
            double ux = bx - ax, uy = by - ay, uz = bz - az;
            double t = len < 1e-9 ? 0 : ((x - ax) * ux + (y - ay) * uy + (z - az) * uz) / (len * len);
            t = Math.max(0, Math.min(1, t));
            double cy = ay + uy * t;
            double px = ax + ux * t - x, py = cy - y, pz = az + uz * t - z;
            return new double[]{px * px + py * py + pz * pz, t * len, cy};
        }
    }

    /** 매달린 덩어리: 0 탱크(세로 원통, 아래 둥긂), 1 칸막이벽, 2 기계 상자. */
    static final class Mass {
        int type, x0, z0, x1, z1, bot, cx, cz;
        double r;

        boolean inBox(double x, double y, double z, double m) {
            return x >= x0 - m && x <= x1 + 1 + m && z >= z0 - m && z <= z1 + 1 + m && y >= bot - m && y <= CEIL + 1 + m;
        }
    }

    static final class Hall {
        int cx, cz, ytop, spx, spz, spy, shx, shz, laps;
        double pitch;
        Seg[] paths;                                              // 타고 다니는 배관(오르는 길 + 곁가지)
        Seg[] segs;                                               // 모든 배관
        Mass[] masses;
    }

    private static final ThreadLocal<HashMap<Long, Hall>> CACHE = ThreadLocal.withInitial(HashMap::new);

    static boolean active(CityLayout.Params p) {
        return enabled && p.layers() >= 1;
    }

    static boolean touches(CityLayout.Params p, int x, int z) {
        return active(p) && Math.abs(x - p.spawnX()) <= HALF && Math.abs(z - p.spawnZ()) <= HALF;
    }

    /** 지상 출입구 둘레(건물, 웅덩이 등을 두지 않는 곳). */
    static boolean exitZone(CityLayout.Params p, long seed, int x, int z, int margin) {
        if (!active(p)) return false;
        int sx = p.spawnX() + SDX, sz = p.spawnZ() + SDZ;
        return Math.max(Math.abs(x - sx), Math.abs(z - sz)) <= HUT_R + 1 + margin;
    }

    static boolean exitOverlaps(CityLayout.Params p, long seed, int x0, int z0, int x1, int z1, int margin) {
        if (!active(p)) return false;
        int sx = p.spawnX() + SDX, sz = p.spawnZ() + SDZ, e = HUT_R + 1 + margin;
        return x1 >= sx - e && x0 <= sx + e && z1 >= sz - e && z0 <= sz + e;
    }

    /** 처음 들어오는 플레이어가 서는 곳 {x, y(발), z}. */
    static int[] spawnPos(CityLayout.Params p, long seed) {
        Hall h = hall(p, seed);
        return new int[]{h.spx, h.spy, h.spz};
    }

    static Hall hall(CityLayout.Params p, long seed) {
        HashMap<Long, Hall> cache = CACHE.get();
        long key = CityLayout.hash(seed ^ ((long) p.hashCode() * 0xD6E8FEB86659FD93L), 0, 0, 7430);
        Hall h = cache.get(key);
        if (h == null) {
            h = build(p, seed);
            if (cache.size() > 16) cache.clear();
            cache.put(key, h);
        }
        return h;
    }

    // ---------------- 만들기 ----------------
    private static Hall build(CityLayout.Params p, long seed) {
        Hall h = new Hall();
        h.cx = p.spawnX(); h.cz = p.spawnZ();
        h.shx = h.cx + SDX; h.shz = h.cz + SDZ;
        CityLayout.Layer l = CityLayout.baseLayer(p, seed, 0, h.shx, h.shz);
        h.ytop = l != null ? l.top() : CityLayout.LEVEL_BASE;
        int rise = h.ytop - LANDING;
        h.laps = Math.max(1, (int) Math.ceil(rise / 10.0));
        h.pitch = rise / (double) h.laps;
        h.spx = h.cx + SPAWN_DX; h.spz = h.cz + SPAWN_DZ;

        SplittableRandom rng = new SplittableRandom(CityLayout.hash(seed, h.cx, h.cz, 7431));
        double r = ROUTE_R;
        double y0 = CEIL - 100;
        double yEnd = LANDING - 2.9;                              // 등 꼭대기가 발판 윗면과 같은 높이
        double[] start = {h.cx - 60, y0, h.cz + 60};
        double[] end = {h.shx - 24, yEnd, h.shz};
        // 오르는 길: 이리저리 꺾이며 일정한 기울기로 오름(가파르면 다시 고름)
        ArrayList<double[]> pts = null;
        for (int attempt = 0; attempt < 400 && pts == null; attempt++) {
            int n = 2 + rng.nextInt(3);
            ArrayList<double[]> cand = new ArrayList<>();
            cand.add(start);
            for (int i = 1; i <= n; i++) {
                cand.add(new double[]{h.cx - 100 + rng.nextInt(200), 0, h.cz - 100 + rng.nextInt(200)});
            }
            cand.add(end);
            double total = 0;
            for (int i = 1; i < cand.size(); i++) total += hdist(cand.get(i - 1), cand.get(i));
            double slope = (yEnd - y0) / total;
            if (slope < 0.18 || slope > 0.55) continue;
            double acc = 0;
            boolean ok = true;
            for (int i = 1; i < cand.size() - 1; i++) {
                acc += hdist(cand.get(i - 1), cand.get(i));
                cand.get(i)[1] = y0 + slope * acc;
                double[] c = cand.get(i);
                if (Math.max(Math.abs(c[0] - h.shx), Math.abs(c[2] - h.shz)) < 22) ok = false;   // 끝 전에는 계단실을 비켜 감
                if (Math.hypot(c[0] - h.spx, c[2] - h.spz) < 20) ok = false;
            }
            // 꺾이는 각도가 너무 급하면(되돌아감) 다시
            for (int i = 1; i < cand.size() - 1 && ok; i++) {
                double[] a = cand.get(i - 1), b = cand.get(i), c = cand.get(i + 1);
                double ux = b[0] - a[0], uz = b[2] - a[2], vx = c[0] - b[0], vz = c[2] - b[2];
                double cos = (ux * vx + uz * vz) / (Math.hypot(ux, uz) * Math.hypot(vx, vz) + 1e-9);
                if (cos < -0.3) ok = false;
            }
            if (ok) pts = cand;
        }
        if (pts == null) {   // 안전한 기본 길
            pts = new ArrayList<>();
            pts.add(start);
            pts.add(new double[]{h.cx + 80, 0, h.cz + 60});
            pts.add(new double[]{h.cx + 80, 0, h.cz - 80});
            pts.add(new double[]{h.cx - 40, 0, h.cz - 80});
            pts.add(end);
            double total = 0;
            for (int i = 1; i < pts.size(); i++) total += hdist(pts.get(i - 1), pts.get(i));
            double acc = 0;
            for (int i = 1; i < pts.size() - 1; i++) {
                acc += hdist(pts.get(i - 1), pts.get(i));
                pts.get(i)[1] = y0 + (yEnd - y0) * acc / total;
            }
        }
        ArrayList<Seg> paths = new ArrayList<>();
        int routeMat = pickMat(rng);
        paths.add(walkSeg(h.cx - 96, y0, h.cz + 60, start[0], start[1], start[2], r, routeMat));   // 스폰 배관
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            paths.add(walkSeg(a[0], a[1], a[2], b[0], b[1], b[2], r, rng.nextDouble() < 0.6 ? routeMat : pickMat(rng)));
        }
        paths.add(walkSeg(end[0], end[1], end[2], h.shx - 6, yEnd, h.shz, r, routeMat));            // 발판 밑으로 들어감
        int routeCount = paths.size();
        // 곁가지: 길 중간에서 갈라져 나가는 탈 수 있는 배관(대부분 막다른 길)
        for (int i = 0; i < 6; i++) {
            Seg base = paths.get(1 + rng.nextInt(routeCount - 2));
            double t = 0.2 + 0.6 * rng.nextDouble();
            double ax = base.ax + (base.bx - base.ax) * t, ay = base.ay + (base.by - base.ay) * t, az = base.az + (base.bz - base.az) * t;
            double yaw = Math.atan2(base.bz - base.az, base.bx - base.ax) + (rng.nextBoolean() ? 1 : -1) * (0.9 + rng.nextDouble() * 0.9);
            double L = 20 + rng.nextInt(30);
            double slope = -0.25 + rng.nextDouble() * 0.6;
            double bx = ax + Math.cos(yaw) * L, bz = az + Math.sin(yaw) * L, by = Math.min(CEIL - 9, ay + slope * L);
            if (Math.abs(bx - h.cx) > HALF - 14 || Math.abs(bz - h.cz) > HALF - 14) continue;
            if (Math.max(Math.abs(bx - h.shx), Math.abs(bz - h.shz)) < LANDING_R + 8) continue;
            Seg s = walkSeg(ax, ay, az, bx, by, bz, r, pickMat(rng));
            s.broken = rng.nextDouble() < 0.6;
            paths.add(s);
        }
        h.paths = paths.toArray(new Seg[0]);
        h.spy = (int) Math.floor(y0 + r + 0.45 - 0.5) + 1;
        while (h.spy > y0 && solidRaw(h.paths, h.spx, h.spy - 1, h.spz) == false) h.spy--;
        while (solidRaw(h.paths, h.spx, h.spy, h.spz)) h.spy++;

        // 매달린 덩어리
        ArrayList<Mass> masses = new ArrayList<>();
        int lo = -HALF + 14, hi = HALF - 14;
        for (int tries = 0, nT = 0, nW = 0, nB = 0; tries < 300 && (nT < 3 || nW < 3 || nB < 5); tries++) {
            Mass m = new Mass();
            m.type = nT < 3 ? 0 : nW < 3 ? 1 : 2;
            if (m.type == 0) {
                m.r = 5 + rng.nextInt(4);
                m.cx = h.cx + lo + rng.nextInt(hi - lo); m.cz = h.cz + lo + rng.nextInt(hi - lo);
                m.x0 = (int) (m.cx - m.r); m.x1 = (int) (m.cx + m.r); m.z0 = (int) (m.cz - m.r); m.z1 = (int) (m.cz + m.r);
                m.bot = CEIL - 18 - rng.nextInt(26);
            } else if (m.type == 1) {
                boolean ax = rng.nextBoolean();
                int len = 30 + rng.nextInt(40);
                int x = h.cx + lo + rng.nextInt(hi - lo - (ax ? len : 0)), z = h.cz + lo + rng.nextInt(hi - lo - (ax ? 0 : len));
                m.x0 = x; m.z0 = z; m.x1 = ax ? x + len : x + 1; m.z1 = ax ? z + 1 : z + len;
                m.bot = CEIL - 20 - rng.nextInt(36);
            } else {
                int w = 6 + rng.nextInt(9), d = 6 + rng.nextInt(9);
                int x = h.cx + lo + rng.nextInt(hi - lo - w), z = h.cz + lo + rng.nextInt(hi - lo - d);
                m.x0 = x; m.z0 = z; m.x1 = x + w; m.z1 = z + d;
                m.bot = CEIL - 6 - rng.nextInt(8);
            }
            if (!massClear(h, m)) continue;
            masses.add(m);
            if (m.type == 0) nT++; else if (m.type == 1) nW++; else nB++;
        }
        h.masses = masses.toArray(new Mass[0]);

        // 얽힌 배관: 판에 매달리거나 다른 배관/덩어리에서 갈라져 나옴
        ArrayList<Seg> segs = new ArrayList<>(paths);
        ArrayList<Seg> free = new ArrayList<>();
        int yLo = CEIL - DEPTH;
        int tries = 0;
        while (free.size() < 75 && tries++ < 4000) {
            double q = rng.nextDouble();
            double rad = q < 0.4 ? 1 : q < 0.75 ? 2 : q < 0.9 ? 3 : q < 0.97 ? 4 : 5;
            double ax, ay, az;
            double pick = rng.nextDouble();
            if (free.isEmpty() || pick < 0.35) {
                ax = h.cx + lo + rng.nextInt(hi - lo); az = h.cz + lo + rng.nextInt(hi - lo); ay = CEIL - rad + 1;
            } else if (pick < 0.5 && !masses.isEmpty()) {
                Mass m = masses.get(rng.nextInt(masses.size()));
                ax = m.x0 + rng.nextInt(m.x1 - m.x0 + 1); az = m.z0 + rng.nextInt(m.z1 - m.z0 + 1); ay = m.bot + 2;
            } else {
                Seg o = free.get(rng.nextInt(free.size()));
                double t = rng.nextDouble();
                ax = o.ax + (o.bx - o.ax) * t; ay = o.ay + (o.by - o.ay) * t; az = o.az + (o.bz - o.az) * t;
            }
            double kind = rng.nextDouble();
            double yaw = rng.nextDouble() * Math.PI * 2;
            double pitch;
            if (kind < 0.3) pitch = -(1.25 + rng.nextDouble() * 0.32);
            else if (kind < 0.8) pitch = (rng.nextDouble() - 0.5) * 0.4;
            else pitch = (rng.nextDouble() - 0.6) * 1.6;
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
            if (by < CEIL - 20 && rng.nextDouble() < 0.5) s.broken = true;
            if (!segClear(h, s)) continue;
            free.add(s);
            segs.add(s);
        }
        // 오르는 길을 받치는 줄기(아래로 늘어짐)
        for (int i = 0; i < routeCount; i += 2) {
            Seg b = paths.get(i);
            double sx = b.ax + (b.bx - b.ax) * 0.5, sy = b.ay + (b.by - b.ay) * 0.5, sz = b.az + (b.bz - b.az) * 0.5;
            Seg s = new Seg(sx, sy - 2, sz, sx + rng.nextInt(9) - 4, sy - 25 - rng.nextInt(35), sz + rng.nextInt(9) - 4, 1 + rng.nextInt(2), pickMat(rng));
            s.broken = true;
            segs.add(s);
        }
        h.segs = segs.toArray(new Seg[0]);
        return h;
    }

    private static double hdist(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[2] - a[2]);
    }

    private static Seg walkSeg(double ax, double ay, double az, double bx, double by, double bz, double r, int mat) {
        Seg s = new Seg(ax, ay, az, bx, by, bz, r, mat);
        s.walk = true;
        return s;
    }

    private static boolean solidRaw(Seg[] list, int x, int y, int z) {
        for (Seg g : list) {
            double[] d = g.dist(x + 0.5, y + 0.5, z + 0.5);
            if (d[0] <= (g.r + 0.45) * (g.r + 0.45)) return true;
        }
        return false;
    }

    private static int pickMat(SplittableRandom rng) {
        double m = rng.nextDouble();
        return m < 0.4 ? 0 : m < 0.62 ? 1 : m < 0.75 ? 2 : m < 0.87 ? 3 : 4;
    }

    /** 타는 배관 위 머리 공간, 옆, 계단실 둘레, 스폰을 막지 않는지(아래로 지나가거나 충분히 위로 지나가면 괜찮음). */
    private static boolean segClear(Hall h, Seg s) {
        int n = Math.max(2, (int) (s.len / 1.5));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double px = s.ax + (s.bx - s.ax) * t, py = s.ay + (s.by - s.ay) * t, pz = s.az + (s.bz - s.az) * t;
            if (!pointClear(h, px, py, pz, s.r)) return false;
        }
        return true;
    }

    private static boolean pointClear(Hall h, double px, double py, double pz, double rad) {
        for (Seg rt : h.paths) {
            double[] d = rt.dist(px, py, pz);
            if (Math.sqrt(d[0]) >= rad + ROUTE_R + 5) continue;
            double ry = d[2];
            boolean above = py - rad >= ry + ROUTE_R + 4;
            boolean below = py + rad <= ry - ROUTE_R - 1;
            if (!above && !below) return false;
        }
        if (Math.max(Math.abs(px - h.shx), Math.abs(pz - h.shz)) < LANDING_R + 5 + rad && py + rad > LANDING - 10) return false;
        double ds = Math.sqrt((px - h.spx) * (px - h.spx) + (py - h.spy) * (py - h.spy) + (pz - h.spz) * (pz - h.spz));
        return ds >= rad + 5;
    }

    private static boolean massClear(Hall h, Mass m) {
        for (Seg rt : h.paths) {
            int n = Math.max(2, (int) (rt.len / 1.5));
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                double px = rt.ax + (rt.bx - rt.ax) * t, py = rt.ay + (rt.by - rt.ay) * t, pz = rt.az + (rt.bz - rt.az) * t;
                if (m.inBox(px, py, pz, ROUTE_R + 5)) return false;
            }
        }
        if (m.x1 >= h.shx - LANDING_R - 6 && m.x0 <= h.shx + LANDING_R + 6 && m.z1 >= h.shz - LANDING_R - 6 && m.z0 <= h.shz + LANDING_R + 6) return false;
        return !m.inBox(h.spx, h.spy, h.spz, 6);
    }

    static int minY() { return CEIL - 150; }

    static int maxY(Hall h) { return h.ytop + 20; }

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
            double along = d[1];
            if (g.broken && along > g.len - 4) {
                double cut = g.len - 4 + 4 * CityLayout.hash01(seed, x * 7 + y, z, 7417);
                if (along > cut) continue;
            }
            if (d[0] <= (g.r + 0.45) * (g.r + 0.45)) {
                if (CityLayout.hash01(seed, x * 7 + y, z, 7418) < 0.035) return RUST;
                return PIPE[g.mat];
            }
            if (g.r >= 3 && d[0] <= (g.r + 1.45) * (g.r + 1.45) && Math.floorMod((int) Math.floor(along), 11) == 5
                    && along > 1 && along < g.len - 1) {
                if (g.walk && y + 0.5 > d[2] + g.r * 0.3) continue;      // 타는 배관: 등 쪽에는 고리 없음
                return FLANGE;
            }
        }
        return null;
    }

    private static BlockState massAt(long seed, Hall h, int x, int y, int z) {
        for (Mass m : h.masses) {
            if (x < m.x0 || x > m.x1 || z < m.z0 || z > m.z1 || y > CEIL) continue;
            switch (m.type) {
                case 0: {
                    double dx = x + 0.5 - (m.cx + 0.5), dz = z + 0.5 - (m.cz + 0.5);
                    double d2 = dx * dx + dz * dz, rr = m.r;
                    if (y < m.bot) continue;
                    if (y < m.bot + 3) rr = m.r * Math.sqrt(Math.max(0, 1 - Math.pow((m.bot + 3 - y) / 3.5, 2)));
                    if (d2 > rr * rr) continue;
                    if (Math.floorMod(y - m.bot, 7) == 3 && d2 >= (m.r - 1.2) * (m.r - 1.2)) return TANK_BAND;
                    return TANK;
                }
                case 1: {
                    int along = m.x1 - m.x0 > m.z1 - m.z0 ? x : z;
                    int bot = m.bot - (int) Math.round(10 * CityLayout.noise(seed, along, m.x0 + m.z0, 6.0, 7432))
                            - CityLayout.rand(seed, along, m.z0, 7433, 3);
                    if (y < bot) continue;
                    if (CityLayout.noise(seed, along, y, 7.0, 7434) > 0.8) continue;   // 뚫린 구멍
                    return CityLayout.hash01(seed, along * 3 + y, m.x0, 7435) < 0.1 ? WALL_CRACK : WALL;
                }
                default: {
                    if (y < m.bot) continue;
                    int e = 0;
                    if (x == m.x0 || x == m.x1) e++;
                    if (z == m.z0 || z == m.z1) e++;
                    if (y == m.bot || y == CEIL) e++;
                    return e >= 2 ? BOX_EDGE : BOX;
                }
            }
        }
        return null;
    }

    /** 둘레 벽: 판에서 늘어진 부서진 콘크리트 벽, 24칸마다 더 깊이 내려간 벽기둥. */
    private static BlockState wallAt(long seed, Hall h, int x, int y, int z) {
        int dx = x - h.cx, dz = z - h.cz;
        int e = HALF - Math.max(Math.abs(dx), Math.abs(dz));
        if (e > 4 || e < 0) return null;
        int along = Math.abs(dx) >= Math.abs(dz) ? z : x;
        boolean rib = Math.floorMod(along, 24) <= 2;
        if (e > 2 && !rib) return null;
        int bot = CEIL - 80 - (int) Math.round(55 * CityLayout.noise(seed, along, e > 2 ? 1 : 0, 22.0, 7436))
                - CityLayout.rand(seed, along, 7, 7437, 5);
        if (rib) bot -= 15;
        if (y < bot) return null;
        if (!rib && CityLayout.noise(seed, along, y, 9.0, 7438) > 0.8) return null;   // 뚫린 곳
        if (rib) return RIB;
        return CityLayout.hash01(seed, along * 3 + y, e, 7439) < 0.1 ? WALL_CRACK : WALL;
    }

    /** 계단실(발판에서 판을 뚫고 지상까지), 지상 출입구 건물. null이면 해당 없음. */
    private static BlockState shaft(Hall h, int x, int y, int z) {
        int dx = x - h.shx, dz = z - h.shz, c = Math.max(Math.abs(dx), Math.abs(dz));
        if (c > HUT_R + 1 || y <= LANDING) return null;
        int yt = h.ytop;
        // 지상 출입구 건물
        if (y > yt) {
            if (y > yt + 20) return null;
            if (c == HUT_R + 1) return null;
            int ry = y - yt;
            if (ry == 5) return c == HUT_R ? HUT_EAVE : HUT_ROOF;
            if (ry > 5) return AIR;
            if (c == HUT_R) {
                if (dx == -HUT_R && Math.abs(dz) <= 1 && ry <= 3) return AIR;                 // 출입문(서쪽)
                if (Math.abs(dx) == HUT_R && Math.abs(dz) == HUT_R) return HUT_FRAME;         // 모서리 기둥
                if (dx == -HUT_R && Math.abs(dz) == 2) return HUT_FRAME;                      // 문틀
                if (ry == 4) return HUT_FRAME;                                                // 띠
                return HUT_WALL;
            }
            return AIR;
        }
        if (c > SH_R) return null;
        if (c <= 2) return CORE;
        if (c == SH_R) {
            if (y > CEIL) return null;                                                        // 판이 벽
            if (dx == -SH_R && Math.abs(dz) <= 1 && y <= LANDING + 3) return AIR;             // 아래 출입구(서쪽)
            return WALL;
        }
        double s = Math.atan2(dz, -dx) / (2 * Math.PI);
        if (s < 0) s += 1;
        for (int n = 0; n < h.laps; n++) {
            double S = LANDING + 1 + (n + s) * h.pitch;
            if (S > yt + 1 + 1e-6) break;
            double q = Math.floor(S * 2) / 2;
            int full = (int) Math.floor(q);
            if (q - full < 0.25) { if (y == full - 1) return TREAD; }
            else if (y == full) return TREAD_SLAB;
        }
        return AIR;
    }

    /** 이 칸의 블록. null이면 손대지 않음(판, 공기 그대로). AIR이면 비움. */
    static BlockState block(long seed, Hall h, int x, int y, int z) {
        BlockState sh = shaft(h, x, y, z);
        if (sh != null) return sh;
        if (y > CEIL || y < minY()) return null;
        int dx = x - h.shx, dz = z - h.shz, c = Math.max(Math.abs(dx), Math.abs(dz));
        if (c <= LANDING_R) {
            if (y == LANDING) return LANDING_FLOOR;
            if (y == LANDING - 1 && c >= LANDING_R - 1) return BOX_EDGE;                       // 발판 테두리 보
            if (y == LANDING + 1 && c == LANDING_R && !(dx == -LANDING_R && Math.abs(dz) <= 3)) return RAIL;
            if (y > LANDING && Math.abs(dx) == LANDING_R - 1 && Math.abs(dz) == LANDING_R - 1) return BOX_EDGE;   // 발판을 매단 기둥
        }
        BlockState ps = pipeAt(seed, h, x, y, z);
        if (ps != null) return ps;
        BlockState ms = massAt(seed, h, x, y, z);
        if (ms != null) return ms;
        return wallAt(seed, h, x, y, z);
    }
}
