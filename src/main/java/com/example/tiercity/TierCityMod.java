package com.example.tiercity;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(TierCityMod.MODID)
public class TierCityMod {
    public static final String MODID = "tiercity";

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);

    /** 인공 하늘 패널: 항상 최대 밝기, 빛은 내지 않음(조명 계산 부담 없음), 부수면 아무것도 안 나옴. */
    public static final RegistryObject<Block> SKY_PANEL = BLOCKS.register("sky_panel", () -> new SkyPanelBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.GLASS)
                    .randomTicks()
                    .emissiveRendering((state, level, pos) -> true)));

    static {
        CHUNK_GENERATORS.register("layered_city", () -> LayeredCityChunkGenerator.CODEC);
    }

    public TierCityMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        CHUNK_GENERATORS.register(bus);
        BLOCKS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TierCityConfig.SPEC);
    }
}
