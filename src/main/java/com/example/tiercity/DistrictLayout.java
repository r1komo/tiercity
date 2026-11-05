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
    static final int ROAD_SPACING = 260;     // 큰길 간격(평균). 큰길 사이 구획은 골목으로 잘게 쪼개진다
    static final int ROAD_JITTER = 36;       // 도로 위치 흔들림
    static final int ROAD_HALF = 5;          // 도로 반폭(가운데 포함 11칸, 양끝 1칸은 연석)
    static final double WARP_MAX = 55;       // 바깥쪽에서 도로가 격자를 벗어나 휘는 정도
    static final double WARP_CELL = 300;
    static final double ROAD_DROP = 0.18;    // 도로 한 구간이 없는 확률(구획이 합쳐져 보임)
    static final int LIGHT_STEP = 12;        // 큰길 가로등 간격(양쪽 모두)
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
    private static final BlockState WINDOW = Blocks.GLASS.defaultBlockState();
    private static final BlockState WINDOW_LIT = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState A_WALL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState A_FRAME = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState STEP_SLAB = Blocks.STONE_BRICK_SLAB.defaultBlockState();
    private static final BlockState RUNWAY_LIGHT = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();

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
        Landmarks.Struct st; // 이 컬럼을 덮는 구조물(건물, 석상, 사원, 공군 기지)
        int snowY = -1;      // 얇은 눈이 쌓이는 y
        int maxY;

        boolean blocksStanding() { return st != null || light != L_NONE; }

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
        if (Landmarks.cityLayer(p, k)) n += 0.06;    // 2층: 아직 사람이 살던 모습이 남은 도시
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

    // ---------------- 도시 조직: 큰길 사이 구획을 잘게 쪼갠 부지와 골목 ----------------
    static final int RUIN = 0, APARTMENT = 1;

    /** 건물의 한 덩어리: 사각형(구멍 하나 가능)이 f0층부터 f1-1층까지 차지. */
    static final class Vol {
        int x0, z0, x1, z1, f0, f1;
        int hx0 = 1, hz0 = 1, hx1 = 0, hz1 = 0;

        Vol(int x0, int z0, int x1, int z1, int f0, int f1) {
            this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.f0 = f0; this.f1 = f1;
        }

        boolean inPlan(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1 && !(x >= hx0 && x <= hx1 && z >= hz0 && z <= hz1);
        }

        boolean has(int x, int z, int f) { return f >= f0 && f < f1 && inPlan(x, z); }
    }

    static final class Building extends Landmarks.Piece {
        int base, fh, floors, cs, cw, id, style, k;
        double wallKeep, dmg;
        boolean openSky;
        Vol[] vols;
        int balconySides;                     // 비트: 0 서(-x), 1 동(+x), 2 북(-z), 3 남(+z)
        int stairX = Integer.MIN_VALUE, stairZ, stairF;
        int[][] doors = new int[0][];         // 다리 출입구 {x0, z0, x1, z1, y}

        @Override
        public BlockState block(long seed, int x, int y, int z, int deckTop) {
            return style == APARTMENT ? apartmentBlock(seed, this, x, y, z, deckTop) : buildingBlock(seed, this, x, y, z, deckTop);
        }

        @Override
        public int maxY() { return nominalTop() + 5; }

        @Override
        public int topHint(int x, int z) { return Math.min(maxY(), base + topF(x, z) * fh + 5); }

        int nominalTop() { return base + floors * fh; }

        boolean cov(int x, int z, int f) {
            for (Vol v : vols) if (v.has(x, z, f)) return true;
            return false;
        }

        /** 이 컬럼의 층 수(가장 높은 덩어리 기준). */
        int topF(int x, int z) {
            int t = 0;
            for (Vol v : vols) if (v.inPlan(x, z)) t = Math.max(t, v.f1);
            return t;
        }

        /** f층에서 벽이 빠진 방향 비트(0 서, 1 동, 2 북, 3 남). 0이면 내부. */
        int missing(int x, int z, int f) {
            int m = 0;
            if (!cov(x - 1, z, f)) m |= 1;
            if (!cov(x + 1, z, f)) m |= 2;
            if (!cov(x, z - 1, f)) m |= 4;
            if (!cov(x, z + 1, f)) m |= 8;
            return m;
        }

        boolean door(int x, int y, int z) {
            for (int[] d : doors) {
                if (x >= d[0] && x <= d[2] && z >= d[1] && z <= d[3] && y > d[4] && y <= d[4] + 3) return true;
            }
            return false;
        }

        int stairC(int x, int z) {
            if (stairX == Integer.MIN_VALUE) return 99;
            return Math.max(Math.abs(x - stairX), Math.abs(z - stairZ));
        }
    }

    /** 골목 위 설치물: 0 배관(축 방향 원통), 1 다리, 2 가로등. */
    static final class Fx {
        int type, axis, x0, z0, x1, z1, y, r, mat, light;

        boolean covers(int x, int z) {
            int m = type == 0 ? r + (r >= 1 ? 1 : 0) : 0;
            if (type == 0) {
                if (axis == 0) return x >= x0 && x <= x1 && Math.abs(z - z0) <= m;
                return z >= z0 && z <= z1 && Math.abs(x - x0) <= m;
            }
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        int top() { return type == 0 ? y + r + 1 : type == 1 ? y + 1 : Integer.MIN_VALUE; }
    }

    /** 부지 사이의 골목(쪼갠 틈). axis 0: 틈이 x 방향으로 벌어짐(골목은 z 방향으로 뻗음). */
    static final class Alley extends Landmarks.Piece {
        int axis, gap;
        Fx[] fx = new Fx[0];

        @Override public BlockState block(long seed, int x, int y, int z, int deckTop) { return null; }
        @Override public int maxY() { return 0; }
    }

    /** 한 컬럼에 걸친 골목 설치물만 모은 것(장식용). */
    static final class AlleyCol implements Landmarks.Struct {
        final Fx[] fx;
        final int k;
        final int max;

        AlleyCol(Fx[] fx, int k, int max) { this.fx = fx; this.k = k; this.max = max; }

        @Override
        public BlockState block(long seed, int x, int y, int z, int deckTop) {
            for (Fx f : fx) {
                BlockState s = fxBlock(seed, f, k, x, y, z, deckTop);
                if (s != null) return s;
            }
            return null;
        }

        @Override public int maxY() { return max; }
    }

    static final class Lot {
        int district, bx0, bz0, nbx, nbz;
        Building[] buildings = new Building[0];
        java.util.ArrayList<Landmarks.Piece>[] buckets;

        Landmarks.Piece find(int x, int z) {
            if (buckets == null) return null;
            int i = Math.floorDiv(x, 16) - bx0, j = Math.floorDiv(z, 16) - bz0;
            if (i < 0 || j < 0 || i >= nbx || j >= nbz) return null;
            java.util.ArrayList<Landmarks.Piece> l = buckets[i + j * nbx];
            if (l == null) return null;
            for (Landmarks.Piece pc : l) if (pc.contains(x, z)) return pc;
            return null;
        }

        @SuppressWarnings("unchecked")
        void index(java.util.List<Landmarks.Piece> pieces, int x0, int z0, int x1, int z1) {
            bx0 = Math.floorDiv(x0, 16); bz0 = Math.floorDiv(z0, 16);
            nbx = Math.floorDiv(x1, 16) - bx0 + 1; nbz = Math.floorDiv(z1, 16) - bz0 + 1;
            buckets = new java.util.ArrayList[nbx * nbz];
            for (Landmarks.Piece pc : pieces) {
                for (int i = Math.max(0, Math.floorDiv(pc.x0, 16) - bx0); i <= Math.min(nbx - 1, Math.floorDiv(pc.x1, 16) - bx0); i++) {
                    for (int j = Math.max(0, Math.floorDiv(pc.z0, 16) - bz0); j <= Math.min(nbz - 1, Math.floorDiv(pc.z1, 16) - bz0); j++) {
                        if (buckets[i + j * nbx] == null) buckets[i + j * nbx] = new java.util.ArrayList<>(4);
                        buckets[i + j * nbx].add(pc);
                    }
                }
            }
        }

        /** (x, z)에서 r 이내에 있는 건물 중 가장 가까운 거리(없으면 큰 값). */
        double nearBuilding(int x, int z, double r, double minDmg) {
            if (buckets == null) return 1e9;
            double best = 1e9;
            for (int i = Math.floorDiv((int) (x - r), 16) - bx0; i <= Math.floorDiv((int) (x + r), 16) - bx0; i++) {
                for (int j = Math.floorDiv((int) (z - r), 16) - bz0; j <= Math.floorDiv((int) (z + r), 16) - bz0; j++) {
                    if (i < 0 || j < 0 || i >= nbx || j >= nbz || buckets[i + j * nbx] == null) continue;
                    for (Landmarks.Piece pc : buckets[i + j * nbx]) {
                        if (!(pc instanceof Building) || ((Building) pc).dmg < minDmg) continue;
                        best = Math.min(best, rectDist(pc.x0, pc.z0, pc.x1, pc.z1, x + 0.5, z + 0.5));
                    }
                }
            }
            return best;
        }
    }

    private static final ThreadLocal<HashMap<Long, Lot>> LOT_CACHE = ThreadLocal.withInitial(HashMap::new);

    static Lot lot(CityLayout.Params p, long seed, int k, int ix, int jz) {
        HashMap<Long, Lot> cache = LOT_CACHE.get();
        long key = CityLayout.hash(seed ^ ((long) p.hashCode() * 0xD6E8FEB86659FD93L), ix, jz, 7002 + 31 * k);   // 섞인 키(XOR 키는 충돌함)
        Lot cached = cache.get(key);
        if (cached != null) return cached;
        Lot lot = buildLot(p, seed, k, ix, jz);
        if (cache.size() > 2048) cache.clear();
        cache.put(key, lot);
        return lot;
    }

    /** 쪼개기 규칙(층별). */
    private static final class Ctx {
        CityLayout.Params p;
        long seed;
        int k, ix, jz, minPlot, maxPlot, base, fh, ceil;
        double stop;
        boolean city, mega;
        java.util.SplittableRandom rng;
        java.util.ArrayList<int[]> plots = new java.util.ArrayList<>();
        java.util.ArrayList<Alley> alleys = new java.util.ArrayList<>();
    }

    private static Lot buildLot(CityLayout.Params p, long seed, int k, int ix, int jz) {
        Lot lot = new Lot();
        lot.district = lotDistrict(p, seed, k, ix, jz);
        boolean city = Landmarks.cityLayer(p, k);
        if ((k != 0 && !city) || isFinalLayer(p, k)) return lot;
        if (merged(p, seed, k, ix - 1, jz)) return lot;           // 서쪽 구획과 합쳐짐: 건물은 서쪽 구획이 지음
        boolean mega = merged(p, seed, k, ix, jz);
        int margin = city ? ROAD_HALF + 3 : ROAD_HALF + 1;
        int lx0 = lineX(seed, k, ix) + margin, lx1 = lineX(seed, k, ix + (mega ? 2 : 1)) - margin;
        int lz0 = lineZ(seed, k, jz) + margin, lz1 = lineZ(seed, k, jz + 1) - margin;
        if (lx1 - lx0 < 24 || lz1 - lz0 < 24) return lot;

        Ctx c = new Ctx();
        c.p = p; c.seed = seed; c.k = k; c.ix = ix; c.jz = jz; c.city = city; c.mega = mega;
        c.rng = new java.util.SplittableRandom(CityLayout.hash(seed, ix, jz, 7100 + 31 * k));
        if (city) { c.minPlot = 14; c.maxPlot = 56; c.stop = 0.3; c.fh = 5; }
        else { c.minPlot = 16; c.maxPlot = mega ? 180 : 72; c.stop = 0.3; c.fh = 6; }

        // 구획 전체가 같은 기초 높이(다리와 층이 이웃 건물끼리 맞도록)
        int maxTop = Integer.MIN_VALUE;
        for (int a = 0; a <= 8; a++) for (int b = 0; b <= 8; b++) {
            CityLayout.Layer l = CityLayout.baseLayer(p, seed, k, lx0 + (lx1 - lx0) * a / 8, lz0 + (lz1 - lz0) * b / 8);
            if (l != null) maxTop = Math.max(maxTop, l.top());
        }
        if (maxTop == Integer.MIN_VALUE) return lot;
        c.base = maxTop + 1;
        c.ceil = ceilingFor(p, seed, k, lx0, lz0, lx1, lz1);

        split(c, lx0, lz0, lx1, lz1, 0);

        java.util.ArrayList<Landmarks.Piece> pieces = new java.util.ArrayList<>();
        java.util.ArrayList<Building> list = new java.util.ArrayList<>();
        boolean warped = outskirt(p, k, (lx0 + lx1) / 2.0, (lz0 + lz1) / 2.0) > 0.01;
        int pi = 0;
        for (int[] pl : c.plots) {
            pi++;
            makePlot(c, lot, pl, pi, warped, pieces, list);
        }
        lot.buildings = list.toArray(new Building[0]);
        pieces.addAll(c.alleys);
        lot.index(pieces, lx0 - 2, lz0 - 2, lx1 + 2, lz1 + 2);
        for (Alley al : c.alleys) furnishAlley(c, lot, al);
        return lot;
    }

    private static int gapWidth(Ctx c, int depth) {
        if (depth == 0) return c.city ? 6 + c.rng.nextInt(3) : 8 + c.rng.nextInt(4);
        double r = c.rng.nextDouble();
        if (c.city) {
            if (r < 0.38) return 0;
            if (r < 0.73) return 2 + c.rng.nextInt(2);
            return 4 + c.rng.nextInt(3);
        }
        if (r < 0.25) return 0;
        if (r < 0.55) return 2 + c.rng.nextInt(2);
        if (r < 0.85) return 4 + c.rng.nextInt(3);
        return 7 + c.rng.nextInt(3);
    }

    private static void split(Ctx c, int x0, int z0, int x1, int z1, int depth) {
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        int gap = gapWidth(c, depth);
        boolean canX = w >= 2 * c.minPlot + gap, canZ = d >= 2 * c.minPlot + gap;
        boolean small = w <= c.maxPlot && d <= c.maxPlot;
        if ((!canX && !canZ) || (small && depth > 0 && c.rng.nextDouble() < c.stop) || depth > 9) {
            c.plots.add(new int[]{x0, z0, x1, z1});
            return;
        }
        boolean alongX;
        if (canX && canZ) alongX = w > d * 1.25 || (d <= w * 1.25 && c.rng.nextBoolean());
        else alongX = canX;
        int len = alongX ? w : d;
        int lo = alongX ? x0 : z0;
        double f = 0.3 + 0.4 * c.rng.nextDouble();
        int pos = lo + Math.max(c.minPlot, Math.min(len - c.minPlot - gap, (int) Math.round((len - gap) * f)));
        if (alongX) {
            split(c, x0, z0, pos - 1, z1, depth + 1);
            if (gap > 0) addAlley(c, pos, z0, pos + gap - 1, z1, 0, gap);
            split(c, pos + gap, z0, x1, z1, depth + 1);
        } else {
            split(c, x0, z0, x1, pos - 1, depth + 1);
            if (gap > 0) addAlley(c, x0, pos, x1, pos + gap - 1, 1, gap);
            split(c, x0, pos + gap, x1, z1, depth + 1);
        }
    }

    private static void addAlley(Ctx c, int x0, int z0, int x1, int z1, int axis, int gap) {
        Alley a = new Alley();
        a.x0 = x0; a.z0 = z0; a.x1 = x1; a.z1 = z1; a.axis = axis; a.gap = gap;
        c.alleys.add(a);
    }

    private static boolean blockedRect(Ctx c, int x0, int z0, int x1, int z1, boolean warped) {
        CityLayout.Params p = c.p;
        if (c.k == 0 && StartComplex.exitOverlaps(p, c.seed, x0, z0, x1, z1, 8)) return true;
        if (c.city && Landmarks.overlapsSite(p, c.seed, c.k, x0, z0, x1, z1, 10)) return true;
        if (warped) {
            for (int t = 0; t <= 2 * ((x1 - x0) + (z1 - z0)); t += 4) {
                int px, pz, ww = x1 - x0, dd = z1 - z0;
                if (t <= ww) { px = x0 + t; pz = z0; }
                else if (t <= ww + dd) { px = x1; pz = z0 + (t - ww); }
                else if (t <= 2 * ww + dd) { px = x1 - (t - ww - dd); pz = z1; }
                else { px = x0; pz = z1 - (t - 2 * ww - dd); }
                if (roadAt(p, c.seed, c.k, px, pz)) return true;
            }
        }
        int missing = 0;
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
            if (CityLayout.baseLayer(p, c.seed, c.k, x0 + (x1 - x0) * a / 2, z0 + (z1 - z0) * b / 2) == null) missing++;
        }
        return missing > 2;
    }

    private static void makePlot(Ctx c, Lot lot, int[] pl, int pi, boolean warped,
                                 java.util.List<Landmarks.Piece> pieces, java.util.List<Building> list) {
        CityLayout.Params p = c.p;
        long seed = c.seed;
        java.util.SplittableRandom r = new java.util.SplittableRandom(CityLayout.hash(seed, c.ix * 977 + pi, c.jz, 7200 + 31 * c.k));
        int sb = c.city ? 2 : 2;
        int x0 = pl[0] + r.nextInt(sb), z0 = pl[1] + r.nextInt(sb), x1 = pl[2] - r.nextInt(sb), z1 = pl[3] - r.nextInt(sb);
        int[] t = trimForPlazas(x0, z0, x1, z1, c.minPlot - 6);
        if (t == null) return;
        x0 = t[0]; z0 = t[1]; x1 = t[2]; z1 = t[3];
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        if (w < 8 || d < 8) return;
        if (blockedRect(c, x0, z0, x1, z1, warped)) return;

        int dist = district(p, seed, c.k, (x0 + x1) / 2, (z0 + z1) / 2);
        double chance;
        if (c.city) chance = dist == DENSE ? 0.95 : dist == SPARSE ? 0.8 : 0.3;
        else chance = c.mega ? 1.0 : dist == DENSE ? 0.95 : dist == SPARSE ? 0.72 : 0.25;
        if (r.nextDouble() >= chance * p.buildingDensity()) {
            if (c.city) statueGroup(c, r, x0, z0, x1, z1, pieces);
            return;
        }

        Building b = new Building();
        b.k = c.k;
        b.style = c.city ? APARTMENT : RUIN;
        b.x0 = x0; b.z0 = z0; b.x1 = x1; b.z1 = z1;
        b.id = (int) (CityLayout.hash01(seed, c.ix * 977 + pi, c.jz, 844 + c.k) * Integer.MAX_VALUE);
        b.base = c.base;
        b.fh = c.fh;
        b.openSky = openSky(p, seed, c.k, (x0 + x1) / 2, (z0 + z1) / 2);
        b.cs = 8 + r.nextInt(5);
        b.cw = b.cs >= 11 ? 3 : 2;

        int minH, maxH;
        if (c.city) {
            if (dist == DENSE) { minH = 20; maxH = 65; }
            else if (dist == SPARSE) { minH = 12; maxH = 36; }
            else { minH = 8; maxH = 18; }
        } else if (c.mega || (dist == DENSE && Math.min(w, d) >= 60 && r.nextDouble() < 0.3)) {
            minH = 110; maxH = 320;                                    // 거대한 폐허
            b.cs = 11 + r.nextInt(3); b.cw = 3;
        } else if (dist == DENSE) { minH = 30; maxH = 140; }
        else if (dist == SPARSE) { minH = 12; maxH = 55; }
        else { minH = 6; maxH = 20; }
        maxH = Math.min(maxH, p.buildingMaxHeight());
        minH = Math.min(minH, maxH);
        int h = minH + (int) ((maxH - minH) * Math.pow(r.nextDouble(), 0.7));
        int ceil = ceilingFor(p, seed, c.k, x0, z0, x1, z1);
        h = Math.min(h, ceil - b.base - 4 - b.fh * 2);
        int F = h / b.fh;
        if (F < 2) return;

        b.vols = forms(c, r, x0, z0, x1, z1, F, Math.max(1, (ceil - b.base - 4) / b.fh));
        int fl = 0;
        for (Vol v : b.vols) fl = Math.max(fl, v.f1);
        b.floors = fl;

        if (c.city) {
            b.wallKeep = 0.97;
            b.dmg = 0.02 + 0.1 * r.nextDouble() + (b.openSky ? 0.12 : 0);
            for (int s = 0; s < 4; s++) if (r.nextDouble() < 0.35) b.balconySides |= 1 << s;
            placeStair(b, r);
        } else {
            double kw = r.nextDouble(), kd = r.nextDouble();
            if (dist == DENSE) { b.wallKeep = 0.55 + 0.35 * kw; b.dmg = 0.12 + 0.33 * kd; }
            else { b.wallKeep = 0.25 + 0.35 * kw; b.dmg = 0.40 + 0.35 * kd; }
            if (b.openSky) { b.wallKeep *= 0.7; b.dmg = Math.min(0.9, b.dmg + 0.15); }
            if (r.nextDouble() < 0.12) b.dmg = 0.85;                  // 거의 다 무너진 건물
        }
        pieces.add(b);
        list.add(b);
    }

    /** 건물 모양: 상자, 계단식, 저층부 위 탑, ㄱ자, 중정형, 높이가 다른 조각들. 옥상 증축이 붙기도 한다. */
    private static Vol[] forms(Ctx c, java.util.SplittableRandom r, int x0, int z0, int x1, int z1, int F, int maxF) {
        java.util.ArrayList<Vol> vs = new java.util.ArrayList<>();
        int w = x1 - x0 + 1, d = z1 - z0 + 1, mn = Math.min(w, d);
        double q = r.nextDouble();
        int form;
        if (c.city) form = q < 0.22 ? 0 : q < 0.42 ? 5 : q < 0.6 ? 3 : q < 0.7 ? 4 : q < 0.82 ? 1 : 2;
        else form = q < 0.3 ? 0 : q < 0.5 ? 5 : q < 0.65 ? 3 : q < 0.72 ? 4 : q < 0.86 ? 1 : 2;
        if (form == 4 && mn < 26) form = 0;
        if ((form == 1 || form == 2) && mn < 20) form = 5;
        if (form == 5 && Math.max(w, d) < 22) form = 0;
        if (form == 3 && mn < 16) form = 0;
        switch (form) {
            case 1: {   // 계단식
                int s = Math.max(3, mn / 6);
                int n1 = Math.max(1, (int) (F * (0.35 + 0.2 * r.nextDouble()))), n2 = Math.max(n1 + 1, (int) (F * (0.65 + 0.15 * r.nextDouble())));
                vs.add(new Vol(x0, z0, x1, z1, 0, n1));
                vs.add(new Vol(x0 + s, z0 + s, x1 - s, z1 - s, n1, Math.min(F, n2)));
                if (mn - 4 * s >= 8 && n2 < F) vs.add(new Vol(x0 + 2 * s, z0 + 2 * s, x1 - 2 * s, z1 - 2 * s, n2, F));
                break;
            }
            case 2: {   // 저층부 위 탑
                int pf = Math.min(F - 1, 1 + r.nextInt(3));
                vs.add(new Vol(x0, z0, x1, z1, 0, pf));
                int tw = Math.max(8, (int) (w * (0.4 + 0.25 * r.nextDouble()))), td = Math.max(8, (int) (d * (0.4 + 0.25 * r.nextDouble())));
                int tx = x0 + 2 + r.nextInt(Math.max(1, w - tw - 3)), tz = z0 + 2 + r.nextInt(Math.max(1, d - td - 3));
                vs.add(new Vol(tx, tz, Math.min(x1 - 2, tx + tw - 1), Math.min(z1 - 2, tz + td - 1), pf, Math.min(maxF, F + F / 3)));
                break;
            }
            case 3: {   // ㄱ자: 긴 날개 + 짧은 날개(높이 다름)
                boolean ax = w >= d;
                int cut = (int) ((ax ? d : w) * (0.45 + 0.15 * r.nextDouble()));
                int f2 = Math.max(1, (int) (F * (0.4 + 0.5 * r.nextDouble())));
                boolean flip = r.nextBoolean(), flip2 = r.nextBoolean();
                if (ax) {
                    int zs = flip ? z0 : z1 - cut + 1;
                    vs.add(new Vol(x0, zs, x1, zs + cut - 1, 0, F));
                    int len = (int) (w * (0.35 + 0.25 * r.nextDouble()));
                    int xs = flip2 ? x0 : x1 - len + 1;
                    vs.add(flip ? new Vol(xs, z0 + cut, xs + len - 1, z1, 0, f2) : new Vol(xs, z0, xs + len - 1, z1 - cut, 0, f2));
                } else {
                    int xs = flip ? x0 : x1 - cut + 1;
                    vs.add(new Vol(xs, z0, xs + cut - 1, z1, 0, F));
                    int len = (int) (d * (0.35 + 0.25 * r.nextDouble()));
                    int zs = flip2 ? z0 : z1 - len + 1;
                    vs.add(flip ? new Vol(x0 + cut, zs, x1, zs + len - 1, 0, f2) : new Vol(x0, zs, x1 - cut, zs + len - 1, 0, f2));
                }
                break;
            }
            case 4: {   // 중정형
                Vol v = new Vol(x0, z0, x1, z1, 0, F);
                int ring = 7 + r.nextInt(Math.max(1, mn / 5));
                v.hx0 = x0 + ring; v.hz0 = z0 + ring; v.hx1 = x1 - ring; v.hz1 = z1 - ring;
                vs.add(v);
                break;
            }
            case 5: {   // 높이가 다른 조각 2~3개가 붙어 있음
                boolean ax = w >= d;
                int len = ax ? w : d;
                int n = len >= 40 ? 2 + r.nextInt(2) : 2;
                int a = ax ? x0 : z0;
                for (int i = 0; i < n; i++) {
                    int e = i == n - 1 ? (ax ? x1 : z1) : a + Math.max(8, (int) (len / (double) n * (0.7 + 0.6 * r.nextDouble()))) - 1;
                    e = Math.min(e, (ax ? x1 : z1) - (n - 1 - i) * 8);
                    int fi = Math.max(1, (int) (F * (0.35 + 0.65 * r.nextDouble())));
                    if (i == 0) fi = F;
                    vs.add(ax ? new Vol(a, z0, e, z1, 0, fi) : new Vol(x0, a, x1, e, 0, fi));
                    a = e + 1;
                    if (a > (ax ? x1 : z1)) break;
                }
                break;
            }
            default:
                vs.add(new Vol(x0, z0, x1, z1, 0, F));
        }
        // 옥상 증축(작은 덩어리 한두 층)
        if (r.nextDouble() < (c.city ? 0.45 : 0.3)) {
            Vol top = vs.get(0);
            for (Vol v : vs) if (v.f1 > top.f1) top = v;
            int vw = top.x1 - top.x0 + 1, vd = top.z1 - top.z0 + 1;
            if (vw >= 12 && vd >= 12 && top.f1 + 1 <= maxF) {
                int aw = 5 + r.nextInt(Math.min(8, vw - 9)), ad = 5 + r.nextInt(Math.min(8, vd - 9));
                int ax0 = top.x0 + 2 + r.nextInt(vw - aw - 3), az0 = top.z0 + 2 + r.nextInt(vd - ad - 3);
                vs.add(new Vol(ax0, az0, ax0 + aw - 1, az0 + ad - 1, top.f1, Math.min(maxF, top.f1 + 1 + r.nextInt(2))));
            }
        }
        return vs.toArray(new Vol[0]);
    }

    /** 계단실(2층 주거 건물): 가운데 3x3 기둥을 감아 오르는 2칸 폭 계단, 층마다 한 바퀴, 옥상까지. */
    private static void placeStair(Building b, java.util.SplittableRandom r) {
        if (b.floors < 2) return;
        for (int tries = 0; tries < 4; tries++) {
            Vol v = b.vols[r.nextInt(b.vols.length)];
            if (v.f0 != 0) continue;
            int vw = v.x1 - v.x0 + 1, vd = v.z1 - v.z0 + 1;
            if (vw < 12 || vd < 12) continue;
            int cx = r.nextBoolean() ? v.x0 + 5 : v.x1 - 5, cz = r.nextBoolean() ? v.z0 + 5 : v.z1 - 5;
            // 계단실이 지나는 모든 층에서 둘레(c<=4)가 건물 안이어야 함
            int f1 = v.f1;
            boolean ok = true;
            for (int f = 0; f < f1 && ok; f++) {
                for (int a = -4; a <= 4 && ok; a += 4) for (int c = -4; c <= 4 && ok; c += 4) {
                    if (!b.cov(cx + a, cz + c, f) || b.missing(cx + a, cz + c, f) != 0) ok = false;
                }
            }
            // 옥상 출구 위를 증축이 막지 않음
            for (Vol o : b.vols) if (o.f0 >= f1 && o.x0 <= cx + 5 && o.x1 >= cx - 5 && o.z0 <= cz + 5 && o.z1 >= cz - 5) ok = false;
            if (!ok) continue;
            b.stairX = cx; b.stairZ = cz; b.stairF = f1;
            return;
        }
    }

    /** 빈 부지(2층): 작은 석상 몇 개가 한 줄로 서 있다. */
    private static void statueGroup(Ctx c, java.util.SplittableRandom r, int x0, int z0, int x1, int z1,
                                    java.util.List<Landmarks.Piece> pieces) {
        if (r.nextDouble() > 0.55) return;
        boolean ax = (x1 - x0) >= (z1 - z0);
        int len = ax ? x1 - x0 : z1 - z0;
        int n = Math.min(1 + r.nextInt(5), Math.max(1, (len - 4) / 5));
        int sp = 4 + r.nextInt(3);
        int start = (ax ? x0 : z0) + (len - (n - 1) * sp) / 2;
        int mid = ax ? (z0 + z1) / 2 : (x0 + x1) / 2;
        int face = ax ? (r.nextBoolean() ? 0 : 1) : (r.nextBoolean() ? 2 : 3);
        int ceil = ceilingFor(c.p, c.seed, c.k, x0, z0, x1, z1);
        for (int i = 0; i < n; i++) {
            int a = start + i * sp;
            int sx = ax ? a : mid, sz = ax ? mid : a;
            if (nearGrid(sx, sz, PLAZA_CLEAR)) continue;
            CityLayout.Layer l = CityLayout.baseLayer(c.p, c.seed, c.k, sx, sz);
            if (l == null) continue;
            boolean big = r.nextDouble() < 0.18;
            int h = big ? 7 + r.nextInt(4) : 4 + r.nextInt(3);
            if (l.top() + 2 + h >= ceil) continue;
            boolean broken = r.nextDouble() < 0.08;
            int f = r.nextDouble() < 0.8 ? face : r.nextInt(4);
            pieces.add(new Landmarks.Statue(sx, sz, l.top() + 1, h, big ? 2 : 1, f, broken));
        }
    }

    // ---------------- 골목 설치물: 가로지르는 배관, 벽을 따라가는 배관, 건물 사이 다리, 가로등 ----------------
    private static void furnishAlley(Ctx c, Lot lot, Alley al) {
        java.util.SplittableRandom r = new java.util.SplittableRandom(CityLayout.hash(c.seed, al.x0 * 31 + al.z0, al.x1 * 17 + al.z1, 7300 + c.k));
        java.util.ArrayList<Fx> out = new java.util.ArrayList<>();
        boolean ax = al.axis == 0;                       // 틈이 x로 벌어짐, 골목은 z로 뻗음
        int a0 = ax ? al.z0 : al.x0, a1 = ax ? al.z1 : al.x1;   // 골목 길이 방향 범위
        int g0 = ax ? al.x0 : al.z0, g1 = ax ? al.x1 : al.z1;   // 틈 방향 범위
        int len = a1 - a0 + 1;
        // 양쪽 벽 건물(틈 바로 바깥 칸)
        // 가로지르는 배관
        int nCross = (int) (len / (c.city ? 9.0 : 14.0) * (0.5 + r.nextDouble()));
        for (int i = 0; i < nCross; i++) {
            int a = a0 + 2 + r.nextInt(Math.max(1, len - 4));
            int rad = c.city ? (r.nextDouble() < 0.75 ? 0 : 1) : (r.nextDouble() < 0.45 ? 0 : r.nextDouble() < 0.75 ? 1 : 2);
            Building A = sideBuilding(lot, ax, g0 - 1, a), B = sideBuilding(lot, ax, g1 + 1, a);
            if (A == null || B == null) continue;
            int lim = Math.min(colTopAt(c.seed, A, ax, g0 - 1, a), colTopAt(c.seed, B, ax, g1 + 1, a)) - rad - 2;
            int lo = c.base + 4 + rad;
            if (lim < lo) continue;
            Fx f = new Fx();
            f.type = 0; f.axis = ax ? 0 : 1; f.r = rad;
            f.y = lo + r.nextInt(Math.min(lim - lo + 1, c.city ? 40 : 70));
            if (ax) { f.x0 = g0; f.x1 = g1; f.z0 = f.z1 = a; } else { f.z0 = g0; f.z1 = g1; f.x0 = f.x1 = a; }
            f.mat = r.nextInt(3);
            out.add(f);
            if (c.city && r.nextDouble() < 0.4 && rad == 0) {    // 가는 배관 묶음
                Fx f2 = new Fx();
                f2.type = 0; f2.axis = f.axis; f2.r = 0; f2.y = f.y + (r.nextBoolean() ? 1 : -1); f2.mat = f.mat;
                if (ax) { f2.x0 = g0; f2.x1 = g1; f2.z0 = f2.z1 = a + 1; } else { f2.z0 = g0; f2.z1 = g1; f2.x0 = f2.x1 = a + 1; }
                if (f2.y > c.base + 3) out.add(f2);
            }
        }
        // 벽을 따라가는 배관(틈이 4칸 이상)
        if (al.gap >= 4) {
            int nPar = (c.city ? 1 : 0) + r.nextInt(3);
            for (int i = 0; i < nPar; i++) {
                boolean sideA = r.nextBoolean();
                int g = sideA ? g0 + 1 : g1 - 1, wallG = sideA ? g0 - 1 : g1 + 1;
                int s0 = a0 + r.nextInt(Math.max(1, len / 2)), s1 = Math.min(a1, s0 + 12 + r.nextInt(Math.max(1, len)));
                int rad = c.city ? 0 : (r.nextDouble() < 0.5 ? 0 : 1);
                int lim = Integer.MAX_VALUE;
                boolean ok = true;
                for (int t = 0; t <= 4 && ok; t++) {
                    int a = s0 + (s1 - s0) * t / 4;
                    Building W = sideBuilding(lot, ax, wallG, a);
                    if (W == null) { ok = false; break; }
                    lim = Math.min(lim, colTopAt(c.seed, W, ax, wallG, a) - rad - 2);
                }
                int lo = c.base + 3 + rad;
                if (!ok || lim < lo) continue;
                Fx f = new Fx();
                f.type = 0; f.axis = ax ? 1 : 0; f.r = rad; f.mat = r.nextInt(3);
                f.y = lo + r.nextInt(Math.min(lim - lo + 1, 30));
                if (ax) { f.x0 = f.x1 = g; f.z0 = s0; f.z1 = s1; } else { f.z0 = f.z1 = g; f.x0 = s0; f.x1 = s1; }
                out.add(f);
            }
        }
        // 다리: 같은 층 높이로 양쪽 건물을 이음(출입구를 뚫음)
        if (al.gap >= 2 && al.gap <= 12 && len >= 12) {
            int nBr = c.city ? (r.nextDouble() < 0.75 ? 1 + r.nextInt(2) : 0) : (r.nextDouble() < 0.2 ? 1 : 0);
            for (int i = 0; i < nBr; i++) {
                int a = a0 + 3 + r.nextInt(Math.max(1, len - 8));
                Building A = sideBuilding(lot, ax, g0 - 1, a), B = sideBuilding(lot, ax, g1 + 1, a);
                Building A2 = sideBuilding(lot, ax, g0 - 1, a + 2), B2 = sideBuilding(lot, ax, g1 + 1, a + 2);
                if (A == null || B == null || A != A2 || B != B2 || A.fh != B.fh || A.base != B.base) continue;
                int fmax = Math.min(A.floors, B.floors) - 1;
                if (fmax < 1) continue;
                int f = 1 + r.nextInt(fmax);
                int y = A.base + f * A.fh;
                boolean ok = true;
                for (int t = 0; t <= 2; t++) {
                    int pa = a + t;
                    for (int[] e : new int[][]{{g0 - 1, g0 - 2}, {g1 + 1, g1 + 2}}) {
                        Building W = e[0] == g0 - 1 ? A : B;
                        int wx = ax ? e[0] : pa, wz = ax ? pa : e[0], ix2 = ax ? e[1] : pa, iz2 = ax ? pa : e[1];
                        if (!W.cov(wx, wz, f) || !W.cov(ix2, iz2, f) || W.stairC(ix2, iz2) <= 4) ok = false;
                        if (colTopAt(c.seed, W, ax, e[0], pa) < y + 5) ok = false;
                    }
                }
                if (!ok) continue;
                Fx br = new Fx();
                br.type = 1; br.y = y;
                if (ax) { br.x0 = g0; br.x1 = g1; br.z0 = a; br.z1 = a + 2; } else { br.z0 = g0; br.z1 = g1; br.x0 = a; br.x1 = a + 2; }
                br.axis = ax ? 0 : 1;
                out.add(br);
                int[] dA = ax ? new int[]{g0 - 1, a, g0 - 1, a + 2, y} : new int[]{a, g0 - 1, a + 2, g0 - 1, y};
                int[] dB = ax ? new int[]{g1 + 1, a, g1 + 1, a + 2, y} : new int[]{a, g1 + 1, a + 2, g1 + 1, y};
                A.doors = append(A.doors, dA);
                B.doors = append(B.doors, dB);
            }
        }
        // 가로등(넓은 골목 가운데 줄)
        if (al.gap >= 5) {
            int mid = (g0 + g1) / 2;
            int step = c.city ? 10 : 14;
            int off = r.nextInt(step);
            for (int a = a0 + 3 + off; a <= a1 - 3; a += step) {
                Fx f = new Fx();
                f.type = 2;
                if (ax) { f.x0 = f.x1 = mid; f.z0 = f.z1 = a; } else { f.z0 = f.z1 = mid; f.x0 = f.x1 = a; }
                double q = r.nextDouble();
                f.light = c.city ? (q < 0.9 ? L_ON : L_OFF) : (q < 0.25 ? L_STUB : q < 0.85 ? L_OFF : L_NONE);
                f.r = 1 + r.nextInt(3);
                if (f.light != L_NONE) out.add(f);
            }
        }
        al.fx = out.toArray(new Fx[0]);
    }

    private static int[][] append(int[][] a, int[] e) {
        int[][] n = java.util.Arrays.copyOf(a, a.length + 1);
        n[a.length] = e;
        return n;
    }

    private static Building sideBuilding(Lot lot, boolean ax, int g, int a) {
        Landmarks.Piece pc = ax ? lot.find(g, a) : lot.find(a, g);
        return pc instanceof Building ? (Building) pc : null;
    }

    private static int colTopAt(long seed, Building b, boolean ax, int g, int a) {
        int x = ax ? g : a, z = ax ? a : g;
        if (!b.cov(x, z, 0)) return b.base;
        return colTop(seed, b, x, z);
    }

    private static final BlockState PIPE_A = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState PIPE_B = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PIPE_C = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PIPE_RUST = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final BlockState FLANGE = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState BRIDGE = Blocks.SMOOTH_STONE.defaultBlockState();

    static BlockState fxBlock(long seed, Fx f, int k, int x, int y, int z, int deckTop) {
        switch (f.type) {
            case 0: {
                int along = f.axis == 0 ? x : z;
                int lo = f.axis == 0 ? f.x0 : f.z0, hi = f.axis == 0 ? f.x1 : f.z1;
                if (along < lo || along > hi) return null;
                int dl = f.axis == 0 ? z - f.z0 : x - f.x0, dy = y - f.y;
                int d2 = dl * dl + dy * dy;
                boolean flange = f.r >= 1 && Math.floorMod(along, 7) == 3;
                double rr = f.r * f.r + 0.8 * f.r, rf = (f.r + 1) * (f.r + 1) + 0.8 * (f.r + 1);
                if (d2 <= rr) {
                    if (k == 0 && CityLayout.hash01(seed, x * 7 + y, z, 7310) < 0.15) return PIPE_RUST;
                    return f.mat == 0 ? PIPE_A : f.mat == 1 ? PIPE_B : PIPE_C;
                }
                if (flange && d2 <= rf) return FLANGE;
                return null;
            }
            case 1: {
                if (!f.covers(x, z)) return null;
                if (y == f.y) {
                    if (k == 0 && CityLayout.noise(seed, x, z, 3.0, 7311) > 0.75) return null;   // 1층: 깨진 다리
                    return BRIDGE;
                }
                if (y == f.y + 1) {
                    boolean edge = f.axis == 0 ? (z == f.z0 || z == f.z1) : (x == f.x0 || x == f.x1);
                    return edge ? LAMP_CAP : null;
                }
                return null;
            }
            default: {
                if (x != f.x0 || z != f.z0 || y <= deckTop) return null;
                int rel = y - deckTop;
                if (f.light == L_STUB) return rel <= f.r ? POLE : null;
                if (rel <= POLE_H) return POLE;
                if (rel == POLE_H + 1) return f.light == L_ON ? LAMP_ON : LAMP_OFF;
                if (rel == POLE_H + 2) return LAMP_CAP;
                return null;
            }
        }
    }

    /** (x, z)를 덮는 건물/석상/골목 조각. */
    static Landmarks.Piece pieceAt(CityLayout.Params p, long seed, int k, int x, int z) {
        if ((k != 0 && !Landmarks.cityLayer(p, k)) || isFinalLayer(p, k)) return null;
        int[] u = warp(p, seed, k, x, z);
        int ix = cellX(seed, k, u[0]), jz = cellZ(seed, k, u[1]);
        Landmarks.Piece pc = lot(p, seed, k, ix, jz).find(x, z);
        if (pc != null) return pc;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                pc = lot(p, seed, k, ix + a, jz + b).find(x, z);
                if (pc != null) return pc;
            }
        }
        return null;
    }

    /** (x, z)를 덮는 건물(없으면 null). */
    static Building buildingAt(CityLayout.Params p, long seed, int k, int x, int z) {
        Landmarks.Piece pc = pieceAt(p, seed, k, x, z);
        return pc instanceof Building ? (Building) pc : null;
    }

    // ---------------- 공통 ----------------
    /** 탑 광장에서 비켜 나도록 사각형을 잘라 냄. 못 쓰면 null. */
    private static int[] trimForPlazas(int sx0, int sz0, int sx1, int sz1, int minSize) {
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
                if (ox1 - ox0 < minSize || oz1 - oz0 < minSize) continue;
                long ar = (long) (ox1 - ox0) * (oz1 - oz0);
                if (ar > bestArea) { bestArea = ar; best = new int[]{ox0, oz0, ox1, oz1}; }
            }
            if (best == null) return null;
            sx0 = best[0]; sz0 = best[1]; sx1 = best[2]; sz1 = best[3];
        }
        return new int[]{sx0, sz0, sx1, sz1};
    }

    private static int ceilingFor(CityLayout.Params p, long seed, int k, int x0, int z0, int x1, int z1) {
        if (k + 1 >= p.layers()) return CityLayout.MIN_Y + CityLayout.HEIGHT - 8;
        for (int a : new int[]{x0, (x0 + x1) / 2, x1}) for (int b : new int[]{z0, (z0 + z1) / 2, z1}) {
            if (CityLayout.insideRim(p, seed, k + 1, a, b)) return CityLayout.layerBottom(k + 1) - CEILING_MARGIN;
        }
        return CityLayout.MIN_Y + CityLayout.HEIGHT - 8;
    }

    // ---------------- 2층 주거 건물 ----------------
    private static final BlockState RAIL = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();

    /** 계단 디딤판: 계단실 안 (x, z)의 y에 놓일 블록(없으면 null). */
    private static BlockState stairTread(Building b, int x, int y, int z, int c) {
        double s = Math.atan2(z - b.stairZ, -(x - b.stairX)) / (2 * Math.PI);
        if (s < 0) s += 1;
        for (int n = 0; n < b.stairF; n++) {
            double S = b.base + 1 + (n + s) * b.fh;
            double q = Math.floor(S * 2) / 2;
            int full = (int) Math.floor(q);
            if (q - full < 0.25) { if (y == full - 1) return A_FRAME; }
            else if (y == full) return STEP_SLAB;
        }
        return null;
    }

    /** 2층 주거 건물: 외벽이 거의 온전하고, 창이 규칙적이고, 불 켜진 창과 들어간 발코니가 있다. 계단실로 옥상까지 오를 수 있다. */
    static BlockState apartmentBlock(long seed, Building b, int x, int y, int z, int deckTop) {
        if (y <= deckTop) return null;
        if (y < b.base) return b.cov(x, z, 0) ? B_FOUND : null;
        int rel = y - b.base, f = rel / b.fh, ry = rel % b.fh;
        int sc = b.stairC(x, z);
        if (sc <= 3 && f < b.stairF + (ry == 0 ? 1 : 0) && b.cov(x, z, Math.min(f, b.stairF - 1))) {
            if (sc <= 1) return f < b.stairF ? A_FRAME : null;          // 가운데 기둥
            if (f == 0 && ry == 0) return B_SLAB;
            return stairTread(b, x, y, z, sc);
        }
        int tf = b.topF(x, z);
        if (tf == 0) return null;
        // 지붕은 대체로 온전하고, 군데군데만 몇 층이 무너져 내림(계단실 둘레는 온전)
        int top = colTop(seed, b, x, z);
        boolean cf = b.cov(x, z, f), cb = f > 0 && b.cov(x, z, f - 1);
        if (!cf && cb && ry == 1 && b.missing(x, z, f - 1) != 0 && y <= top + 1) return A_FRAME;   // 옥상 난간
        if (y > top) return null;
        if (ry == 0) {
            if (!cf && !cb) return null;
            if (f > 0 && sc > 5 && CityLayout.noise(seed, x + f * 131, z, 9.0, 970) > 0.92 - 0.3 * b.dmg) return null;
            return B_SLAB;
        }
        if (!cf) return null;
        if (b.door(x, y, z)) return null;
        int miss = b.missing(x, z, f);
        int dir = -1;
        boolean loggiaInner = false;
        if (miss != 0) {
            dir = (miss & 1) != 0 ? 0 : (miss & 2) != 0 ? 1 : (miss & 4) != 0 ? 2 : 3;
        } else if (f >= 1 && b.balconySides != 0) {
            // 들어간 발코니의 안쪽 벽: 바로 바깥 칸이 발코니 쪽 외벽이면 여기가 실제 벽
            int[][] dd = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int s = 0; s < 4 && dir < 0; s++) {
                if ((b.balconySides & (1 << s)) == 0) continue;
                int nx = x + dd[s][0], nz = z + dd[s][1];
                if (b.cov(nx, nz, f) && !b.cov(nx + dd[s][0], nz + dd[s][1], f) && b.missing(nx, nz, f) == (1 << s)) {
                    int along = s <= 1 ? z : x;
                    if (Math.floorMod(along, 8) != 0) { dir = s; loggiaInner = true; }
                }
            }
            if (dir < 0) return null;
        } else {
            return null;                                                  // 내부는 비어 있음
        }
        boolean corner = Integer.bitCount(miss) >= 2 && (miss & 3) != 0 && (miss & 12) != 0;
        if (corner) return A_FRAME;
        int along = dir <= 1 ? z : x;
        // 들어간 발코니(바깥 벽 칸): 난간만 있고 위는 트임
        if (!loggiaInner && f >= 1 && (b.balconySides & (1 << dir)) != 0 && Integer.bitCount(miss) == 1) {
            if (Math.floorMod(along, 8) == 0) return A_FRAME;
            return ry == 1 ? RAIL : null;
        }
        if (Math.floorMod(along, 4) == 0) return A_FRAME;                 // 창틀 기둥
        boolean entrance = f == 0 && Math.floorMod(along, 24) >= 9 && Math.floorMod(along, 24) <= 13
                && CityLayout.hash01(seed, b.id + dir, Math.floorDiv(along, 24), 973) < 0.7;
        if (entrance && ry <= 3) return null;                             // 1층 출입구
        if (f == 0 && ry == 4 && Math.floorMod(along, 12) == 2) return LAMP_ON;   // 벽등
        if (ry >= 2 && ry <= 3) {
            double r = CityLayout.hash01(seed, x * 3 + f, z, 971);
            if (r < 0.07) return WINDOW_LIT;
            if (r < 0.12 + 0.4 * b.dmg) return null;                      // 깨진 창
            return WINDOW;
        }
        return A_WALL;
    }

    /** 무너진 정도를 반영한 이 컬럼의 건물 꼭대기. */
    static int colTop(long seed, Building b, int x, int z) {
        int nominal = b.base + b.topF(x, z) * b.fh;
        if (b.style == APARTMENT) {
            if (b.stairC(x, z) <= 4) return nominal;
            int hgt = nominal - b.base;
            double n = CityLayout.noise(seed, x, z, 18.0, 972 + (b.id & 255));
            return nominal - (int) Math.round(hgt * Math.min(1.0, b.dmg * 6) * Math.max(0, n - 0.6) * 2.5);
        }
        int hgt = nominal - b.base;
        double cell = Math.max(12.0, Math.min(b.x1 - b.x0, b.z1 - b.z0) * 0.6);
        double n = CityLayout.noise(seed, x, z, cell, 860 + (b.id & 1023));
        double jag = CityLayout.noise(seed, x, z, 4.0, 861) - 0.5;
        double c = hgt * b.dmg * (0.25 + 1.3 * n) + jag * 3.0;
        int top = nominal - (int) Math.max(0, Math.round(c));
        return Math.max(b.base + 1, Math.min(nominal, top));
    }

    private static boolean gridCol(Building b, int x, int z) {
        return Math.floorMod(x - b.x0, b.cs) < b.cw && Math.floorMod(z - b.z0, b.cs) < b.cw;
    }

    /** 1층 폐허 건물: 콘크리트 골조(기둥, 바닥판), 벗겨진 외벽, 무너진 꼭대기. */
    static BlockState buildingBlock(long seed, Building b, int x, int y, int z, int deckTop) {
        if (y <= deckTop) return null;
        if (y < b.base) return b.cov(x, z, 0) ? B_FOUND : null;
        int tf = b.topF(x, z);
        if (tf == 0) return null;
        int rel = y - b.base, f = rel / b.fh, ry = rel % b.fh;
        int top = colTop(seed, b, x, z);
        int nominal = b.base + tf * b.fh;
        boolean grid = gridCol(b, x, z);
        if (y > top) {
            if (grid && top < nominal - b.fh && b.cov(x, z, Math.max(0, (top - b.base) / b.fh))
                    && y <= top + 1 + CityLayout.rand(seed, x >> 1, z >> 1, 862, 4)
                    && CityLayout.hash01(seed, x >> 1, z >> 1, 863) < 0.07) return B_REBAR;   // 부러진 기둥에서 솟은 철근
            return null;
        }
        boolean cf = b.cov(x, z, f), cb = f > 0 && b.cov(x, z, f - 1);
        if (ry == 0) {
            if (!cf && !cb) return null;
            if (f > 0 && CityLayout.noise(seed, x + f * 131, z, 9.0, 864) > 0.66 + 0.25 * (1 - b.dmg)) return null;   // 무너진 바닥
            return B_SLAB;
        }
        if (!cf) return null;
        if (grid) return B_COLUMN;
        int miss = b.missing(x, z, f);
        if (miss == 0) return null;                                       // 내부는 비어 있음
        boolean corner = (miss & 3) != 0 && (miss & 12) != 0;
        if (corner) return B_COLUMN;
        int dir = (miss & 1) != 0 ? 0 : (miss & 2) != 0 ? 1 : (miss & 4) != 0 ? 2 : 3;
        int along = dir <= 1 ? z - b.z0 : x - b.x0;
        int bay = Math.floorMod(along, b.cs);
        if (bay < b.cw) return B_COLUMN;
        if (b.door(x, y, z)) return null;
        if (CityLayout.hash01(seed, b.id + dir * 7919 + (along / b.cs) * 31, f, 865) >= b.wallKeep) return null;   // 벗겨진 외벽
        if (f == 0 && ry >= 1 && CityLayout.hash01(seed, b.id + dir, along / b.cs, 866) < 0.35) return null;   // 입구
        if (f == 0 && ry == 4 && bay == b.cw + 1 && CityLayout.hash01(seed, b.id + dir * 3, along / b.cs, 868) < 0.6) {
            return CityLayout.hash01(seed, x, z, 869) < 0.3 ? null : LAMP_OFF;   // 꺼진 벽등
        }
        if (ry >= 2 && ry <= b.fh - 2 && bay >= b.cw + 1 && bay <= b.cs - 2) return null;   // 창
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
            boolean exit = k == 0 && StartComplex.exitZone(p, seed, x, z, 3);
            Landmarks.Site site = Landmarks.siteAt(p, seed, k, x, z);
            Lot here = lot(p, seed, k, ix, jz);
            if (site != null) dc.st = site;
            else if (!exit) {
                Landmarks.Piece pc = pieceAt(p, seed, k, x, z);
                if (pc instanceof Alley && plaza) {
                    // 탑 광장 안에는 골목 설치물을 두지 않음
                } else if (pc instanceof Alley) {
                    Alley al = (Alley) pc;
                    int cnt = 0, mx = 0;
                    for (Fx f : al.fx) if (f.covers(x, z)) cnt++;
                    if (cnt > 0) {
                        Fx[] fs = new Fx[cnt];
                        int i = 0;
                        for (Fx f : al.fx) if (f.covers(x, z)) {
                            fs[i++] = f;
                            mx = Math.max(mx, f.type == 2 ? layer.top() + POLE_H + 2 : f.top());
                        }
                        dc.st = new AlleyCol(fs, k, mx);
                    }
                } else {
                    dc.st = pc;
                }
            }
            if (exit) ns = ew = false;

            if (dc.st != null) {
                any = true;                                     // 구조물이 도로보다 우선(휘어진 도로 가장자리, 기지/사원 안)
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
                if (!plaza && k == 0 && !exit) {
                    double e = here.nearBuilding(x, z, 6, 0.3);
                    if (e < 6) {
                        int h = (int) Math.round(3 * (1 - e / 6) * (CityLayout.noise(seed, x, z, 3.0, 892) - 0.25) * 1.6);
                        if (h > dc.pile) { dc.pile = h; any = true; }
                    }
                }
            }

            // 윗면만 파인 자국과 돌무더기(광장, 건물 안, 잔해 비탈 제외)
            if (!plaza && !onTalus && !exit && dc.st == null && dc.light == L_NONE) {
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
        if (snow > 0 && dc.light == L_NONE && !(layer.slab() && dc.st == null && dc.pile == 0)) {   // 반 블록 위에는 눈이 버티지 못함
            int topSolid = dc.top + dc.pile;
            if (dc.st != null) {
                topSolid = Math.max(topSolid, structTopSolid(seed, dc.st, x, z, layer.top()));
                if (structSnowBlocked(seed, dc.st, x, topSolid, z, layer.top())) topSolid = Integer.MIN_VALUE;
            }
            dc.snowY = topSolid == Integer.MIN_VALUE ? -1 : topSolid + 1;
            if (snow == 2 && dc.st == null && dc.pile == 0 && dc.surface != S_CRATER
                    && CityLayout.noise(seed, x, z, 24.0, 893) > 0.62) {
                dc.surface = S_SNOWBANK;
            }
            any = any || dc.snowY >= 0;
        }

        if (!any) return null;
        int mx = dc.top + dc.pile;
        if (dc.st != null) mx = Math.max(mx, dc.st.maxY());
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
        boolean first = onNs ? westOfLine : northOfLine;
        if (first && Math.floorMod(along - off, LIGHT_STEP) != 0) return L_NONE;
        if ((onNs ? dz : dx) <= ROAD_HALF + 3) return L_NONE;                  // 교차로 근처 제외
        if (!(onNs ? westOfLine : northOfLine)) {
            if (Math.floorMod(along - off - LIGHT_STEP / 2, LIGHT_STEP) != 0) return L_NONE;   // 반대편은 반 칸 어긋나게
        }
        double r = CityLayout.hash01(seed, x, z, 896 + k);
        if (k == 0) {
            if (r < 0.15) return L_NONE;
            return r < 0.35 ? L_STUB : L_OFF;
        }
        if (r < 0.04) return L_NONE;
        return r < 0.92 ? L_ON : L_OFF;
    }

    private static int structTopSolid(long seed, Landmarks.Struct st, int x, int z, int deckTop) {
        for (int y = st.topHint(x, z); y > deckTop; y--) {
            if (st.block(seed, x, y, z, deckTop) != null) return y;
        }
        return deckTop;
    }

    /** 맨 위 블록이 반 블록, 유리, 빛 블록이면 눈을 얹지 않는다(버티지 못하거나 어색함). */
    private static boolean structSnowBlocked(long seed, Landmarks.Struct st, int x, int y, int z, int deckTop) {
        if (y <= deckTop) return false;
        BlockState b = st.block(seed, x, y, z, deckTop);
        return b == null || b == STEP_SLAB || b == WINDOW || b == WINDOW_LIT || b == RUNWAY_LIGHT || b == LAMP_CAP || b == RAIL
                || b == Landmarks.PLANE_BODY || b == Landmarks.PLANE_WING || b == Landmarks.GLASS || b == LAMP_ON || b == LAMP_OFF || Landmarks.noSnow(b);
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
        if (dc.st != null) {
            BlockState s = dc.st.block(seed, x, y, z, dc.top);
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
