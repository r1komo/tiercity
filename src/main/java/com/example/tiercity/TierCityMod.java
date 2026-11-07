package com.example.tiercity;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
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
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);

    /** 손전등(우클릭으로 켜고 끔). */
    public static final RegistryObject<Item> FLASHLIGHT = ITEMS.register("flashlight", () -> new FlashlightItem(new Item.Properties().stacksTo(1)));

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
    public static final RegistryObject<Block> STATUE_HEAD_RIGHT = BLOCKS.register("statue_head_right", () -> new StatueFaceBlock(statueProps().noOcclusion()));
    /** 머리 바로 아래 몸통(뒷면이 비스듬히 깎임): 1x1용, 2x2 뒷줄용. */
    public static final RegistryObject<Block> STATUE_NECK = BLOCKS.register("statue_neck", () -> new StatueFaceBlock(statueProps().noOcclusion()));
    public static final RegistryObject<Block> STATUE_NECK_BACK = BLOCKS.register("statue_neck_back", () -> new StatueFaceBlock(statueProps().noOcclusion()));

    /** 거리 설비: 전봇대, 완목, 전선, 가는 배관 두 굵기. */
    public static final RegistryObject<Block> UTILITY_POLE = BLOCKS.register("utility_pole", () -> new FixtureBlocks.Pole(fixtureProps(MapColor.STONE).noOcclusion()));
    public static final RegistryObject<Block> POLE_CROSSARM = BLOCKS.register("pole_crossarm", () -> new FixtureBlocks.Crossarm(fixtureProps(MapColor.STONE).noOcclusion()));
    public static final RegistryObject<Block> POWER_LINE = BLOCKS.register("power_line", () -> new FixtureBlocks.Wire(fixtureProps(MapColor.COLOR_BLACK).noOcclusion().noCollission().instabreak()));
    public static final RegistryObject<Block> PIPE_SMALL = BLOCKS.register("pipe_small", () -> new FixtureBlocks.Pipe(fixtureProps(MapColor.METAL).noOcclusion(), 5));
    public static final RegistryObject<Block> PIPE_MEDIUM = BLOCKS.register("pipe_medium", () -> new FixtureBlocks.Pipe(fixtureProps(MapColor.METAL).noOcclusion(), 9));

    private static BlockBehaviour.Properties fixtureProps(MapColor c) {
        return BlockBehaviour.Properties.of().mapColor(c).strength(1.5F, 6.0F).sound(SoundType.METAL);
    }

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

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(FLASHLIGHT);
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
        Landmarks.statueHeadR = faced(STATUE_HEAD_RIGHT.get(), dirs);
        Landmarks.statueNeck = faced(STATUE_NECK.get(), dirs);
        Landmarks.statueNeckBack = faced(STATUE_NECK_BACK.get(), dirs);
        Block[] kois = {KOI_ORANGE.get(), KOI_WHITE.get(), KOI_KOHAKU.get()};
        BlockState[][] ks = new BlockState[3][];
        for (int c = 0; c < 3; c++) {
            ks[c] = faced(kois[c], dirs);
            for (int i = 0; i < 4; i++) ks[c][i] = ks[c][i].setValue(BlockStateProperties.WATERLOGGED, true);
        }
        Landmarks.koiStates = ks;
        // 거리 설비
        Fixtures.pole = UTILITY_POLE.get().defaultBlockState();
        Fixtures.crossarm = new BlockState[]{
                POLE_CROSSARM.get().defaultBlockState().setValue(FixtureBlocks.Crossarm.AXIS, Direction.Axis.X),
                POLE_CROSSARM.get().defaultBlockState().setValue(FixtureBlocks.Crossarm.AXIS, Direction.Axis.Z)};
        BlockState[][] w = new BlockState[2][Fixtures.SAG_LEVELS];
        for (int a = 0; a < 2; a++) for (int s = 0; s < Fixtures.SAG_LEVELS; s++) {
            w[a][s] = POWER_LINE.get().defaultBlockState().setValue(FixtureBlocks.Wire.AXIS, a == 0 ? Direction.Axis.X : Direction.Axis.Z)
                    .setValue(FixtureBlocks.Wire.SAG, s);
        }
        Fixtures.wire = w;
        Direction.Axis[] ax3 = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
        BlockState[] ps = new BlockState[3], pm = new BlockState[3];
        for (int i = 0; i < 3; i++) {
            ps[i] = PIPE_SMALL.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, ax3[i]);
            pm[i] = PIPE_MEDIUM.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, ax3[i]);
        }
        Fixtures.pipeS = ps;
        Fixtures.pipeM = pm;
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
        ITEMS.register(bus);
        bus.addListener(TierCityMod::addCreative);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TierCityConfig.SPEC);
    }
}
