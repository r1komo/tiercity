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

    /** 월드 최초 생성 시 스폰: 첫 구획 중심에서 +X로 300블록 떨어진 데크 위. */
    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof ServerLevel level)) return;
        if (!(level.getChunkSource().getGenerator() instanceof LayeredCityChunkGenerator)) return;

        long seed = LayeredCityChunkGenerator.layoutSeed(level.getChunkSource().randomState());
        int sx = CityLayout.MID + CityLayout.SPAWN_OFFSET;
        int sz = CityLayout.MID;
        CityLayout.Column col = CityLayout.column(seed, sx, sz);
        if (col.deckTop() < 0) return;

        event.getSettings().setSpawn(new BlockPos(sx, col.deckTop() + 1, sz), 90.0F);
        event.setCanceled(true);
    }

    /** 허공으로 떨어져 죽으면 아이템을 가장 가까운 데크 가장자리 안쪽으로 옮긴다. */
    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(level.getChunkSource().getGenerator() instanceof LayeredCityChunkGenerator)) return;
        if (event.getDrops().isEmpty()) return;

        long seed = LayeredCityChunkGenerator.layoutSeed(level.getChunkSource().randomState());
        double x = player.getX(), z = player.getZ();
        int[] c = CityLayout.nearestCenter((int) Math.floor(x), (int) Math.floor(z));
        double dx = x - c[0], dz = z - c[1];
        double len = Math.sqrt(dx * dx + dz * dz);
        double max = CityLayout.CELL / 2.0 - 8.0;   // 난간 안쪽
        double tx = x, tz = z;
        if (len > max) {
            tx = c[0] + dx / len * max;
            tz = c[1] + dz / len * max;
        }
        int ix = (int) Math.floor(tx), iz = (int) Math.floor(tz);
        CityLayout.Column col = CityLayout.column(seed, ix, iz);
        if (col.deckTop() < 0) return;
        int iy = col.deckTop() + 1;

        for (ItemEntity item : event.getDrops()) {
            item.setPos(tx + (level.random.nextDouble() - 0.5) * 2.0, iy, tz + (level.random.nextDouble() - 0.5) * 2.0);
            item.setDeltaMovement(Vec3.ZERO);
            item.setUnlimitedLifetime();
            item.setInvulnerable(true);
        }
        player.sendSystemMessage(Component.translatable("message.tiercity.items_recovered", ix, iy, iz));
    }
}
