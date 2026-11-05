package com.example.tiercity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;

/**
 * 2층(아직 불이 들어오는 도시)의 구조물: 눈과 입이 새겨진 기둥 석상, 사원, 공군 기지.
 * 모두 (설정, 시드, 좌표)만으로 결과가 정해지는 순수 함수다.
 *
 * 사원과 공군 기지는 탑 격자 칸(768)의 한가운데에 놓인다. 네 탑의 광장에서 가장 먼 자리라 서로 겹치지 않는다.
 */
final class Landmarks {
    private Landmarks() {}

    /** 판 위에 서는 구조물 하나. */
    interface Struct {
        /** deckTop(판 윗면)보다 위의 블록. null이면 이 구조물이 두는 블록 없음. */
        BlockState block(long seed, int x, int y, int z, int deckTop);

        /** 이 구조물이 차지할 수 있는 가장 높은 y. */
        int maxY();

        /** (x, z) 컬럼에서 이 구조물의 맨 위 블록을 찾기 시작할 y(맨 위 블록 이상). */
        default int topHint(int x, int z) { return maxY(); }
    }

    // ---------------- 블록 ----------------
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState BRICK_CRACK = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState TOP = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState STEP = Blocks.STONE_BRICK_SLAB.defaultBlockState();
    private static final BlockState APRON = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState JOINT = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState RUNWAY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState MARK = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    static final BlockState RUNWAY_LIGHT = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    private static final BlockState HANGAR = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState HANGAR_FRAME = Blocks.POLISHED_ANDESITE.defaultBlockState();
    static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    static final BlockState PLANE_BODY = Blocks.GRAY_CONCRETE.defaultBlockState();
    static final BlockState PLANE_WING = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState PLANE_DARK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState PLINTH = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState EYE_DARK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState KOI_FALLBACK = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    /** 가짜 잉어 블록(물에 잠긴 상태) [색 0 주황, 1 흰색, 2 홍백][방향]. 생성기가 넣어 줌. */
    static volatile BlockState[][] koiStates = null;
    /** 물에 잠긴 쇠창살(금속 수초). 생성기가 넣어 줌. */
    static volatile BlockState metalPlant = null;
    private static final BlockState POND_LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState FLOOR_LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState SCONCE = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState POST = Blocks.ANDESITE_WALL.defaultBlockState();
    private static final BlockState POST_LAMP = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    private static final BlockState POST_CAP = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();

    /** 판 위에 놓이는 사각형 구조물(건물, 석상, 골목). 겹치지 않게 배치된다. */
    abstract static class Piece implements Struct {
        int x0, z0, x1, z1;

        boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    // ---------------- 석상 블록(모드 블록, 생성기가 넣어 줌; 비어 있으면 시험용 대체 블록) ----------------
    /** 방향 번호: 0 +z(남), 1 -z(북), 2 +x(동), 3 -x(서). 얼굴이 바라보는 쪽. */
    static volatile BlockState statueStone = null;
    static volatile BlockState[] statueFace = null, statueEyeL = null, statueEyeR = null,
            statueMouthL = null, statueMouthR = null, statueHead = null;

    private static BlockState sStone() {
        BlockState s = statueStone;
        return s != null ? s : Blocks.CALCITE.defaultBlockState();
    }

    private static BlockState pick(BlockState[] arr, int dir, BlockState fallback) {
        return arr != null ? arr[dir] : fallback;
    }

    /** 눈을 얹지 않는 석상 블록(둥근 머리 위의 눈은 어색함). */
    static boolean noSnow(BlockState b) {
        for (BlockState[] arr : new BlockState[][]{statueFace, statueEyeL, statueEyeR, statueHead}) {
            if (arr == null) continue;
            for (BlockState s : arr) if (s == b) return true;
        }
        return false;
    }

    static int dirX(int dir) { return dir == 2 ? 1 : dir == 3 ? -1 : 0; }
    static int dirZ(int dir) { return dir == 0 ? 1 : dir == 1 ? -1 : 0; }

    static int dirOf(int vx, int vz) {
        if (vz > 0) return 0;
        if (vz < 0) return 1;
        return vx > 0 ? 2 : 3;
    }

    // ---------------- 석상 ----------------
    /**
     * 가늘고 긴 하얀 기둥 석상(둥근 머리, 위쪽에 작은 검은 눈).
     * size 1: 1x1 기둥, 맨 위가 얼굴 블록 하나. size 2: 2x2 기둥, 맨 위 줄 앞면이 왼눈/오른눈, 그 아래 줄 앞면이 입 왼쪽/오른쪽.
     * (sx, sz)가 기둥 북서쪽 칸, base는 받침 높이(받침 한 칸 위부터 기둥). face는 얼굴 방향.
     */
    static final class Statue extends Piece {
        final int sx, sz, base, h, size, face;
        final boolean broken;

        Statue(int sx, int sz, int base, int h, int size, int face, boolean broken) {
            this.sx = sx; this.sz = sz; this.base = base; this.h = h; this.size = size; this.face = face; this.broken = broken;
            x0 = sx - 1; z0 = sz - 1; x1 = sx + size; z1 = sz + size;
        }

        @Override
        public int maxY() { return base + h; }

        @Override
        public BlockState block(long seed, int x, int y, int z, int deckTop) {
            if (!contains(x, z) || y <= deckTop) return null;
            if (y <= base) return PLINTH;                                   // 받침(기초 포함)
            int ox = x - sx, oz = z - sz;
            if (ox < 0 || oz < 0 || ox >= size || oz >= size) return null;
            int cr = y - base - 1;                                          // 0..h-1
            if (broken) {
                int top = (int) (h * 0.4) + CityLayout.rand(seed, sx, sz, 901, Math.max(1, h / 3)) + CityLayout.rand(seed, x, z, 902, 2);
                return cr <= Math.min(top, h - 3) ? sStone() : null;
            }
            if (cr >= h) return null;
            if (size == 1) {
                return cr == h - 1 ? pick(statueFace, face, Blocks.WHITE_CONCRETE.defaultBlockState()) : sStone();
            }
            int fx = dirX(face), fz = dirZ(face);
            int lx = -fz, lz = fx;                                          // 바라보는 사람 기준 왼쪽
            boolean front = fx != 0 ? (fx > 0 ? ox == 1 : ox == 0) : (fz > 0 ? oz == 1 : oz == 0);
            boolean left = lx != 0 ? (lx > 0 ? ox == 1 : ox == 0) : (lz > 0 ? oz == 1 : oz == 0);
            if (cr == h - 1) {
                if (!front) return pick(statueHead, face, sStone());
                return left ? pick(statueEyeL, face, EYE_DARK) : pick(statueEyeR, face, EYE_DARK);
            }
            if (cr == h - 2 && front) {
                return left ? pick(statueMouthL, face, Blocks.WHITE_CONCRETE.defaultBlockState())
                        : pick(statueMouthR, face, Blocks.WHITE_CONCRETE.defaultBlockState());
            }
            return sStone();
        }
    }

    // ---------------- 사원, 공군 기지 ----------------
    static final int TEMPLE = 1, AIRBASE = 2;
    static final int CELL = CityLayout.PILLAR_SPACING;   // 탑 격자 칸

    static final class Site implements Struct {
        int type, cx, cz, rot, base, hu, hv, ceil, id, frontY;

        /** 월드 좌표 → 지역 좌표 {u, v}(v+가 정면). */
        int[] local(int x, int z) {
            int dx = x - cx, dz = z - cz;
            switch (rot) {
                case 0: return new int[]{dx, dz};
                case 1: return new int[]{-dz, dx};
                case 2: return new int[]{-dx, -dz};
                default: return new int[]{dz, -dx};
            }
        }

        /** 지역 좌표 → 월드 좌표. */
        int[] world(int u, int v) {
            switch (rot) {
                case 0: return new int[]{cx + u, cz + v};
                case 1: return new int[]{cx + v, cz - u};
                case 2: return new int[]{cx - u, cz - v};
                default: return new int[]{cx - v, cz + u};
            }
        }

        boolean contains(int x, int z) {
            int[] uv = local(x, z);
            return Math.abs(uv[0]) <= hu && Math.abs(uv[1]) <= hv;
        }

        /** 지역 좌표의 반폭을 월드 x/z 반폭으로. */
        int halfX() { return (rot % 2 == 0) ? hu : hv; }
        int halfZ() { return (rot % 2 == 0) ? hv : hu; }

        @Override
        public int maxY() { return Math.min(ceil, base + (type == TEMPLE ? 80 : 48)); }

        @Override
        public BlockState block(long seed, int x, int y, int z, int deckTop) {
            if (y <= deckTop) return null;
            int[] uv = local(x, z);
            BlockState s = type == TEMPLE ? temple(seed, this, uv[0], uv[1], x, y, z, deckTop)
                    : airbase(seed, this, uv[0], uv[1], x, y, z, deckTop);
            if (s != null && y > ceil) return null;
            return s;
        }
    }

    private static final ThreadLocal<HashMap<Long, Site>> SITE_CACHE = ThreadLocal.withInitial(HashMap::new);
    private static final Site NO_SITE = new Site();

    /** 2층 테마가 적용되는 층인지(3층 이상 구성에서 아래에서 두 번째 층). */
    static boolean cityLayer(CityLayout.Params p, int k) {
        return k == 1 && p.layers() >= 3;
    }

    /** (x, z)를 덮는 사원/공군 기지(없으면 null). */
    static Site siteAt(CityLayout.Params p, long seed, int k, int x, int z) {
        if (!cityLayer(p, k)) return null;
        Site s = site(p, seed, k, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
        return (s != null && s.contains(x, z)) ? s : null;
    }

    static Site site(CityLayout.Params p, long seed, int k, int i, int j) {
        if (!cityLayer(p, k)) return null;
        HashMap<Long, Site> cache = SITE_CACHE.get();
        long key = CityLayout.hash(seed ^ ((long) p.hashCode() * 0xD6E8FEB86659FD93L), i, j, 7003 + 31 * k);   // 섞인 키(XOR 키는 충돌함)
        Site c = cache.get(key);
        if (c == null) {
            c = buildSite(p, seed, k, i, j);
            if (cache.size() > 2048) cache.clear();
            cache.put(key, c == null ? NO_SITE : c);
        }
        return c == NO_SITE ? null : c;
    }

    private static Site buildSite(CityLayout.Params p, long seed, int k, int i, int j) {
        double r = CityLayout.hash01(seed, i, j, 910 + k);
        int cx = i * CELL + CELL / 2 + (int) Math.round((CityLayout.hash01(seed, i, j, 911) - 0.5) * 32);
        int cz = j * CELL + CELL / 2 + (int) Math.round((CityLayout.hash01(seed, i, j, 912) - 0.5) * 32);
        double out = DistrictLayout.outskirt(p, k, cx, cz);
        Site s = new Site();
        if (r < 0.18 && out < 0.7) {
            s.type = TEMPLE; s.hu = 112; s.hv = 112;
        } else if (r >= 0.18 && r < 0.32 && out >= 0.3) {
            s.type = AIRBASE; s.hu = 240; s.hv = 100;
        } else {
            return null;
        }
        s.cx = cx; s.cz = cz;
        s.rot = CityLayout.rand(seed, i, j, 913, 4);
        s.id = (int) (CityLayout.hash01(seed, i, j, 914) * Integer.MAX_VALUE);

        // 판 안쪽에 완전히 들어오는지(가장자리에서 여유 30)
        int hx = s.halfX() + 30, hz = s.halfZ() + 30;
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                if (Math.abs(a) != 2 && Math.abs(b) != 2) continue;
                if (!CityLayout.insideRim(p, seed, k, cx + hx * a / 2, cz + hz * b / 2)) return null;
            }
        }
        // 기초 높이와 천장
        int maxTop = Integer.MIN_VALUE;
        boolean covered = false;
        for (int a = 0; a <= 8; a++) {
            for (int b = 0; b <= 8; b++) {
                int px = cx - s.halfX() + 2 * s.halfX() * a / 8, pz = cz - s.halfZ() + 2 * s.halfZ() * b / 8;
                CityLayout.Layer l = CityLayout.baseLayer(p, seed, k, px, pz);
                if (l != null) maxTop = Math.max(maxTop, l.maxY());
                if (k + 1 < p.layers() && CityLayout.insideRim(p, seed, k + 1, px, pz)) covered = true;
            }
        }
        if (maxTop == Integer.MIN_VALUE) return null;
        s.base = maxTop + 1;
        int[] fw = s.world(0, s.hv);
        CityLayout.Layer fl = CityLayout.baseLayer(p, seed, k, fw[0], fw[1]);
        s.frontY = fl != null ? fl.maxY() : s.base - 1;
        s.ceil = covered ? CityLayout.layerBottom(k + 1) - 12 : CityLayout.MIN_Y + CityLayout.HEIGHT - 1;
        return s;
    }

    /** 사원/공군 기지가 이 사각형과 겹치는지(구획에 건물을 지을 때 피하기 위함). */
    static boolean overlapsSite(CityLayout.Params p, long seed, int k, int x0, int z0, int x1, int z1, int margin) {
        if (!cityLayer(p, k)) return false;
        for (int i = Math.floorDiv(x0, CELL); i <= Math.floorDiv(x1, CELL); i++) {
            for (int j = Math.floorDiv(z0, CELL); j <= Math.floorDiv(z1, CELL); j++) {
                Site s = site(p, seed, k, i, j);
                if (s == null) continue;
                if (x1 >= s.cx - s.halfX() - margin && x0 <= s.cx + s.halfX() + margin
                        && z1 >= s.cz - s.halfZ() - margin && z0 <= s.cz + s.halfZ() + margin) return true;
            }
        }
        return false;
    }

    // ---------------- 사원 ----------------
    /** 지역 방향(0 v+, 1 v-, 2 u+, 3 u-)을 월드 방향 번호로. */
    private static int worldDir(int rot, int localDir) {
        int vx, vz;
        boolean alongV = localDir <= 1;
        int sgn = (localDir == 0 || localDir == 2) ? 1 : -1;
        if (alongV) {
            switch (rot) { case 0: vx = 0; vz = 1; break; case 1: vx = 1; vz = 0; break; case 2: vx = 0; vz = -1; break; default: vx = -1; vz = 0; }
        } else {
            switch (rot) { case 0: vx = 1; vz = 0; break; case 1: vx = 0; vz = -1; break; case 2: vx = -1; vz = 0; break; default: vx = 0; vz = 1; }
        }
        return dirOf(vx * sgn, vz * sgn);
    }

    /** 지역 좌표 (su, sv)에 선 석상의 블록. */
    private static BlockState localStatue(long seed, Site s, int su, int sv, int size, int h, int localDir, boolean broken,
                                          int base, int x, int y, int z, int deckTop) {
        int[] w = s.world(su, sv);
        return new Statue(w[0], w[1], base, h, size, worldDir(s.rot, localDir), broken).block(seed, x, y, z, deckTop);
    }

    /** 가로등 기둥(받침 위 rel 1부터): 기둥 n칸, 등, 갓. */
    private static BlockState post(int rel, int n) {
        if (rel >= 1 && rel <= n) return POST;
        if (rel == n + 1) return POST_LAMP;
        if (rel == n + 2) return POST_CAP;
        return null;
    }

    /*
     * 3단 기단 위에 열주(기둥 열)와 큰 전당. 전당 안에는 하얀 신상(둥근 머리, 작은 검은 눈)과
     * 그 앞으로 뻗은 참배로(양옆에 작은 석상과 바닥 등), 참배로 양쪽의 유리 호수(유리 아래 물, 가짜 잉어, 금속 수초, 수중등).
     * 정면(v+)으로 기단을 오르는 넓은 계단 양옆에 작은 석상과 가로등이 번갈아 줄지어 선다.
     */
    private static BlockState temple(long seed, Site s, int u, int v, int x, int y, int z, int deckTop) {
        int b = s.base;
        int t1 = b + 3, t2 = b + 6, t3 = b + 9;
        int m = Math.max(Math.abs(u), Math.abs(v));
        int au = Math.abs(u);

        // 정면 계단: 사원 터 바깥 땅 높이(v 112)에서 맨 위 기단(v 56)까지, |u| <= 8, 반 블록씩 오름
        if (v >= 56 && v <= 112 && au <= 8) {
            double hgt = s.frontY + (t3 - s.frontY) * (112 - v) / 56.0;
            double q = Math.floor(hgt * 2) / 2;
            int full = (int) Math.floor(q);
            if (y <= full) return BRICK;
            if (y == full + 1 && q - full >= 0.5) return STEP;
            return null;
        }

        // 유리 호수(맨 위 기단을 파서 만듦): 참배로 양쪽
        if (au >= 8 && au <= 26 && v >= 2 && v <= 28 && y >= t3 - 2 && y <= t3) {
            if (y == t3) return GLASS;
            double hp = CityLayout.hash01(seed, x, z, 940);
            if (hp < 0.025 && y == t3 - 2) return POND_LIGHT;
            if (hp > 0.95 && y == t3 - 2) {                                     // 금속 수초(바닥에서 한 칸)
                BlockState mp = metalPlant;
                return mp != null ? mp : Blocks.IRON_BARS.defaultBlockState();
            }
            BlockState koi = koi(seed, s, u, v, y - (t3 - 2));
            return koi != null ? koi : WATER;
        }

        // 기단
        int tierTop = m <= 56 ? t3 : m <= 76 ? t2 : m <= 96 ? t1 : Integer.MIN_VALUE;
        if (tierTop != Integer.MIN_VALUE && y <= tierTop) {
            if (y == tierTop) {
                if (m <= 31 && au == 3 && v >= 4 && v <= 30 && Math.floorMod(v, 4) == 0) return FLOOR_LIGHT;   // 참배로 바닥 등
                return TOP;
            }
            return CityLayout.hash01(seed, x * 13 + y, z, 920) < 0.08 ? BRICK_CRACK : BRICK;
        }

        // 계단 양옆: 작은 석상(계단 쪽을 봄)과 가로등이 번갈아 섬
        if (v >= 57 && v <= 111 && au >= 10 && au <= 12) {
            int gTop = v > 96 ? deckTop : v > 76 ? t1 : t2;
            int slot = Math.round((v - 60) / 3f);
            int sv = 60 + slot * 3;
            boolean nearEdge = Math.abs(sv - 76) <= 2 || Math.abs(sv - 96) <= 2 || sv > 109;
            if (!nearEdge && Math.abs(v - sv) <= 1) {
                int su = (int) Math.signum(u) * 11;
                if (Math.floorMod(slot, 2) == 0) {
                    int hh = 5 + CityLayout.rand(seed, s.id + sv, u > 0 ? 1 : 0, 921, 2);
                    BlockState bs = localStatue(seed, s, su, sv, 1, hh, u > 0 ? 3 : 2, false, gTop + 1, x, y, z, Math.min(gTop, deckTop));
                    if (bs != null) return bs;
                } else if (u == su && v == sv && y > gTop) {
                    BlockState ps = post(y - gTop, 3);
                    if (ps != null) return ps;
                }
            }
        }

        // 아래 기단 둘레의 2x2 석상(바깥을 봄)
        if (m >= 84 && m <= 89 && !(v > 0 && au <= 24)) {
            boolean sideV = Math.abs(v) >= au;
            int along = sideV ? u : v;
            int sa = Math.round(along / 16f) * 16;
            int side = sideV ? (v > 0 ? 0 : 1) : (u > 0 ? 2 : 3);
            int su = sideV ? sa : (int) Math.signum(u) * 86;
            int sv = sideV ? (int) Math.signum(v) * 86 : sa;
            if (Math.abs(u - su) <= 2 && Math.abs(v - sv) <= 2) {
                int hh = 9 + CityLayout.rand(seed, s.id + sa, side, 922, 3);
                boolean broken = CityLayout.hash01(seed, s.id + sa, side, 923) < 0.15;
                BlockState bs = localStatue(seed, s, su, sv, 2, hh, side, broken, t1, x, y, z, Math.min(t1, deckTop));
                if (bs != null) return bs;
            }
        }

        int rel = y - t3;
        if (m > 56 || rel <= 0) return null;

        // 신상(전당 가운데, 정면을 봄): 둥근 기둥, 둥근 머리, 작은 검은 눈
        int gu = u, gv = v + 6;
        double R = 5.5;
        int gh = Math.max(24, Math.min(56, s.ceil - t3 - 4));
        int d2 = gu * gu + gv * gv;
        if (d2 <= 8 * 8) {
            if (rel <= 2) return d2 <= 7.5 * 7.5 ? PLINTH : null;            // 받침
            int cr = rel - 3;
            if (cr < gh && d2 <= R * R) {
                int domeStart = gh - 6;
                if (cr > domeStart) {
                    double t = (cr - domeStart) / 6.0;
                    double rr = R * Math.sqrt(Math.max(0, 1 - t * t));
                    if (d2 > rr * rr) return null;
                }
                // 눈: 앞면(v+) 맨 바깥 칸, 머리 바로 아래
                if (Math.abs(gu) >= 2 && Math.abs(gu) <= 3 && cr >= domeStart - 4 && cr <= domeStart - 2) {
                    int front = (int) Math.floor(Math.sqrt(R * R - gu * gu));
                    if (gv == front) return EYE_DARK;
                }
                return sStone();
            }
            // 신상 옆 가로등 4개
        }
        if ((au == 10) && (v == 0 || v == -12)) {
            BlockState ps = post(rel, 4);
            if (ps != null) return ps;
        }

        // 참배로 양옆의 작은 석상(참배로 쪽을 봄)
        if (au >= 5 && au <= 7 && v >= 5 && v <= 31) {
            int sv = 6 + Math.round((v - 6) / 4f) * 4;
            if (sv <= 30 && Math.abs(v - sv) <= 1) {
                int su = (int) Math.signum(u) * 6;
                int hh = 4 + CityLayout.rand(seed, s.id + sv, u > 0 ? 7 : 8, 924, 2);
                BlockState bs = localStatue(seed, s, su, sv, 1, hh, u > 0 ? 3 : 2, false, t3, x, y, z, Math.min(t3, deckTop));
                if (bs != null) return bs;
            }
        }

        // 열주: 맨 위 기단 둘레(m 50~52), 14칸마다 3x3 기둥, 위에 들보. 기둥 사이에 석등.
        int hallH = 32, colH = 26;
        if (m >= 44 && m <= 53) {
            boolean sideV = Math.abs(v) >= au;
            int along = sideV ? u : v;
            boolean frontGap = v > 0 && sideV && au <= 9;
            if (m == 45 && Math.floorMod(along, 14) == 6 && !frontGap) {
                BlockState ps = post(rel, 2);
                if (ps != null) return ps;
            }
            if (m >= 49) {
                boolean pillar = m >= 50 && m <= 52 && Math.floorMod(along + 1, 14) <= 2 && !frontGap;
                if (pillar && rel <= colH) {
                    if (rel > colH - 6 && CityLayout.noise(seed, x, z, 9.0, 924) > 0.78) return null;   // 부러진 기둥 머리
                    return BRICK;
                }
                if ((rel == colH + 1 || rel == colH + 2) && CityLayout.noise(seed, x, z, 12.0, 925) < 0.7) return TOP;   // 들보(군데군데 무너짐)
                return null;
            }
        }

        // 전당: 벽(m 32~33), 정문, 높은 창, 벽등, 지붕(군데군데 무너짐, 신상 자리는 뚫림)
        if (m >= 32 && m <= 33 && rel <= hallH) {
            boolean sideV = Math.abs(v) >= au;
            boolean frontWall = v > 0 && sideV;
            if (frontWall && au <= 7 && rel <= 16) return null;                       // 정문
            int along = sideV ? u : v;
            if (rel >= 18 && rel <= 27 && Math.floorMod(along, 10) == 5) return null;    // 높은 창
            if (m == 32 && rel == 10 && Math.floorMod(along, 10) == 0) return SCONCE;    // 벽등
            if (rel > hallH - 8 && CityLayout.noise(seed, x, z, 14.0, 926) > 0.72) return null;
            return CityLayout.hash01(seed, x * 11 + y, z, 927) < 0.08 ? BRICK_CRACK : BRICK;
        }
        if (m < 32 && rel == hallH + 1) {
            if (d2 <= 8 * 8) return null;
            if (CityLayout.noise(seed, x, z, 16.0, 928) > 0.62) return null;
            return BRICK;
        }
        return null;
    }

    /** 유리 아래 가짜 잉어: 4x4 칸마다 한 마리(없을 수도), 잉어 모양 블록 하나. ly: 0 바닥 쪽, 1 유리 바로 아래. */
    private static BlockState koi(long seed, Site s, int u, int v, int ly) {
        int ci = Math.floorDiv(u, 4), cj = Math.floorDiv(v, 4);
        int key = s.id * 31 + ci;
        if (CityLayout.hash01(seed, key, cj, 941) >= 0.45) return null;
        if (CityLayout.rand(seed, key, cj, 942, 2) != ly) return null;
        int hu = ci * 4 + CityLayout.rand(seed, key, cj, 943, 4), hv = cj * 4 + CityLayout.rand(seed, key, cj, 944, 4);
        if (u != hu || v != hv) return null;
        BlockState[][] k = koiStates;
        if (k == null) return KOI_FALLBACK;
        int c = CityLayout.rand(seed, key, cj, 947, 10);
        int color = c < 5 ? 0 : c < 7 ? 1 : 2;
        return k[color][CityLayout.rand(seed, key, cj, 945, 4)];
    }

    // ---------------- 공군 기지 ----------------
    /*
     * 넓은 콘크리트 계류장 위에 활주로(표지와 끝 줄무늬, 일부 켜진 활주로 등), 둥근 지붕의 격납고 셋, 관제탑, 부서진 비행기.
     * u: 활주로 방향(길이 480), v: 폭 방향(200).
     */
    private static BlockState airbase(long seed, Site s, int u, int v, int x, int y, int z, int deckTop) {
        int b = s.base;
        if (y < b) return APRON;                                    // 기초
        if (y == b) {
            if (v >= -62 && v <= -28 && Math.abs(u) <= 232) {       // 활주로
                if (v == -62 || v == -28) {
                    if (Math.floorMod(u, 24) == 0 && CityLayout.hash01(seed, s.id + u, v, 930) < 0.55) return RUNWAY_LIGHT;
                    return MARK;
                }
                if (v == -45 && Math.floorMod(u, 20) < 10 && Math.abs(u) < 200) return MARK;           // 중앙선
                if (Math.abs(u) >= 212 && Math.abs(u) <= 226 && Math.floorMod(v + 62, 4) < 2) return MARK;   // 끝 줄무늬
                return CityLayout.hash01(seed, x, z, 931) < 0.03 ? APRON : RUNWAY;
            }
            if (Math.floorMod(u, 20) == 0 || Math.floorMod(v, 20) == 0) return JOINT;   // 줄눈
            return APRON;
        }
        int rel = y - b;

        // 격납고: 반원통 지붕(반폭 35, 높이 26), v 30~90, 정면(v=30)은 열려 있음
        for (int hc : new int[]{-150, -60, 30}) {
            int du = u - hc;
            if (Math.abs(du) <= 35 && v >= 30 && v <= 90) {
                double hh = 26 * Math.sqrt(Math.max(0, 1 - (du / 35.0) * (du / 35.0)));
                double hn = 26 * Math.sqrt(Math.max(0, 1 - ((Math.abs(du) + 1) / 35.0) * ((Math.abs(du) + 1) / 35.0)));
                int hi = (int) Math.round(hh), lo = Math.min(hi, (int) Math.round(hn));
                boolean shell = rel >= lo && rel <= hi;
                boolean back = v == 90 && rel <= hi;
                boolean frame = v == 30 && rel >= lo && rel <= hi;
                if (shell || back || frame) {
                    if (shell && v > 32 && v < 90 && CityLayout.noise(seed, x, z, 11.0, 932 + hc) > 0.7) return null;   // 무너진 지붕
                    return frame || (Math.floorMod(v, 12) == 6 && shell) ? HANGAR_FRAME : HANGAR;
                }
                return null;
            }
        }

        // 관제탑
        int tu = u - 130, tv = v - 60;
        if (Math.abs(tu) <= 7 && Math.abs(tv) <= 7) {
            if (Math.abs(tu) <= 4 && Math.abs(tv) <= 4 && rel <= 34) {
                if ((Math.abs(tu) == 4 || Math.abs(tv) == 4) && Math.floorMod(rel, 6) == 3 && (tu == 0 || tv == 0)) return null;   // 작은 창
                return HANGAR;
            }
            if (rel == 34 || rel == 40) return HANGAR_FRAME;                        // 관제실 바닥과 지붕
            if (rel > 34 && rel < 40 && (Math.abs(tu) == 7 || Math.abs(tv) == 7)) {
                if (Math.abs(tu) == 7 && Math.abs(tv) == 7) return HANGAR_FRAME;
                return CityLayout.hash01(seed, x * 5 + y, z, 933) < 0.25 ? null : GLASS;   // 깨진 유리
            }
            return null;
        }

        // 부서진 비행기(계류장 둘, 활주로 위 하나)
        BlockState pl = plane(u + 40, v - 2, rel, 0);
        if (pl != null) return pl;
        pl = plane(u - 140, v + 4, rel, 1);
        if (pl != null) return pl;
        pl = plane(-(u + 120), v + 45, rel, 2);
        if (pl != null) return pl;
        return null;
    }

    /**
     * 비행기 잔해(어두운 회색이라 눈 위에서도 잘 보임): 길이 37의 둥근 동체, 폭 45의 뒤로 젖힌 날개, 수직/수평 꼬리날개, 검은 조종석.
     * variant 1: 한쪽 날개가 떨어져 나가고 기수가 땅에 처박힘. variant 2: 동체가 두 동강, 꼬리가 꺾임.
     */
    private static BlockState plane(int du, int dv, int rel, int variant) {
        if (Math.abs(du) > 19 || Math.abs(dv) > 22 || rel < 1 || rel > 14) return null;
        int adv = Math.abs(dv);
        if (variant == 2 && du >= -2 && du <= 0) return null;                 // 두 동강
        // 기울기: variant 1은 기수가 땅에 박히고(앞으로 숙임) 왼쪽 날개가 땅에 닿음(옆으로 기욺)
        double pitch = 0, roll = 0, cy0 = 3.0;
        if (variant == 1) { pitch = -0.19; roll = 0.2; cy0 = 3.6; }
        double cy = cy0 + du * pitch;
        if (variant == 2 && du < -2) { cy = 3.0 + (-2 - du) * 0.28; roll = -0.1; }   // 뒤쪽 동강이 들림
        // 동체
        if (du >= -18 && du <= 18) {
            double r = du > 14 ? 2.2 - (du - 14) * 0.45 : 2.2;
            double dy = rel - (cy + dv * roll);
            if (dv * dv + dy * dy <= r * r) {
                if (du >= 11 && du <= 14 && dy >= 1 && adv <= 1) return PLANE_DARK;   // 조종석
                return PLANE_BODY;
            }
        }
        // 주날개(한 칸 두께, 기울기를 따라 높이가 변함)
        if (adv >= 2 && adv <= 22) {
            if (variant == 1 && dv > 7) return null;                          // 떨어져 나간 날개
            double le = 5 - adv * 0.45, te = -5 - adv * 0.25;
            if (du <= le && du >= te && rel == (int) Math.round(cy + dv * roll)) return PLANE_WING;
        }
        // 꼬리
        if (du >= -18 && du <= -13) {
            int fin = 5 + (int) Math.round((du + 18) * -0.8 + 5);              // 뒤로 갈수록 높아짐
            int base = (int) Math.round(cy);
            if (dv == 0 && rel > base + 1 && rel <= base + 1 + Math.max(1, fin - 4)) return PLANE_WING;
            if (rel == (int) Math.round(cy + 1 + dv * roll) && adv <= 7 && adv >= 2) return PLANE_WING;
        }
        return null;
    }

}
