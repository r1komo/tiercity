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
    }

    // ---------------- 블록 ----------------
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState STONE_ALT = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState BRICK_CRACK = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState TOP = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState STEP = Blocks.STONE_BRICK_SLAB.defaultBlockState();
    private static final BlockState APRON = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState JOINT = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState RUNWAY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState MARK = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState RUNWAY_LIGHT = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    private static final BlockState HANGAR = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState HANGAR_FRAME = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState METAL = Blocks.IRON_BLOCK.defaultBlockState();

    // ---------------- 석상 ----------------
    /** 위쪽에 눈과 입이 뚫린 기둥 모양 석상. hw는 기둥 반폭(1이면 3x3). face: 0 +z, 1 -z, 2 +x, 3 -x. */
    static final class Statue implements Struct {
        final int cx, cz, base, h, hw, face;
        final boolean broken;

        Statue(int cx, int cz, int base, int h, int hw, int face, boolean broken) {
            this.cx = cx; this.cz = cz; this.base = base; this.h = h; this.hw = hw; this.face = face; this.broken = broken;
        }

        int ped() { return hw + 1; }

        boolean contains(int x, int z) {
            return Math.abs(x - cx) <= ped() && Math.abs(z - cz) <= ped();
        }

        @Override
        public int maxY() { return base + 2 + h; }

        @Override
        public BlockState block(long seed, int x, int y, int z, int deckTop) {
            int dx = x - cx, dz = z - cz, ped = ped();
            if (Math.abs(dx) > ped || Math.abs(dz) > ped || y <= deckTop) return null;
            if (y < base + 2) return BRICK;                          // 받침(기초 포함)
            if (Math.abs(dx) > hw || Math.abs(dz) > hw) return null;
            int cr = y - base - 2;
            int top = h - 1;
            if (broken) {
                top = (int) (h * 0.35) + CityLayout.rand(seed, cx, cz, 901, Math.max(1, h / 3)) + CityLayout.rand(seed, x, z, 902, 2);
            }
            if (cr > top) return null;
            if (!broken) {
                if (cr == h - 1 && Math.abs(dx) == hw && Math.abs(dz) == hw) return null;   // 둥근 머리
                int lat, depth;
                switch (face) {
                    case 0: lat = dx; depth = hw - dz; break;
                    case 1: lat = -dx; depth = hw + dz; break;
                    case 2: lat = -dz; depth = hw - dx; break;
                    default: lat = dz; depth = hw + dx; break;
                }
                if (faceHole(lat, depth, cr, h, hw)) return null;
            }
            return CityLayout.hash01(seed, x * 7 + y, z, 903) < 0.1 ? STONE_ALT : STONE;
        }
    }

    /** 석상 얼굴의 구멍(눈 둘, 입 하나). lat: 얼굴 가로 위치, depth: 얼굴 면에서 안쪽으로 몇 칸, cr: 기둥 아래에서 몇 번째 줄. */
    static boolean faceHole(int lat, int depth, int cr, int h, int hw) {
        int s = Math.max(1, hw / 2);
        if (depth >= s) return false;
        int c = Math.max(1, (hw + 1) / 2);
        int ew = s / 2;
        int eyeTop = h - 1 - s, eyeBot = eyeTop - s + 1;
        if (cr >= eyeBot && cr <= eyeTop && Math.abs(Math.abs(lat) - c) <= ew) return true;
        int mouthTop = eyeBot - 1 - s, mouthBot = mouthTop - Math.max(0, s - 1);
        int mw = ew + (hw >= 2 ? 1 : 0);
        return cr >= mouthBot && cr <= mouthTop && Math.abs(lat) <= mw;
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
    /*
     * 3단 기단 위에 열주(기둥 열)와 큰 전당, 그 안에 지붕을 뚫고 솟은 거대한 석상.
     * 정면(v+)으로 기단을 오르는 넓은 계단(반 블록 단), 계단 양옆과 아래 기단 둘레에 작은 석상이 줄지어 선다.
     */
    private static BlockState temple(long seed, Site s, int u, int v, int x, int y, int z, int deckTop) {
        int b = s.base;
        int t1 = b + 3, t2 = b + 6, t3 = b + 9;
        int m = Math.max(Math.abs(u), Math.abs(v));

        // 정면 계단: 사원 터 바깥 땅 높이(v 112)에서 맨 위 기단(v 56)까지, |u| <= 8, 반 블록씩 오름
        if (v >= 56 && v <= 112 && Math.abs(u) <= 8) {
            double hgt = s.frontY + (t3 - s.frontY) * (112 - v) / 56.0;
            double q = Math.floor(hgt * 2) / 2;
            int full = (int) Math.floor(q);
            if (y <= full) return BRICK;
            if (y == full + 1 && q - full >= 0.5) return STEP;
            return null;
        }

        // 기단
        int tierTop = m <= 56 ? t3 : m <= 76 ? t2 : m <= 96 ? t1 : Integer.MIN_VALUE;
        if (tierTop != Integer.MIN_VALUE && y <= tierTop) {
            if (y == tierTop) return TOP;
            return CityLayout.hash01(seed, x * 13 + y, z, 920) < 0.08 ? BRICK_CRACK : BRICK;
        }

        // 계단 옆 석상(정면을 계단 쪽으로)
        if (Math.abs(Math.abs(u) - 15) <= 2 && v >= 60 && v <= 108) {
            int sv = 60 + Math.round((v - 60) / 12f) * 12;
            if (Math.abs(v - sv) <= 2 && sv <= 108) {
                int sx = (int) Math.signum(u) * 15;
                int top = sv > 96 ? b - 1 : sv > 76 ? t1 : t2;
                Statue st = new Statue(0, 0, top + 1, 14, 1, u > 0 ? 3 : 2, CityLayout.hash01(seed, s.id, sv * 3 + (u > 0 ? 1 : 0), 921) < 0.15);
                BlockState bs = st.block(seed, u - sx, y, v - sv, Math.min(top, deckTop));
                if (bs != null) return bs;
            }
        }
        // 아래 기단 둘레의 석상(바깥을 봄)
        if (m >= 84 && m <= 88 && !(v > 0 && Math.abs(u) <= 24)) {
            int along = Math.abs(v) >= Math.abs(u) ? u : v;
            int sa = Math.round(along / 16f) * 16;
            int side = Math.abs(v) >= Math.abs(u) ? (v > 0 ? 0 : 1) : (u > 0 ? 2 : 3);
            int du = side <= 1 ? u - sa : u - (int) Math.signum(u) * 86;
            int dv = side <= 1 ? v - (int) Math.signum(v) * 86 : v - sa;
            if (Math.abs(du) <= 2 && Math.abs(dv) <= 2) {
                Statue st = new Statue(0, 0, t1 + 1, 12, 1, side, CityLayout.hash01(seed, s.id + sa, side, 922) < 0.2);
                BlockState bs = st.block(seed, du, y, dv, t1);
                if (bs != null) return bs;
            }
        }

        int rel = y - t3;
        if (m > 56 || rel <= 0) return null;

        // 거대 석상(전당 가운데, 정면을 봄)
        int gu = u, gv = v + 6, ghw = 5;
        int gh = Math.max(24, Math.min(56, s.ceil - t3 - 4));
        if (Math.abs(gu) <= ghw + 2 && Math.abs(gv) <= ghw + 2) {
            if (rel <= 2) return BRICK;                                        // 받침
            if (Math.abs(gu) <= ghw && Math.abs(gv) <= ghw) {
                int cr = rel - 3;
                if (cr < gh) {
                    if (cr >= gh - 2 && Math.abs(gu) >= ghw - (cr - gh + 3) && Math.abs(gv) >= ghw - (cr - gh + 3)) return null;   // 둥근 머리
                    if (faceHole(gu, ghw - gv, cr, gh, ghw)) return null;
                    return CityLayout.hash01(seed, x * 7 + y, z, 923) < 0.1 ? STONE_ALT : STONE;
                }
            }
        }

        // 열주: 맨 위 기단 둘레(m 50~52), 14칸마다 3x3 기둥, 위에 들보
        int hallH = 32, colH = 26;
        if (m >= 49 && m <= 53) {
            int along = Math.abs(v) >= Math.abs(u) ? u : v;
            boolean frontGap = v > 0 && Math.abs(v) >= Math.abs(u) && Math.abs(u) <= 9;
            boolean pillar = m >= 50 && m <= 52 && Math.floorMod(along + 1, 14) <= 2 && !frontGap;
            if (pillar && rel <= colH) {
                if (rel > colH - 6 && CityLayout.noise(seed, x, z, 9.0, 924) > 0.78) return null;   // 부러진 기둥 머리
                return BRICK;
            }
            if ((rel == colH + 1 || rel == colH + 2) && CityLayout.noise(seed, x, z, 12.0, 925) < 0.7) return TOP;   // 들보(군데군데 무너짐)
            return null;
        }

        // 전당: 벽(m 32~33), 정문, 높은 창, 지붕(군데군데 무너짐, 석상 자리는 뚫림)
        if (m >= 32 && m <= 33 && rel <= hallH) {
            boolean frontWall = v > 0 && Math.abs(v) >= Math.abs(u);
            if (frontWall && Math.abs(u) <= 7 && rel <= 16) return null;                 // 정문
            int along = Math.abs(v) >= Math.abs(u) ? u : v;
            if (rel >= 18 && rel <= 27 && Math.floorMod(along, 10) == 5) return null;    // 높은 창
            if (rel > hallH - 8 && CityLayout.noise(seed, x, z, 14.0, 926) > 0.72) return null;
            return CityLayout.hash01(seed, x * 11 + y, z, 927) < 0.08 ? BRICK_CRACK : BRICK;
        }
        if (m < 32 && rel == hallH + 1) {
            if (Math.abs(gu) <= ghw + 2 && Math.abs(gv) <= ghw + 2) return null;
            if (CityLayout.noise(seed, x, z, 16.0, 928) > 0.62) return null;
            return BRICK;
        }
        return null;
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

        // 부서진 비행기(활주로 옆 계류장)
        BlockState pl = plane(seed, s, u + 40, v - 2, rel, 0);
        if (pl != null) return pl;
        pl = plane(seed, s, u - 140, v + 4, rel, 1);
        if (pl != null) return pl;
        return null;
    }

    /** 단순한 비행기 잔해: 동체, 날개, 꼬리. variant 1은 한쪽 날개가 떨어져 나갔고 기수가 무너짐. */
    private static BlockState plane(long seed, Site s, int du, int dv, int rel, int variant) {
        if (Math.abs(du) > 16 || Math.abs(dv) > 14) return null;
        boolean fuselage = Math.abs(dv) <= 1 && du >= -14 && du <= 14 && rel >= 1 && rel <= 3;
        if (fuselage && variant == 1 && du > 9 && rel == 3) return null;
        if (fuselage && Math.abs(dv) == 1 && rel == 3 && (du == -14 || du == 14)) return null;
        boolean wing = du >= -2 && du <= 3 && Math.abs(dv) <= 13 && rel == 2;
        if (wing && variant == 1 && dv > 5) return null;
        boolean tail = du >= -14 && du <= -11 && ((dv == 0 && rel >= 4 && rel <= 7) || (Math.abs(dv) <= 4 && rel == 3));
        return (fuselage || wing || tail) ? METAL : null;
    }
}
