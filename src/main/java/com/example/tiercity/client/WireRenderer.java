package com.example.tiercity.client;

import com.example.tiercity.FixtureBlocks;
import com.example.tiercity.TierCityMod;
import com.example.tiercity.WireBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** 전선: 두 연결점 사이를 늘어진 곡선(가는 끈 16마디)으로 그린다. 완목은 두 가닥, 벽 연결점은 한 가닥. */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WireRenderer implements BlockEntityRenderer<WireBlockEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(TierCityMod.MODID, "textures/block/power_line.png");
    private static final int SEGS = 16;
    private static final float W = 0.035F;                 // 전선 반 굵기

    public WireRenderer(BlockEntityRendererProvider.Context ctx) {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TierCityMod.WIRE_BE.get(), WireRenderer::new);
    }

    @Override
    public boolean shouldRenderOffScreen(WireBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 160; }

    /** 이 블록의 전선이 걸리는 점들(월드 좌표). */
    static List<Vec3> points(BlockState st, BlockPos p) {
        List<Vec3> out = new ArrayList<>(2);
        if (st.getBlock() instanceof FixtureBlocks.Crossarm) {
            double y = p.getY() + 15 / 16.0;
            if (st.getValue(FixtureBlocks.Crossarm.AXIS) == Direction.Axis.X) {
                out.add(new Vec3(p.getX() + 2 / 16.0, y, p.getZ() + 0.5));
                out.add(new Vec3(p.getX() + 14 / 16.0, y, p.getZ() + 0.5));
            } else {
                out.add(new Vec3(p.getX() + 0.5, y, p.getZ() + 2 / 16.0));
                out.add(new Vec3(p.getX() + 0.5, y, p.getZ() + 14 / 16.0));
            }
        } else if (st.getBlock() instanceof FixtureBlocks.Anchor) {
            Direction f = st.getValue(HorizontalDirectionalBlock.FACING);
            out.add(new Vec3(p.getX() + 0.5 + f.getStepX() * 0.19, p.getY() + 10 / 16.0, p.getZ() + 0.5 + f.getStepZ() * 0.19));
        } else {
            out.add(Vec3.atCenterOf(p));
        }
        return out;
    }

    /** 전선 모양은 한 번 계산해 둔다(매 프레임 새로 만들지 않음). 상대 쪽 청크가 아직 없으면 다음에 다시 계산. */
    private static final class Geo {
        float[] verts;          // 사각형마다 꼭짓점 4개 x (x, y, z), 블록 기준 상대 좌표
        long[] lightAt;         // 사각형마다 밝기를 읽을 칸
        boolean complete;
        long[] targets;
    }

    private static final java.util.WeakHashMap<WireBlockEntity, Geo> CACHE = new java.util.WeakHashMap<>();
    private static final BlockPos.MutableBlockPos MPOS = new BlockPos.MutableBlockPos();

    @Override
    public void render(WireBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || be.targets().length == 0) return;
        Geo g = CACHE.get(be);
        if (g == null || !g.complete || g.targets != be.targets()) {
            g = build(level, be);
            CACHE.put(be, g);
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float[] v = g.verts;
        for (int q = 0; q < g.lightAt.length; q++) {
            int li = LevelRenderer.getLightColor(level, MPOS.set(g.lightAt[q]));
            int o = q * 12;
            vtx(vc, m, n, v[o], v[o + 1], v[o + 2], 0, 0, li);
            vtx(vc, m, n, v[o + 3], v[o + 4], v[o + 5], 1, 0, li);
            vtx(vc, m, n, v[o + 6], v[o + 7], v[o + 8], 1, 1, li);
            vtx(vc, m, n, v[o + 9], v[o + 10], v[o + 11], 0, 1, li);
        }
    }

    private static Geo build(Level level, WireBlockEntity be) {
        Geo g = new Geo();
        g.targets = be.targets();
        g.complete = true;
        BlockPos pos = be.getBlockPos();
        List<Vec3> mine = points(be.getBlockState(), pos);
        java.util.ArrayList<float[]> quads = new java.util.ArrayList<>();
        java.util.ArrayList<Long> lights = new java.util.ArrayList<>();
        for (long t : be.targets()) {
            BlockPos tp = BlockPos.of(t);
            List<Vec3> theirs;
            if (level.isLoaded(tp)) theirs = points(level.getBlockState(tp), tp);
            else { theirs = List.of(Vec3.atCenterOf(tp)); g.complete = false; }
            Vec3 span = Vec3.atCenterOf(tp).subtract(Vec3.atCenterOf(pos));
            Vec3 side = new Vec3(-span.z, 0, span.x);
            mine.sort((a, b) -> Double.compare(a.dot(side), b.dot(side)));
            List<Vec3> th = new ArrayList<>(theirs);
            th.sort((a, b) -> Double.compare(a.dot(side), b.dot(side)));
            for (int i = 0; i < mine.size(); i++) cable(quads, lights, pos, mine.get(i), th.get(Math.min(i, th.size() - 1)));
        }
        g.verts = new float[quads.size() * 12];
        g.lightAt = new long[quads.size()];
        for (int i = 0; i < quads.size(); i++) {
            System.arraycopy(quads.get(i), 0, g.verts, i * 12, 12);
            g.lightAt[i] = lights.get(i);
        }
        return g;
    }

    private static void cable(List<float[]> quads, List<Long> lights, BlockPos origin, Vec3 a, Vec3 b) {
        double hl = Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z));
        // 처짐: 길이에 비례 + 전선마다 0.7~1.4배 난수(양 끝 위치로 정해서 늘 같음)
        long h = Double.doubleToLongBits(a.x * 31.7 + a.z * 7.3 + b.x * 3.1 + b.z * 13.9 + a.y * 0.37);
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        double rnd = 0.7 + 0.7 * ((h >>> 11) * 0x1.0p-53);
        double sag = (0.04 * hl + 0.2) * rnd;
        Vec3 prev = null;
        for (int i = 0; i <= SEGS; i++) {
            double t = i / (double) SEGS;
            Vec3 p = new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t - sag * 4 * t * (1 - t), a.z + (b.z - a.z) * t);
            if (prev != null) {
                long lp = BlockPos.containing(prev.add(p).scale(0.5)).asLong();
                Vec3 d = p.subtract(prev).normalize();
                Vec3 s = d.cross(new Vec3(0, 1, 0));
                if (s.lengthSqr() < 1e-6) s = new Vec3(1, 0, 0);
                s = s.normalize().scale(W);
                Vec3 u = s.cross(d).normalize().scale(W);
                quads.add(q(origin, prev.add(s), prev.subtract(s), p.subtract(s), p.add(s)));
                lights.add(lp);
                quads.add(q(origin, prev.add(u), prev.subtract(u), p.subtract(u), p.add(u)));
                lights.add(lp);
            }
            prev = p;
        }
    }

    private static float[] q(BlockPos o, Vec3... ps) {
        float[] f = new float[12];
        for (int i = 0; i < 4; i++) {
            f[i * 3] = (float) (ps[i].x - o.getX());
            f[i * 3 + 1] = (float) (ps[i].y - o.getY());
            f[i * 3 + 2] = (float) (ps[i].z - o.getZ());
        }
        return f;
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0, 1, 0).endVertex();
    }
}
