package com.example.tiercity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 배관 공간에 있으면 몇 초마다 화면 아래에 "~에서 바람이 불어오는 것 같다."가 뜬다.
 * 방향은 출구(정비 발판의 계단실)가 있는 쪽을 플레이어가 바라보는 방향 기준으로 말한다(앞, 오른쪽 뒤 등). 가까우면 "위쪽".
 */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID)
public final class WindHint {
    private static final int PERIOD = 160;                      // 8초마다
    private static final String[] DIRS = {"front", "front_right", "right", "back_right", "back", "back_left", "left", "front_left"};

    private WindHint() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % PERIOD != 40) return;
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(level.getChunkSource().getGenerator() instanceof LayeredCityChunkGenerator)) return;
        StartComplex.enabled = TierCityConfig.startInPipeHall();
        CityLayout.Params params = TierCityConfig.params();
        if (!StartComplex.active(params)) return;
        if (player.getY() > StartComplex.CEIL + 1 || player.getY() < StartComplex.minY()) return;
        if (!StartComplex.touches(params, player.getBlockX(), player.getBlockZ())) return;
        long seed = LayeredCityChunkGenerator.layoutSeed(level.getChunkSource().randomState());
        StartComplex.Hall h = StartComplex.hall(params, seed);
        double dx = h.shx + 0.5 - player.getX(), dz = h.shz + 0.5 - player.getZ();
        String key;
        if (dx * dx + dz * dz < 12 * 12) {
            key = "above";
        } else {
            double yaw = Math.toRadians(player.getYRot());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);              // 바라보는 방향
            double dot = fx * dx + fz * dz, cross = fx * dz - fz * dx;   // cross > 0: 오른쪽
            double ang = Math.toDegrees(Math.atan2(cross, dot));
            int sector = (int) Math.floorMod(Math.round(ang / 45.0), 8);
            key = DIRS[sector];
        }
        player.displayClientMessage(Component.translatable("tiercity.wind.msg", Component.translatable("tiercity.wind." + key))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
