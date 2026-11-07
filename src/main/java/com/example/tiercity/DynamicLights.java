package com.example.tiercity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 움직이는 빛: 바라보는 방향으로 광선을 쏴서, 부딪힌 곳 바로 앞과 광선 중간 몇 곳에 보이지 않는 빛 블록(minecraft:light)을 놓는다.
 * 다음 갱신 때 더 이상 쓰지 않는 자리는 지운다. 손전등뿐 아니라 나중에 탈것의 전조등에도 그대로 쓴다(키만 다르게).
 */
public final class DynamicLights {
    private DynamicLights() {}

    private record Placed(ResourceKey<Level> dim, Set<BlockPos> pos) {}

    private static final Map<Object, Placed> PLACED = new HashMap<>();

    /** 엔티티의 눈 위치에서 바라보는 방향으로 비춘다. */
    public static void beamFrom(Entity e, Object key, int range) {
        if (!(e.level() instanceof ServerLevel level)) return;
        beam(level, key, e.getEyePosition(), e.getViewVector(1.0F), range, e);
    }

    /** from에서 dir 방향으로 range칸까지 비춘다. 부딪힌 곳 앞은 15, 광선 중간은 거리에 따라 12~9. */
    public static void beam(ServerLevel level, Object key, Vec3 from, Vec3 dir, int range, Entity ignore) {
        Vec3 d = dir.normalize();
        Vec3 to = from.add(d.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ignore));
        double dist = hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(from);
        Map<BlockPos, Integer> want = new HashMap<>();
        if (hit.getType() == HitResult.Type.BLOCK) {
            want.put(hit.getBlockPos().relative(hit.getDirection()), 15);
        } else {
            want.put(BlockPos.containing(to), 13);
        }
        for (double s = 3.0; s < dist - 1.5; s += 4.0) {
            int lv = Math.max(9, 13 - (int) (s / 6));
            want.merge(BlockPos.containing(from.add(d.scale(s))), lv, Math::max);
        }
        apply(level, key, want);
    }

    private static void apply(ServerLevel level, Object key, Map<BlockPos, Integer> want) {
        Placed old = PLACED.get(key);
        if (old != null && old.dim() != level.dimension()) {
            clear(level.getServer(), key);
            old = null;
        }
        Set<BlockPos> now = new HashSet<>();
        for (Map.Entry<BlockPos, Integer> e : want.entrySet()) {
            BlockPos p = e.getKey();
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            boolean ours = old != null && old.pos().contains(p) && s.is(Blocks.LIGHT);
            if (!s.isAir() && !ours) continue;
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, e.getValue());
            if (s != light) level.setBlock(p, light, 3);
            now.add(p.immutable());
        }
        if (old != null) {
            for (BlockPos p : old.pos()) {
                if (!now.contains(p) && level.isLoaded(p) && level.getBlockState(p).is(Blocks.LIGHT)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (now.isEmpty()) PLACED.remove(key);
        else PLACED.put(key, new Placed(level.dimension(), now));
    }

    /** 이 키로 놓은 빛을 모두 지운다. */
    public static void clear(MinecraftServer server, Object key) {
        Placed old = PLACED.remove(key);
        if (old == null || server == null) return;
        ServerLevel level = server.getLevel(old.dim());
        if (level == null) return;
        for (BlockPos p : old.pos()) {
            if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.LIGHT)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public static boolean has(Object key) {
        return PLACED.containsKey(key);
    }

    /** 서버가 꺼질 때: 남은 빛을 모두 지운다. */
    public static void clearAll(MinecraftServer server) {
        for (Iterator<Object> it = new HashSet<>(PLACED.keySet()).iterator(); it.hasNext(); ) clear(server, it.next());
    }
}
