package com.example.tiercity.client;

import com.example.tiercity.CityLayout;
import com.example.tiercity.TierCityMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 최하층 판 아래(배관 공간)에 있을 때 플레이어를 둘러싼 검은 안개: 가까운 곳만 보이고 그 너머는 어둠에 묻힌다.
 * 시야 거리가 짧아도 허공이나 하늘이 보이지 않아 닫힌 공간처럼 느껴진다. 판을 지나며 서서히 바뀜(밝기 효과와 같은 구간).
 */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID, value = Dist.CLIENT)
public final class PipeFog {
    private static final float FAR = 34.0F;     // 이 거리에서 완전히 검게

    private PipeFog() {}

    /** 0: 판 위, 1: 판 아래 깊숙이. */
    static double depth(double camY) {
        double under = CityLayout.layerBottom(0);
        double t = (under + 4 - camY) / 14.0;
        return Math.max(0, Math.min(1, t));
    }

    private static boolean ours() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.effects() instanceof TierCityEffects;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!ours()) return;
        double t = depth(event.getCamera().getPosition().y);
        if (t <= 0) return;
        float far = (float) (event.getFarPlaneDistance() + (FAR - event.getFarPlaneDistance()) * t);
        float near = (float) (event.getNearPlaneDistance() + (0.0F - event.getNearPlaneDistance()) * t);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), far));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), near));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ours()) return;
        double t = depth(event.getCamera().getPosition().y);
        if (t <= 0) return;
        float k = (float) (1 - t);
        event.setRed(event.getRed() * k);
        event.setGreen(event.getGreen() * k);
        event.setBlue(event.getBlue() * k);
    }
}
