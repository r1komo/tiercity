package com.example.tiercity.client;

import com.example.tiercity.CityLayout;
import com.example.tiercity.TierCityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * 층상 도시 차원의 화면 효과: 하늘은 오버월드와 같고, 구름은 맨 위층보다 위에 둔다.
 * 기본 밝기(dimension_type의 ambient_light 0.3)는 판 위에서는 그대로 두고,
 * 카메라가 최하층 판 아래(배관 공간)로 내려가면 빛이 없는 곳이 거의 깜깜해지도록 화면 밝기 표를 어둡게 바꾼다.
 */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TierCityEffects extends DimensionSpecialEffects {
    public static final ResourceLocation ID = new ResourceLocation(TierCityMod.MODID, "layered_city");
    private static final float AMBIENT_HIGH = 0.3F;     // dimension_type의 ambient_light와 같아야 함
    private static final float AMBIENT_LOW = 0.03F;     // 판 아래에서의 기본 밝기

    public TierCityEffects() {
        super(CityLayout.MIN_Y + CityLayout.HEIGHT - 8, true, SkyType.NORMAL, false, false);
    }

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new TierCityEffects());
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    /** 바닐라 밝기 곡선: 빛 단계(0~15)와 기본 밝기로 정해지는 밝기. */
    private static float curve(float ambient, int level) {
        float f = level / 15.0F;
        float g = f / (4.0F - 3.0F * f);
        return ambient + (1.0F - ambient) * g;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (cam == null) return;
        double y = cam.getPosition().y;
        double under = CityLayout.layerBottom(0);                  // 판 아랫면
        double t = (under + 4 - y) / 14.0;                          // 판 속을 지나며 서서히 바뀜
        if (t <= 0) return;
        if (t > 1) t = 1;
        int lv = Math.max(pixelX, pixelY);                          // 이 칸의 밝은 쪽 빛(블록빛, 하늘빛)
        float k = curve(AMBIENT_LOW, lv) / curve(AMBIENT_HIGH, lv);
        float m = (float) (1.0 - (1.0 - k) * t);
        colors.mul(m);
    }
}
