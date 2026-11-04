package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;

/**
 * 판 윗면 위의 도시(1단계): 도로망과 구획, 구역 종류, 1층 콘크리트 건물 골격, 가로등, 윗면만 파인 자국과 돌무더기, 눈.
 * CityLayout과 같은 원칙: (설정, 시드, 좌표)만으로 결과가 정해지는 순수 함수라서 청크 경계와 무관하게 같은 결과가 나온다.
 *
 * 층별 성격(원작의 흐름):
 *  - 1층: 무너진 옛 도시. 지붕(위층) 아래는 빽빽한 시가지, 하늘로 열린 바깥은 눈 덮인 전쟁터. 가로등은 꺼져 있다.
 *  - 중간층: 도로와 켜진 가로등(나머지 테마는 다음 단계).
 *  - 최상층: 눈 덮인 빈 평원(검은 돌은 다음 단계).
 */
final class DistrictLayout {
    private DistrictLayout() {}

    // ---------------- 수치 ----------------
    static final int ROAD_SPACING = 150;     // 도로 간격(평균)
    static final int ROAD_JITTER = 36;       // 도로 위치 흔들림
    static final int ROAD_HALF = 5;          // 도로 반폭(가운데 포함 11칸, 양끝 1칸은 연석)
    static final double WARP_MAX = 55;       // 바깥쪽에서 도로가 격자를 벗어나 휘는 정도
    static final double WARP_CELL = 300;
    static final double ROAD_DROP = 0.18;    // 도로 한 구간이 없는 확률(구획이 합쳐져 보임)
    static final int LIGHT_STEP = 30;        // 가로등 간격
    static final int POLE_H = 8;
    static final int CRATER_CELL = 40;
    static final int PILE_CELL = 28;
    static final int CEILING_MARGIN = 14;    // 건물 꼭대기와 위층 아랫면 사이 최소 여유
    static final int PLAZA_CLEAR = CityLayout.PLAZA_R + CityLayout.PLAZA_BLEND + 6;

    static final int DENSE = 0, SPARSE = 1, OPEN = 2;

    // ---------------- 블록 ----------------
    private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState ASPHALT_DARK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState PATCH = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState PAINT = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState CURB = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState RUBBLE_A = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState RUBBLE_B = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState RUBBLE_C = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState RUBBLE_D = Blocks.STONE.defaultBlockState();
    private static final BlockState POLE = Blocks.ANDESITE_WALL.defaultBlockState();
    private static final BlockState LAMP_OFF = Blocks.REDSTONE_LAMP.defaultBlockState();
    private static final BlockState LAMP_ON = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    private static final BlockState LAMP_CAP = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState B_SLAB = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState B_COLUMN = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState B_WALL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState B_WALL_WORN = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState B_FOUND = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState B_REBAR = Blocks.RAW_IRON_BLOCK.defaultBlockState();

    // ---------------- 결과 ----------------
    /** 표면 재질: 0 판 그대로, 1 아스팔트, 2 연석, 3 차선 도색, 4 눈 더미, 5 파인 자국 바닥(돌무더기). */
    static final int S_DECK = 0, S_ROAD = 1, S_CURB = 2, S_PAINT = 3, S_SNOWBANK = 4, S_CRATER = 5;
    /** 가로등: 0 없음, 1 부러진 기둥, 2 꺼진 가로등, 3 켜진 가로등. */
    static final int L_NONE = 0, L_STUB = 1, L_OFF = 2, L_ON = 3;

    /** 한 층의 한 컬럼 위 장식. top은 파인 자국을 반영한 판 윗면. */
    static final class Decor {
        int top;
        int surface = S_DECK;
        int pile;            // 윗면 위 돌무더기 높이
        int light = L_NONE;
        int lightH;          // 부러진 기둥 높이
        Building bld;        // 이 컬럼을 덮는 건물
        int snowY = -1;      // 얇은 눈이 쌓이는 y
        int maxY;

        boolean blocksStanding() { return bld != null || light != L_NONE; }

        /** 서 있을 수 있는 y. */
        int standY() { return snowY >= 0 ? snowY : top + pile + 1; }
    }

    // ---------------- 도로망 ----------------
    static int lineX(long seed, int k, int i) {
        return i * ROAD_SPACING + (int) Math.round((CityLayout.hash01(seed, i, k, 801) - 0.5) * 2 * ROAD_JITTER);
    }

    static int lineZ(long seed, int k, int j) {
        return j * ROAD_SPACING + (int) Math.round((CityLayout.hash01(seed, k, j, 802) - 0.5) * 2 * ROAD_JITTER);
    }

    static int cellX(long seed, int k, int x) {
        int i = Math.floorDiv(x, ROAD_SPACING);
        while (lineX(seed, k, i) > x) i--;
        while (lineX(seed, k, i + 1) <= x) i++;
        return i;
    }

    static int cellZ(long seed, int k, int z) {
        int j = Math.floorDiv(z, ROAD_SPACING);
        while (lineZ(seed, k, j) > z) j--;
        while (lineZ(seed, k, j + 1) <= z) j++;
        return j;
    }

    /** 바깥쪽일수록 1: 위층 가장자리 조금 안쪽부터 그 밖(하늘로 열린 전쟁터)까지 부드럽게 커진다. */
    static double outskirt(CityLayout.Params p, int k, double x, double z) {
        if (k + 1 >= p.layers()) return 1.0;
        double r1 = p.layerRadius(k + 1);
        double t = (Math.sqrt(x * x + z * z) - 0.8 * r1) / (0.35 * r1);
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** 도로망 좌표: 도심은 반듯한 격자, 바깥으로 갈수록 크게 휘어진다. */
    static int[] warp(CityLayout.Params p, long seed, int k, int x, int z) {
        double a = WARP_MAX * outskirt(p, k, x, z);
        if (a < 0.5) return new int[]{x, z};
        return new int[]{
                x + (int) Math.round(a * (2 * CityLayout.noise(seed, x, z, WARP_CELL, 830 + k) - 1)),
                z + (int) Math.round(a * (2 * CityLayout.noise(seed, x, z, WARP_CELL, 831 + k) - 1))};
    }

    private static double dropChance(CityLayout.Params p, int k, double x, double z) {
        return ROAD_DROP + 0.32 * outskirt(p, k, x, z);   // 바깥쪽은 도로가 끊겨 있는 곳이 많음
    }

    static int lotDistrict(CityLayout.Params p, long seed, int k, int ix, int jz) {
        int cx = (lineX(seed, k, ix) + lineX(seed, k, ix + 1)) / 2, cz = (lineZ(seed, k, jz) + lineZ(seed, k, jz + 1)) / 2;
        return district(p, seed, k, cx, cz);
    }

    /** 구획 ix와 ix+1이 하나의 거대 구획으로 합쳐졌는지(짝수 ix만 합침, 둘 다 빽빽한 구역일 때). */
    static boolean merged(CityLayout.Params p, long seed, int k, int ix, int jz) {
        if (k != 0 || Math.floorMod(ix, 2) != 0) return false;
        if (CityLayout.hash01(seed, ix, jz, 833) >= 0.3) return false;
        return lotDistrict(p, seed, k, ix, jz) == DENSE && lotDistrict(p, seed, k, ix + 1, jz) == DENSE;
    }

    /** 세로(남북) 도로 i의, 가로 구간 j 부분이 있는지. */
    static boolean nsRoad(CityLayout.Params p, long seed, int k, int i, int j) {
        if (merged(p, seed, k, i - 1, j)) return false;
        double mz = (lineZ(seed, k, j) + lineZ(seed, k, j + 1)) / 2.0;
        return CityLayout.hash01(seed, i, j, 803 + 17 * k) >= dropChance(p, k, lineX(seed, k, i), mz);
    }

    static boolean ewRoad(CityLayout.Params p, long seed, int k, int i, int j) {
        double mx = (lineX(seed, k, i) + lineX(seed, k, i + 1)) / 2.0;
        return CityLayout.hash01(seed, i, j, 804 + 17 * k) >= dropChance(p, k, mx, lineZ(seed, k, j));
    }

    /** (x, z)가 도로(연석 포함) 위인지. */
    static boolean roadAt(CityLayout.Params p, long seed, int k, int x, int z) {
        int[] u = warp(p, seed, k, x, z);
        int ix = cellX(seed, k, u[0]), jz = cellZ(seed, k, u[1]);
        int dxa = u[0] - lineX(seed, k, ix), dxb = lineX(seed, k, ix + 1) - u[0];
        int dza = u[1] - lineZ(seed, k, jz), dzb = lineZ(seed, k, jz + 1) - u[1];
        int li = dxa <= dxb ? ix : ix + 1, lj = dza <= dzb ? jz : jz + 1;
        return (Math.min(dxa, dxb) <= ROAD_HALF && nsRoad(p, seed, k, li, jz))
                || (Math.min(dza, dzb) <= ROAD_HALF && ewRoad(p, seed, k, ix, lj));
    }

    // ---------------- 구역 ----------------
    /** 하늘로 열린 곳인지(위층이 덮지 않음). */
    static boolean openSky(CityLayout.Params p, long seed, int k, int x, int z) {
        if (k + 1 >= p.layers()) return true;
        return !CityLayout.insideRim(p, seed, k + 1, x, z);
    }

    /**
     * 눈: 2 = 하늘로 열린 곳과 그 가장자리(두껍게, 눈 더미 포함), 1 = 지붕 밑으로 날려 들어온 흩어진 눈, 0 = 없음.
     * 위층 가장자리에서 안쪽으로 약 30블록까지는 이어지고, 그 안쪽 100블록쯤까지 점점 드문드문해진다.
     */
    static int snowAt(CityLayout.Params p, long seed, int k, int x, int z) {
        if (k + 1 >= p.layers()) return 2;
        if (!CityLayout.insideRim(p, seed, k + 1, x, z)) return 2;
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r < 1) return 0;
        double ux = x / r, uz = z / r;
        double n1 = CityLayout.noise(seed, x, z, 14.0, 822 + k);
        double d1 = 30.0 * n1;
        if (!CityLayout.insideRim(p, seed, k + 1, (int) Math.round(x + ux * d1), (int) Math.round(z + uz * d1))) return 2;
        double patch = CityLayout.noise(seed, x, z, 6.0, 824 + k) * 0.7 + CityLayout.noise(seed, x, z, 2.0, 825 + k) * 0.3;
        double d2 = 30 + 35 * CityLayout.noise(seed, x, z, 40.0, 823 + k);
        if (!CityLayout.insideRim(p, seed, k + 1, (int) Math.round(x + ux * d2), (int) Math.round(z + uz * d2))) {
            return patch > 0.45 ? 1 : 0;
        }
        double d3 = d2 + 45;
        if (!CityLayout.insideRim(p, seed, k + 1, (int) Math.round(x + ux * d3), (int) Math.round(z + uz * d3))) {
            return patch > 0.66 ? 1 : 0;
        }
        return 0;
    }

    static boolean isFinalLayer(CityLayout.Params p, int k) {
        return p.layers() >= 2 && k == p.layers() - 1;
    }

    static int district(CityLayout.Params p, long seed, int k, int x, int z) {
        double n = 0.75 * CityLayout.noise(seed, x, z, 900.0, 820 + k) + 0.25 * CityLayout.noise(seed, x, z, 260.0, 821 + k);
        if (openSky(p, seed, k, x, z)) n -= 0.12;   // 하늘 아래 바깥 고리: 전쟁터(더 비고 더 부서짐)
        else n += 0.08;                              // 지붕(위층) 아래: 빽빽한 콘크리트 정글
        if (n > 0.52) return DENSE;
        if (n > 0.36) return SPARSE;
        return OPEN;
    }

    /** 탑/기둥 격자점 둘레(광장) 안인지. */
    static boolean nearGrid(int x, int z, double r) {
        int tx = CityLayout.towerCoord(x), tz = CityLayout.towerCoord(z);
        double dx = x - tx, dz = z - tz;
        return dx * dx + dz * dz <= r * r;
    }

    private static double rectDist(int x0, int z0, int x1, int z1, double px, double pz) {
        double dx = Math.max(0, Math.max(x0 - px, px - x1));
        double dz = Math.max(0, Math.max(z0 - pz, pz - z1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------- 건물 ----------------
    static final class Building {
        int x0, z0, x1, z1, base, fh, floors, cs, cw, id;
        double wallKeep, dmg;
        boolean openSky;

        int w() { return x1 - x0 + 1; }
        int d() { return z1 - z0 + 1; }
        int nominalTop() { return base + floors * fh; }
        boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    static final class Lot {
        int district;
        Building[] buildings = new Building[0];
    }

    private static final ThreadLocal<HashMap<Long, Lot>> LOT_CACHE = ThreadLocal.withInitial(HashMap::new);

    static Lot lot(CityLayout.Params p, long seed, int k, int ix, int jz) {
        HashMap<Long, Lot> cache = LOT_CACHE.get();
        long key = seed ^ (ix * 0x9E3779B97F4A7C15L) ^ (jz * 0xC2B2AE3D27D4EB4FL) ^ (k * 0x165667B19E3779F9L)
                ^ ((long) p.hashCode() * 0xD6E8FEB86659FD93L);
        Lot cached = cache.get(key);
        if (cached != null) return cached;
        Lot lot = buildLot(p, seed, k, ix, jz);
        if (cache.size() > 4096) cache.clear();
        cache.put(key, lot);
        return lot;
    }

    private static Lot buildLot(CityLayout.Params p, long seed, int k, int ix, int jz) {
        Lot lot = new Lot();
        lot.district = lotDistrict(p, seed, k, ix, jz);
        if (k != 0) return lot;                                   // 1단계: 건물은 1층만
        if (merged(p, seed, k, ix - 1, jz)) return lot;           // 서쪽 구획과 합쳐짐: 건물은 서쪽 구획이 지음
        boolean mega = merged(p, seed, k, ix, jz);
        int lx0 = lineX(seed, k, ix) + ROAD_HALF + 1, lx1 = lineX(seed, k, ix + (mega ? 2 : 1)) - ROAD_HALF - 1;
        int lz0 = lineZ(seed, k, jz) + ROAD_HALF + 1, lz1 = lineZ(seed, k, jz + 1) - ROAD_HALF - 1;
        if (lx1 - lx0 < 18 || lz1 - lz0 < 18) return lot;

        double r = CityLayout.hash01(seed, ix, jz, 830);
        double chance = mega ? 1.0 : (lot.district == DENSE ? 0.95 : lot.district == SPARSE ? 0.75 : 0.2);
        if (r >= chance * p.buildingDensity()) return lot;

        int[][] rects;
        int w = lx1 - lx0 + 1, d = lz1 - lz0 + 1;
        if (!mega && Math.max(w, d) > 110 && CityLayout.hash01(seed, ix, jz, 831) < 0.6) {
            double f = 0.35 + 0.3 * CityLayout.hash01(seed, ix, jz, 832);
            int gap = 5;
            if (w >= d) {
                int sx = lx0 + (int) (w * f);
                rects = new int[][]{{lx0, lz0, sx - gap, lz1}, {sx + gap, lz0, lx1, lz1}};
            } else {
                int sz = lz0 + (int) (d * f);
                rects = new int[][]{{lx0, lz0, lx1, sz - gap}, {lx0, sz + gap, lx1, lz1}};
            }
        } else {
            rects = new int[][]{{lx0, lz0, lx1, lz1}};
        }

        java.util.ArrayList<Building> list = new java.util.ArrayList<>();
        for (int bi = 0; bi < rects.length; bi++) {
            int[] rc = rects[bi];
            int salt = bi * 13;
            int sx0 = rc[0] + 1 + CityLayout.rand(seed, ix, jz, 840 + salt, 6);
            int sx1 = rc[2] - 1 - CityLayout.rand(seed, ix, jz, 841 + salt, 6);
            int sz0 = rc[1] + 1 + CityLayout.rand(seed, ix, jz, 842 + salt, 6);
            int sz1 = rc[3] - 1 - CityLayout.rand(seed, ix, jz, 843 + salt, 6);
            if (sx1 - sx0 < 15 || sz1 - sz0 < 15) continue;

            // 탑 광장과 기둥 둘레는 비워 둔다: 건물을 그쪽 면에서 잘라 냄(가장 넓게 남는 쪽)
            boolean blocked = false;
            int[] gx = {CityLayout.towerCoord(sx0), CityLayout.towerCoord(sx1)};
            int[] gz = {CityLayout.towerCoord(sz0), CityLayout.towerCoord(sz1)};
            for (int a : gx) for (int b : gz) {
                if (rectDist(sx0, sz0, sx1, sz1, a, b) >= PLAZA_CLEAR) continue;
                int c = PLAZA_CLEAR + 1;
                int[][] opt = {{a + c, sz0, sx1, sz1}, {sx0, sz0, a - c, sz1}, {sx0, b + c, sx1, sz1}, {sx0, sz0, sx1, b - c}};
                long bestArea = -1;
                int[] best = null;
                for (int[] o : opt) {
                    int ox0 = Math.max(sx0, o[0]), oz0 = Math.max(sz0, o[1]), ox1 = Math.min(sx1, o[2]), oz1 = Math.min(sz1, o[3]);
                    if (ox1 - ox0 < 15 || oz1 - oz0 < 15) continue;
                    long ar = (long) (ox1 - ox0) * (oz1 - oz0);
                    if (ar > bestArea) { bestArea = ar; best = new int[]{ox0, oz0, ox1, oz1}; }
                }
                if (best == null) { blocked = true; break; }
                sx0 = best[0]; sz0 = best[1]; sx1 = best[2]; sz1 = best[3];
            }
            if (blocked) continue;

            // 휘어진 도로가 건물을 지나가지 않는지(둘레를 따라 확인)
            for (int t = 0; t <= 2 * ((sx1 - sx0) + (sz1 - sz0)) && !blocked; t += 5) {
                int px, pz, ww = sx1 - sx0, dd = sz1 - sz0;
                if (t <= ww) { px = sx0 + t; pz = sz0; }
                else if (t <= ww + dd) { px = sx1; pz = sz0 + (t - ww); }
                else if (t <= 2 * ww + dd) { px = sx1 - (t - ww - dd); pz = sz1; }
                else { px = sx0; pz = sz1 - (t - 2 * ww - dd); }
                if (roadAt(p, seed, k, px, pz)) blocked = true;
            }
            if (blocked) continue;

            // 판이 온전한지(구멍 위에 짓지 않음)와 기초 높이
            // 균열이나 작은 구멍 위는 기초가 메운다. 판이 크게 비어 있으면 짓지 않음.
            int maxTop = Integer.MIN_VALUE, missing = 0;
            for (int a = 0; a <= 6; a++) {
                for (int b = 0; b <= 6; b++) {
                    int px = sx0 + (sx1 - sx0) * a / 6, pz = sz0 + (sz1 - sz0) * b / 6;
                    CityLayout.Layer l = CityLayout.baseLayer(p, seed, k, px, pz);
                    if (l == null) { missing++; continue; }
                    maxTop = Math.max(maxTop, l.maxY());
                }
            }
            if (missing > 6 || maxTop == Integer.MIN_VALUE) continue;

            Building bd = new Building();
            bd.x0 = sx0; bd.x1 = sx1; bd.z0 = sz0; bd.z1 = sz1;
            bd.id = (int) (CityLayout.hash01(seed, ix * 7 + bi, jz, 844) * Integer.MAX_VALUE);
            bd.base = maxTop + 1;
            bd.openSky = openSky(p, seed, k, (sx0 + sx1) / 2, (sz0 + sz1) / 2);
            bd.fh = CityLayout.hash01(seed, ix, jz, 845 + salt) < 0.6 ? 6 : 7;
            bd.cs = (mega ? 11 : 9) + CityLayout.rand(seed, ix, jz, 846 + salt, 5);
            bd.cw = bd.cs >= 11 ? 3 : 2;

            int minH, maxH;
            if (mega) { minH = 90; maxH = 400; }
            else if (lot.district == DENSE) { minH = 45; maxH = 140; }
            else if (lot.district == SPARSE) { minH = 15; maxH = 60; }
            else { minH = 6; maxH = 22; }
            maxH = Math.min(maxH, p.buildingMaxHeight());
            minH = Math.min(minH, maxH);
            double hr = CityLayout.hash01(seed, ix, jz, 847 + salt);
            int h = minH + (int) ((maxH - minH) * Math.pow(hr, 0.6));
            int ceil = Integer.MAX_VALUE;
            boolean covered = false;
            for (int a : new int[]{sx0, sx1}) for (int b : new int[]{sz0, sz1}) {
                if (k + 1 < p.layers() && CityLayout.insideRim(p, seed, k + 1, a, b)) covered = true;
            }
            if (covered || (k + 1 < p.layers() && !bd.openSky)) ceil = CityLayout.layerBottom(k + 1) - CEILING_MARGIN;
            h = Math.min(h, ceil - bd.base - 4);
            bd.floors = h / bd.fh;
            if (bd.floors < 2) continue;

            double kw = CityLayout.hash01(seed, ix, jz, 848 + salt);
            double kd = CityLayout.hash01(seed, ix, jz, 849 + salt);
            if (lot.district == DENSE) { bd.wallKeep = 0.55 + 0.35 * kw; bd.dmg = 0.12 + 0.33 * kd; }
            else { bd.wallKeep = 0.25 + 0.35 * kw; bd.dmg = 0.40 + 0.35 * kd; }
            if (bd.openSky) { bd.wallKeep *= 0.7; bd.dmg = Math.min(0.9, bd.dmg + 0.15); }
            if (CityLayout.hash01(seed, ix, jz, 850 + salt) < 0.12) bd.dmg = 0.85;   // 거의 다 무너진 건물
            list.add(bd);
        }
        lot.buildings = list.toArray(new Building[0]);
        return lot;
    }

    /** (x, z)를 덮는 건물(없으면 null). 도로 위는 항상 null. */
    static Building buildingAt(CityLayout.Params p, long seed, int k, int x, int z) {
        if (k != 0 || isFinalLayer(p, k)) return null;
        int[] u = warp(p, seed, k, x, z);
        int ix = cellX(seed, k, u[0]), jz = cellZ(seed, k, u[1]);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                for (Building bd : lot(p, seed, k, ix + a, jz + b).buildings) {
                    if (bd.contains(x, z)) return bd;
                }
            }
        }
        return null;
    }

    /** 무너진 정도를 반영한 이 컬럼의 건물 꼭대기. */
    static int colTop(long seed, Building b, int x, int z) {
        int hgt = b.floors * b.fh;
        double cell = Math.max(12.0, Math.min(b.w(), b.d()) * 0.6);
        double n = CityLayout.noise(seed, x, z, cell, 860 + (b.id & 1023));
        double jag = CityLayout.noise(seed, x, z, 4.0, 861) - 0.5;
        double c = hgt * b.dmg * (0.25 + 1.3 * n) + jag * 3.0;
        int top = b.nominalTop() - (int) Math.max(0, Math.round(c));
        return Math.max(b.base + 1, Math.min(b.nominalTop(), top));
    }

    private static boolean isColLine(int l, int len, int cs, int cw) {
        return Math.floorMod(l, cs) < cw || l >= len - cw;
    }

    /** 건물 블록. null이면 건물이 아무것도 두지 않음(공기 또는 판). deckTop 이하는 다루지 않는다. */
    static BlockState buildingBlock(long seed, Building b, int x, int y, int z, int deckTop) {
        if (y <= deckTop) return null;
        if (y < b.base) return B_FOUND;                         // 기초: 판 윗면에서 바닥판까지 채움
        int lx = x - b.x0, lz = z - b.z0, w = b.w(), d = b.d();
        boolean isCol = isColLine(lx, w, b.cs, b.cw) && isColLine(lz, d, b.cs, b.cw);
        int top = colTop(seed, b, x, z);
        if (y > top) {
            if (isCol && top < b.nominalTop() - b.fh && y <= top + 1 + CityLayout.rand(seed, x >> 1, z >> 1, 862, 4)
                    && CityLayout.hash01(seed, x >> 1, z >> 1, 863) < 0.2) return B_REBAR;    // 크게 무너진 자리의 부러진 기둥에서 솟은 철근
            return null;
        }
        int rel = y - b.base, f = rel / b.fh, ry = rel % b.fh;
        if (ry == 0) {
            if (f > 0 && CityLayout.noise(seed, x + f * 131, z, 9.0, 864) > 0.66 + 0.25 * (1 - b.dmg)) return null;   // 무너진 바닥
            return B_SLAB;
        }
        if (isCol) return B_COLUMN;
        boolean west = lx == 0, east = lx == w - 1, north = lz == 0, south = lz == d - 1;
        if (!(west || east || north || south)) return null;      // 내부는 비어 있음
        int side = west ? 0 : east ? 1 : north ? 2 : 3;
        if (CityLayout.hash01(seed, b.id + side * 7919, f, 865) >= b.wallKeep) return null;   // 벗겨진 외벽
        int along = (side <= 1) ? lz : lx;
        int bay = Math.floorMod(along, b.cs);
        if (f == 0 && ry >= 1 && CityLayout.hash01(seed, b.id + side, along / b.cs, 866) < 0.35) return null;   // 입구
        if (ry >= 2 && ry <= b.fh - 2 && bay >= b.cw + 1 && bay <= b.cs - 2 && along < (side <= 1 ? b.d() : b.w()) - b.cw - 1) return null;   // 창(기둥 양옆에 벽 한 칸씩)
        return CityLayout.hash01(seed, x * 31 + y, z, 867) < 0.12 ? B_WALL_WORN : B_WALL;
    }

    // ---------------- 파인 자국, 돌무더기 ----------------
    private static double craterChance(int district, int k, boolean finalLayer) {
        if (finalLayer) return 0;
        double c = district == OPEN ? 0.62 : district == SPARSE ? 0.38 : 0.13;
        return k == 0 ? c : c * 0.25;
    }

    /** 파인 깊이(>0)이거나, 가장자리 둔덕이면 -1, 아니면 0. */
    static int crater(CityLayout.Params p, long seed, int k, int x, int z) {
        int cx0 = Math.floorDiv(x, CRATER_CELL), cz0 = Math.floorDiv(z, CRATER_CELL);
        int best = 0;
        boolean rim = false;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                int ci = cx0 + a, cj = cz0 + b;
                double hr = CityLayout.hash01(seed, ci, cj, 870 + k);
                if (hr >= 0.62) continue;
                double mx = ci * CRATER_CELL + 8 + CityLayout.hash01(seed, ci, cj, 871 + k) * (CRATER_CELL - 16);
                double mz = cj * CRATER_CELL + 8 + CityLayout.hash01(seed, ci, cj, 872 + k) * (CRATER_CELL - 16);
                double r = 3 + 5 * CityLayout.hash01(seed, ci, cj, 873 + k);
                double dd = Math.hypot(x + 0.5 - mx, z + 0.5 - mz);
                if (dd > r + 1.5) continue;
                if (hr >= craterChance(district(p, seed, k, (int) mx, (int) mz), k, isFinalLayer(p, k))) continue;
                if (dd < r) {
                    int dep = (int) Math.round((1 + 0.45 * r) * (1 - (dd / r) * (dd / r)));
                    best = Math.max(best, dep);
                } else if (CityLayout.noise(seed, x, z, 2.5, 874) > 0.4) {
                    rim = true;
                }
            }
        }
        return best > 0 ? best : (rim ? -1 : 0);
    }

    private static double pileChance(int district, int k, boolean finalLayer) {
        if (finalLayer) return 0;
        double c = district == DENSE ? 0.2 : 0.44;
        return k == 0 ? c : c * 0.25;
    }

    static int pile(CityLayout.Params p, long seed, int k, int x, int z) {
        int cx0 = Math.floorDiv(x, PILE_CELL), cz0 = Math.floorDiv(z, PILE_CELL);
        double best = 0;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                int ci = cx0 + a, cj = cz0 + b;
                double hr = CityLayout.hash01(seed, ci, cj, 880 + k);
                if (hr >= 0.45) continue;
                double mx = ci * PILE_CELL + 5 + CityLayout.hash01(seed, ci, cj, 881 + k) * (PILE_CELL - 10);
                double mz = cj * PILE_CELL + 5 + CityLayout.hash01(seed, ci, cj, 882 + k) * (PILE_CELL - 10);
                double r = 2 + 4 * CityLayout.hash01(seed, ci, cj, 883 + k);
                double dd = Math.hypot(x + 0.5 - mx, z + 0.5 - mz);
                if (dd >= r) continue;
                if (hr >= pileChance(district(p, seed, k, (int) mx, (int) mz), k, isFinalLayer(p, k))) continue;
                double hmax = 1 + 3 * CityLayout.hash01(seed, ci, cj, 884 + k);
                double h = hmax * (1 - dd / r) * (0.7 + 0.6 * CityLayout.noise(seed, x, z, 3.0, 885));
                best = Math.max(best, h);
            }
        }
        return (int) Math.round(best);
    }

    // ---------------- 한 컬럼 꾸미기 ----------------
    /**
     * k층 판 한 컬럼 위의 장식. layer는 판(파인 자국 반영 전), 반환값의 top이 실제 윗면.
     * 장식이 전혀 없으면 null.
     */
    static Decor decorate(CityLayout.Params p, long seed, int k, int x, int z, CityLayout.Layer layer) {
        boolean finalLayer = isFinalLayer(p, k);
        int snow = snowAt(p, seed, k, x, z);
        Decor dc = new Decor();
        dc.top = layer.top();
        boolean any = false;
        boolean plaza = nearGrid(x, z, PLAZA_CLEAR);
        boolean onTalus = layer.talus() || layer.slab() || layer.debris() > 0;

        if (!finalLayer) {
            int[] u = warp(p, seed, k, x, z);
            int ix = cellX(seed, k, u[0]), jz = cellZ(seed, k, u[1]);
            int dxa = u[0] - lineX(seed, k, ix), dxb = lineX(seed, k, ix + 1) - u[0];
            int dza = u[1] - lineZ(seed, k, jz), dzb = lineZ(seed, k, jz + 1) - u[1];
            boolean nearW = dxa <= dxb;
            int dx = Math.min(dxa, dxb), dz = Math.min(dza, dzb);
            int li = nearW ? ix : ix + 1;                       // 가까운 세로 도로
            int lj = (dza <= dzb) ? jz : jz + 1;                // 가까운 가로 도로
            boolean ns = dx <= ROAD_HALF && nsRoad(p, seed, k, li, jz);
            boolean ew = dz <= ROAD_HALF && ewRoad(p, seed, k, ix, lj);
            dc.bld = buildingAt(p, seed, k, x, z);

            if (dc.bld != null) {
                any = true;                                     // 건물이 도로보다 우선(휘어진 도로 가장자리)
            } else if ((ns || ew) && onTalus) {
                // 도로가 잔해 비탈에 묻힘: 아무것도 하지 않음
            } else if (ns || ew) {
                // 도로
                boolean curb = (ns && dx == ROAD_HALF && !(ew && dz < ROAD_HALF)) || (ew && dz == ROAD_HALF && !(ns && dx < ROAD_HALF));
                if (curb) {
                    dc.surface = S_CURB;
                } else if ((ns && dx == 0 && !ew && Math.floorMod(z, 8) < 4) || (ew && dz == 0 && !ns && Math.floorMod(x, 8) < 4)) {
                    dc.surface = CityLayout.hash01(seed, x >> 2, z >> 2, 890) < 0.35 ? S_PAINT : S_ROAD;
                } else {
                    dc.surface = S_ROAD;
                }
                any = true;

                // 가로등: 연석 위, 일정 간격, 교차로 피함, 양쪽 번갈아
                if (curb) {
                    int light = lightAt(seed, k, ns && dx == ROAD_HALF, x, z, li, lj, nearW, dza <= dzb, dx, dz);
                    if (light != L_NONE) {
                        dc.light = light;
                        if (light == L_STUB) dc.lightH = 1 + CityLayout.rand(seed, x, z, 891, 3);
                    }
                }
            } else {
                // 구획 안: 건물 둘레에 무너져 내린 돌무더기
                if (!plaza && k == 0) {
                    for (int a = -1; a <= 1; a++) for (int bb = -1; bb <= 1; bb++) for (Building b : lot(p, seed, k, ix + a, jz + bb).buildings) {
                        double e = rectDist(b.x0, b.z0, b.x1, b.z1, x + 0.5, z + 0.5);
                        if (e < 6 && b.dmg > 0.3) {
                            int h = (int) Math.round(3 * (1 - e / 6) * (CityLayout.noise(seed, x, z, 3.0, 892) - 0.25) * 1.6);
                            if (h > dc.pile) { dc.pile = h; any = true; }
                        }
                    }
                }
            }

            // 윗면만 파인 자국과 돌무더기(광장, 건물 안, 잔해 비탈 제외)
            if (!plaza && !onTalus && dc.bld == null && dc.light == L_NONE) {
                int cr = crater(p, seed, k, x, z);
                if (cr > 0) {
                    int lim = layer.top() - (layer.bottom() + CityLayout.MIN_THICK - 1);
                    int dep = Math.min(cr, Math.max(0, lim));
                    if (dep > 0) {
                        dc.top = layer.top() - dep;
                        dc.surface = S_CRATER;
                        dc.pile = 0;
                        any = true;
                    }
                } else {
                    int pl = Math.max(pile(p, seed, k, x, z), cr < 0 ? 1 : 0);
                    if (pl > dc.pile) { dc.pile = pl; any = true; }
                }
            }
        }

        // 눈: 하늘로 열린 곳의 맨 위 표면
        if (snow > 0 && dc.light == L_NONE && !(layer.slab() && dc.bld == null && dc.pile == 0)) {   // 반 블록 위에는 눈이 버티지 못함
            int topSolid = dc.top + dc.pile;
            if (dc.bld != null) topSolid = Math.max(topSolid, buildingTopSolid(seed, dc.bld, x, z, layer.top()));
            dc.snowY = topSolid + 1;
            if (snow == 2 && dc.bld == null && dc.pile == 0 && dc.surface != S_CRATER
                    && CityLayout.noise(seed, x, z, 24.0, 893) > 0.62) {
                dc.surface = S_SNOWBANK;
            }
            any = true;
        }

        if (!any) return null;
        int mx = dc.top + dc.pile;
        if (dc.bld != null) mx = Math.max(mx, dc.bld.nominalTop() + 5);
        if (dc.light == L_STUB) mx = Math.max(mx, dc.top + dc.lightH);
        else if (dc.light != L_NONE) mx = Math.max(mx, dc.top + POLE_H + 2);
        if (dc.snowY >= 0) mx = Math.max(mx, dc.snowY);
        dc.maxY = mx;
        return dc;
    }

    private static int lightAt(long seed, int k, boolean onNs, int x, int z, int li, int lj,
                               boolean westOfLine, boolean northOfLine, int dx, int dz) {
        int along = onNs ? z : x;
        int lineIdx = onNs ? li : lj;
        int off = CityLayout.rand(seed, lineIdx, onNs ? 0 : 1, 895 + k, LIGHT_STEP);
        int slot = Math.floorDiv(along - off, LIGHT_STEP);
        if (Math.floorMod(along - off, LIGHT_STEP) != 0) return L_NONE;
        if ((onNs ? dz : dx) <= ROAD_HALF + 3) return L_NONE;                  // 교차로 근처 제외
        boolean wantFirst = Math.floorMod(slot, 2) == 0;
        boolean isFirst = onNs ? westOfLine : northOfLine;
        if (wantFirst != isFirst) return L_NONE;                                // 양쪽 번갈아
        double r = CityLayout.hash01(seed, x, z, 896 + k);
        if (k == 0) {
            if (r < 0.2) return L_NONE;
            return r < 0.42 ? L_STUB : L_OFF;
        }
        if (r < 0.08) return L_NONE;
        return r < 0.9 ? L_ON : L_OFF;
    }

    private static int buildingTopSolid(long seed, Building b, int x, int z, int deckTop) {
        for (int y = b.nominalTop() + 5; y > deckTop; y--) {
            if (buildingBlock(seed, b, x, y, z, deckTop) != null) return y;
        }
        return deckTop;
    }

    // ---------------- 블록 ----------------
    private static BlockState rubble(long seed, int x, int y, int z) {
        int r = CityLayout.rand(seed, x * 31 + y, z, 897, 10);
        if (r < 4) return RUBBLE_A;
        if (r < 7) return RUBBLE_B;
        if (r < 9) return RUBBLE_D;
        return RUBBLE_C;
    }

    /** 장식이 정한 블록, 없으면 null(판이나 공기가 그대로). layerTop은 판의 원래 윗면(파이기 전). */
    static BlockState decorBlock(long seed, Decor dc, int x, int y, int z) {
        if (y == dc.snowY) return SNOW;
        if (dc.bld != null) {
            BlockState s = buildingBlock(seed, dc.bld, x, y, z, dc.top);
            if (s != null) return s;
        }
        if (y == dc.top) {
            switch (dc.surface) {
                case S_ROAD: {
                    double n = CityLayout.noise(seed, x, z, 6.0, 898);
                    if (CityLayout.hash01(seed, x, z, 899) < 0.04) return PATCH;
                    return n > 0.62 ? ASPHALT_DARK : ASPHALT;
                }
                case S_CURB: return CURB;
                case S_PAINT: return PAINT;
                case S_SNOWBANK: return SNOW_BLOCK;
                case S_CRATER: return rubble(seed, x, y, z);
                default: return null;
            }
        }
        if (y > dc.top && y <= dc.top + dc.pile) return rubble(seed, x, y, z);
        if (dc.light != L_NONE && y > dc.top) {
            int rel = y - dc.top;
            if (dc.light == L_STUB) return rel <= dc.lightH ? POLE : null;
            if (rel <= POLE_H) return POLE;
            if (rel == POLE_H + 1) return dc.light == L_ON ? LAMP_ON : LAMP_OFF;
            if (rel == POLE_H + 2) return LAMP_CAP;
        }
        return null;
    }
}
