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

    @Override
    public void render(WireBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || be.targets().length == 0) return;
        BlockPos pos = be.getBlockPos();
        List<Vec3> mine = points(be.getBlockState(), pos);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (long t : be.targets()) {
            BlockPos tp = BlockPos.of(t);
            List<Vec3> theirs = level.isLoaded(tp) ? points(level.getBlockState(tp), tp) : List.of(Vec3.atCenterOf(tp));
            // 가닥끼리 꼬이지 않게: 전선 방향에 수직인 축 위치 순서로 짝지음
            Vec3 span = Vec3.atCenterOf(tp).subtract(Vec3.atCenterOf(pos));
            Vec3 side = new Vec3(-span.z, 0, span.x);
            mine.sort((a, b) -> Double.compare(a.dot(side), b.dot(side)));
            List<Vec3> th = new ArrayList<>(theirs);
            th.sort((a, b) -> Double.compare(a.dot(side), b.dot(side)));
            for (int i = 0; i < mine.size(); i++) {
                Vec3 a = mine.get(i), b = th.get(Math.min(i, th.size() - 1));
                cable(level, vc, m, n, pos, a, b);
            }
        }
    }

    private static void cable(Level level, VertexConsumer vc, Matrix4f m, Matrix3f n, BlockPos origin, Vec3 a, Vec3 b) {
        double hl = Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z));
        double sag = 0.025 * hl + 0.12;
        Vec3 prev = null;
        for (int i = 0; i <= SEGS; i++) {
            double t = i / (double) SEGS;
            Vec3 p = new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t - sag * 4 * t * (1 - t), a.z + (b.z - a.z) * t);
            if (prev != null) {
                int li = LevelRenderer.getLightColor(level, BlockPos.containing(prev.add(p).scale(0.5)));
                Vec3 d = p.subtract(prev).normalize();
                Vec3 s = d.cross(new Vec3(0, 1, 0));
                if (s.lengthSqr() < 1e-6) s = new Vec3(1, 0, 0);
                s = s.normalize().scale(W);
                Vec3 u = s.cross(d).normalize().scale(W);
                quad(vc, m, n, origin, prev.add(s), prev.subtract(s), p.subtract(s), p.add(s), li);
                quad(vc, m, n, origin, prev.add(u), prev.subtract(u), p.subtract(u), p.add(u), li);
            }
            prev = p;
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, BlockPos o, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int light) {
        v(vc, m, n, o, p0, 0, 0, light);
        v(vc, m, n, o, p1, 1, 0, light);
        v(vc, m, n, o, p2, 1, 1, light);
        v(vc, m, n, o, p3, 0, 1, light);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, BlockPos o, Vec3 p, float u, float vv, int light) {
        vc.vertex(m, (float) (p.x - o.getX()), (float) (p.y - o.getY()), (float) (p.z - o.getZ()))
                .color(255, 255, 255, 255).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0, 1, 0).endVertex();
    }
}
