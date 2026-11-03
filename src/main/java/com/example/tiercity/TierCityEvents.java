package com.example.tiercity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 이 월드 유형(층상 도시)에서만 동작한다. 다른 월드에는 영향 없음. */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID)
public final class TierCityEvents {
    private TierCityEvents() {}

    /** 월드 최초 생성 시 스폰: 설정 파일의 spawnX/spawnZ에서 가장 가까운 최하층 판 위. */
    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof ServerLevel level)) return;
        if (!(level.getChunkSource().getGenerator() instanceof LayeredCityChunkGenerator)) return;

        long seed = LayeredCityChunkGenerator.layoutSeed(level.getChunkSource().randomState());
        CityLayout.Params params = TierCityConfig.params();
        int[] d = CityLayout.nearestDeck(params, seed, params.spawnX(), params.spawnZ(), 2048, 0);
        if (d == null) return;

        event.getSettings().setSpawn(new BlockPos(d[0], d[2], d[1]), 90.0F);
        event.setCanceled(true);
    }

    /** 허공으로 떨어져 죽으면 아이템을 가장 가까운 남아 있는 판(위층 우선) 위로 옮긴다. */
    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(level.getChunkSource().getGenerator() instanceof LayeredCityChunkGenerator)) return;
        if (event.getDrops().isEmpty()) return;

        long seed = LayeredCityChunkGenerator.layoutSeed(level.getChunkSource().randomState());
        CityLayout.Params params = TierCityConfig.params();
        int[] d = CityLayout.nearestDeck(params, seed, (int) Math.floor(player.getX()), (int) Math.floor(player.getZ()), 4096, -1);
        if (d == null) return;

        int tx = d[0], tz = d[1], ty = d[2];
        // 가장자리에 걸치지 않도록 사망 지점에서 멀어지는 방향으로 3블록 더 안쪽을 시도
        double vx = d[0] - player.getX(), vz = d[1] - player.getZ();
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len > 0.5) {
            int qx = (int) Math.round(d[0] + vx / len * 3.0);
            int qz = (int) Math.round(d[1] + vz / len * 3.0);
            CityLayout.Layer q = CityLayout.layerAt(params, seed, d[3], qx, qz);
            if (q != null) {
                tx = qx;
                tz = qz;
                ty = q.standY();
            }
        }

        for (ItemEntity item : event.getDrops()) {
            item.setPos(tx + 0.5 + (level.random.nextDouble() - 0.5) * 0.8, ty,
                    tz + 0.5 + (level.random.nextDouble() - 0.5) * 0.8);
            item.setDeltaMovement(Vec3.ZERO);
            item.setUnlimitedLifetime();
            item.setInvulnerable(true);
        }
        player.sendSystemMessage(Component.translatable("message.tiercity.items_recovered", tx, ty, tz));
    }
}
