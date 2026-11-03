package com.example.tiercity.client;

import com.example.tiercity.TierCityMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 하늘 창: 하늘이 그려진 직후 화면을 복사해 두고, 하늘 패널의 아랫면 자리에 그 복사본을 그대로 다시 그린다.
 * 그래서 패널을 올려다보면 판과 그 위의 구조물 대신, 그 방향의 진짜 하늘(해, 달, 별, 노을)이 보인다.
 * 패널 위치는 클라이언트가 받은 청크에서 찾아 청크별 메시로 만들어 둔다(클라이언트 전용).
 */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID, value = Dist.CLIENT)
public final class SkyWindowRenderer {
    private record Mesh(VertexBuffer vbo, int minY, int maxY) {}

    private static final Map<Long, Mesh> MESHES = new HashMap<>();
    private static final Set<Long> LOADED = new LinkedHashSet<>();
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final BufferBuilder BUILDER = new BufferBuilder(8192);
    private static final int BUILD_PER_FRAME = 6;     // 새로 받은 청크 처리량
    private static final int RESCAN_PER_FRAME = 2;    // 블록 변화 반영을 위한 순환 재검사량
    private static List<Long> rescanList = new ArrayList<>();
    private static int rescanCursor = 0;
    private static TextureTarget skyCopy;

    private SkyWindowRenderer() {}

    // ---------------- 청크 추적 ----------------
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide()) return;
        long key = event.getChunk().getPos().toLong();
        LOADED.add(key);
        if (QUEUED.add(key)) QUEUE.add(key);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        long key = event.getChunk().getPos().toLong();
        LOADED.remove(key);
        Mesh m = MESHES.remove(key);
        if (m != null) m.vbo().close();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        for (Mesh m : MESHES.values()) m.vbo().close();
        MESHES.clear();
        LOADED.clear();
        QUEUE.clear();
        QUEUED.clear();
        rescanList = new ArrayList<>();
        rescanCursor = 0;
    }

    // ---------------- 그리기 ----------------
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (!MESHES.isEmpty()) copySky(mc);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

        updateMeshes(level);
        if (MESHES.isEmpty() || skyCopy == null || SkyWindowShaders.skyWindow == null) return;

        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        PoseStack pose = event.getPoseStack();
        Matrix4f proj = event.getProjectionMatrix();

        RenderSystem.setShaderTexture(0, skyCopy.getColorTextureId());
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);   // 같은 자리의 패널 블록 아랫면보다 항상 앞에 그려지도록

        for (Map.Entry<Long, Mesh> e : MESHES.entrySet()) {
            long key = e.getKey();
            Mesh m = e.getValue();
            int bx = ChunkPos.getX(key) * 16, bz = ChunkPos.getZ(key) * 16;
            if (frustum != null && !frustum.isVisible(new AABB(bx, m.minY(), bz, bx + 16, m.maxY() + 1, bz + 16))) continue;
            pose.pushPose();
            pose.translate(bx - cam.x, -cam.y, bz - cam.z);
            m.vbo().bind();
            m.vbo().drawWithShader(pose.last().pose(), proj, SkyWindowShaders.skyWindow);
            pose.popPose();
        }
        VertexBuffer.unbind();

        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
    }

    /** 하늘만 그려진 상태의 화면을 복사해 둔다. */
    private static void copySky(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        if (skyCopy == null || skyCopy.width != main.width || skyCopy.height != main.height) {
            if (skyCopy != null) skyCopy.destroyBuffers();
            skyCopy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            skyCopy.setFilterMode(GL11.GL_NEAREST);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, skyCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, skyCopy.width, skyCopy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
    }

    // ---------------- 메시 ----------------
    private static void updateMeshes(ClientLevel level) {
        for (int n = 0; n < BUILD_PER_FRAME && !QUEUE.isEmpty(); n++) {
            long key = QUEUE.poll();
            QUEUED.remove(key);
            if (LOADED.contains(key)) rebuild(level, key);
        }
        if (LOADED.isEmpty()) return;
        for (int n = 0; n < RESCAN_PER_FRAME; n++) {
            if (rescanCursor >= rescanList.size()) {
                rescanList = new ArrayList<>(LOADED);
                rescanCursor = 0;
                if (rescanList.isEmpty()) return;
            }
            long key = rescanList.get(rescanCursor++);
            if (LOADED.contains(key) && !QUEUED.contains(key)) rebuild(level, key);
        }
    }

    private static void rebuild(ClientLevel level, long key) {
        Mesh old = MESHES.remove(key);
        if (old != null) old.vbo().close();

        LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false);
        if (chunk == null) return;
        Block panel = TierCityMod.SKY_PANEL.get();

        Map<Integer, boolean[]> planes = new HashMap<>();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec == null || sec.hasOnlyAir()) continue;
            if (!sec.maybeHas(s -> s.is(panel))) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (!sec.getBlockState(lx, ly, lz).is(panel)) continue;
                        BlockState below = ly > 0 ? sec.getBlockState(lx, ly - 1, lz)
                                : chunk.getBlockState(pos.set(minX + lx, baseY + ly - 1, minZ + lz));
                        if (below.canOcclude()) continue;   // 아랫면이 가려져 있으면 창이 필요 없음
                        planes.computeIfAbsent(baseY + ly, y -> new boolean[256])[lx + lz * 16] = true;
                    }
                }
            }
        }
        if (planes.isEmpty()) return;

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        BUILDER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (Map.Entry<Integer, boolean[]> e : planes.entrySet()) {
            int y = e.getKey();
            boolean[] m = e.getValue();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            for (int z = 0; z < 16; z++) {
                int x = 0;
                while (x < 16) {
                    if (!m[x + z * 16]) { x++; continue; }
                    int x0 = x;
                    while (x < 16 && m[x + z * 16]) x++;
                    // 아랫면(블록의 y 평면), 한 줄로 합침
                    BUILDER.vertex(x0, y, z + 1).endVertex();
                    BUILDER.vertex(x0, y, z).endVertex();
                    BUILDER.vertex(x, y, z).endVertex();
                    BUILDER.vertex(x, y, z + 1).endVertex();
                }
            }
        }
        BufferBuilder.RenderedBuffer rendered = BUILDER.end();
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(rendered);
        VertexBuffer.unbind();
        MESHES.put(key, new Mesh(vbo, minY, maxY));
    }
}
