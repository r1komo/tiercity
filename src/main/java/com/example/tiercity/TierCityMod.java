package com.example.tiercity;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * 인공 하늘 패널: 아랫면이 화면에서 진짜 하늘로 그려진다(client/SkyWindowRenderer).
     * 그 렌더링이 꺼진 환경(셰이더 팩 등)에서는 블록 자체의 하늘색 아랫면이 보인다. 부수면 아무것도 안 나옴.
     */
    public static final RegistryObject<Block> SKY_PANEL = BLOCKS.register("sky_panel", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)));

    /** 하얀 석상 몸통(점무늬 돌). */
    public static final RegistryObject<Block> STATUE_STONE = BLOCKS.register("statue_stone", () -> new Block(statueProps()));
    /** 1x1 석상의 얼굴(둥근 머리, 두 눈과 작은 입). */
    public static final RegistryObject<Block> STATUE_FACE = BLOCKS.register("statue_face", () -> new StatueFaceBlock(statueProps().noOcclusion()));
    /** 2x2 석상: 윗줄 왼눈/오른눈(둥근 머리 바깥쪽), 아랫줄 입 왼쪽/오른쪽 절반, 뒷줄 둥근 머리. */
    public static final RegistryObject<Block> STATUE_EYE_LEFT = BLOCKS.register("statue_eye_left", () -> new StatueFaceBlock(statueProps().noOcclusion()));
    public static final RegistryObject<Block> STATUE_EYE_RIGHT = BLOCKS.register("statue_eye_right", () -> new StatueFaceBlock(statueProps().noOcclusion()));
    public static final RegistryObject<Block> STATUE_MOUTH_LEFT = BLOCKS.register("statue_mouth_left", () -> new StatueFaceBlock(statueProps()));
    public static final RegistryObject<Block> STATUE_MOUTH_RIGHT = BLOCKS.register("statue_mouth_right", () -> new StatueFaceBlock(statueProps()));
    public static final RegistryObject<Block> STATUE_HEAD = BLOCKS.register("statue_head", () -> new StatueFaceBlock(statueProps().noOcclusion()));

    /** 가짜 잉어 3색(주황, 흰색, 홍백). */
    public static final RegistryObject<Block> KOI_ORANGE = BLOCKS.register("koi_orange", () -> new KoiBlock(koiProps()));
    public static final RegistryObject<Block> KOI_WHITE = BLOCKS.register("koi_white", () -> new KoiBlock(koiProps()));
    public static final RegistryObject<Block> KOI_KOHAKU = BLOCKS.register("koi_kohaku", () -> new KoiBlock(koiProps()));

    private static BlockBehaviour.Properties koiProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.METAL).noOcclusion();
    }

    private static BlockBehaviour.Properties statueProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F, 6.0F).sound(SoundType.CALCITE);
    }

    /** 석상 블록 상태를 생성 코드(Landmarks)에 넣는다. 방향 번호: 0 남, 1 북, 2 동, 3 서. */
    static synchronized void injectStatueBlocks() {
        if (Landmarks.statueFace != null) return;
        Direction[] dirs = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
        Landmarks.statueStone = STATUE_STONE.get().defaultBlockState();
        Landmarks.statueEyeL = faced(STATUE_EYE_LEFT.get(), dirs);
        Landmarks.statueEyeR = faced(STATUE_EYE_RIGHT.get(), dirs);
        Landmarks.statueMouthL = faced(STATUE_MOUTH_LEFT.get(), dirs);
        Landmarks.statueMouthR = faced(STATUE_MOUTH_RIGHT.get(), dirs);
        Landmarks.statueHead = faced(STATUE_HEAD.get(), dirs);
        Block[] kois = {KOI_ORANGE.get(), KOI_WHITE.get(), KOI_KOHAKU.get()};
        BlockState[][] ks = new BlockState[3][];
        for (int c = 0; c < 3; c++) {
            ks[c] = faced(kois[c], dirs);
            for (int i = 0; i < 4; i++) ks[c][i] = ks[c][i].setValue(BlockStateProperties.WATERLOGGED, true);
        }
        Landmarks.koiStates = ks;
        Landmarks.metalPlant = Blocks.IRON_BARS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        Landmarks.statueFace = faced(STATUE_FACE.get(), dirs);
    }

    private static BlockState[] faced(Block b, Direction[] dirs) {
        BlockState[] out = new BlockState[4];
        for (int i = 0; i < 4; i++) out[i] = b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dirs[i]);
        return out;
    }

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
