package com.example.tiercity.client;

import com.example.tiercity.TierCityMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/** 하늘 창 셰이더 등록(클라이언트 전용). */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SkyWindowShaders {
    public static ShaderInstance skyWindow;

    private SkyWindowShaders() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(TierCityMod.MODID, "sky_window"), DefaultVertexFormat.POSITION),
                shader -> skyWindow = shader);
    }
}
