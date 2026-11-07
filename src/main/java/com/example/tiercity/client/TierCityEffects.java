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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * 층상 도시 차원의 화면 효과: 하늘은 오버월드와 같고, 구름은 맨 위층보다 위에 둔다.
 * 기본 밝기(dimension_type의 ambient_light 0.3)는 판 위에서는 그대로 두고,
 * 카메라가 최하층 판 아래(배관 공간)로 내려가면 빛이 없는 곳이 거의 깜깜해지도록 화면 밝기 표를 어둡게 바꾼다.
 */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TierCityEffects extends DimensionSpecialEffects {
    public static final ResourceLocation ID = new ResourceLocation(TierCityMod.MODID, "layered_city");
    private static final float AMBIENT_HIGH = 0.3F;     // dimension_type의 ambient_light와 같아야 함
    private static final float AMBIENT_NIGHT = 0.1F;    // 밤의 기본 밝기
    private static final float AMBIENT_LOW = 0.08F;     // 판 아래에서의 기본 밝기(가까운 곳은 어렴풋이 보임, 먼 곳은 검은 안개)

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

    /** 판 아래에서는 하늘(해, 달, 별)과 구름을 그리지 않는다: 배경은 검은 안개색만 남는다. */
    private static boolean underPlate(Camera camera) {
        return camera != null && PipeFog.depth(camera.getPosition().y) >= 0.5;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        return underPlate(camera);
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        return PipeFog.depth(camY) >= 0.5;
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
        // 밤: 하늘이 어두워질수록 기본 밝기를 0.3에서 0.1로 낮춤(가로등, 불 켜진 창은 그대로)
        float night = Math.max(0F, Math.min(1F, (1.0F - skyDarken) / 0.8F));
        float amb = AMBIENT_HIGH - (AMBIENT_HIGH - AMBIENT_NIGHT) * night;
        // 판 아래(배관 공간): 더 어둡게
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        double t = cam == null ? 0 : PipeFog.depth(cam.getPosition().y);
        if (t > 0) amb = (float) (amb + (AMBIENT_LOW - amb) * t);
        if (Math.abs(amb - AMBIENT_HIGH) < 1e-4) return;
        int lv = Math.max(pixelX, Math.round(pixelY * skyLight));   // 밤에는 하늘빛이 약하므로 실제 밝기 기준
        colors.mul(curve(amb, lv) / curve(AMBIENT_HIGH, lv));
    }

}
