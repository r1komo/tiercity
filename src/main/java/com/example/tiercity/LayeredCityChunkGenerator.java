package com.example.tiercity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LayeredCityChunkGenerator extends ChunkGenerator {
    public static final Codec<LayeredCityChunkGenerator> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
                    .apply(inst, inst.stable(LayeredCityChunkGenerator::new)));

    private static final ResourceLocation SEED_KEY = new ResourceLocation(TierCityMod.MODID, "layout");

    public LayeredCityChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** 월드 시드에서 파생한 레이아웃 시드. 같은 월드 시드이면 항상 같은 값. */
    public static long layoutSeed(RandomState randomState) {
        return randomState.getOrCreateRandomFactory(SEED_KEY).at(0, 0, 0).nextLong();
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        final long seed = layoutSeed(randomState);
        final CityLayout.Params params = TierCityConfig.params();
        return CompletableFuture.supplyAsync(() -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int wx = minX + lx, wz = minZ + lz;
                    CityLayout.Column col = CityLayout.column(params, seed, wx, wz);
                    if (col.isEmpty()) continue;
                    // 판은 층별로, 기둥은 그 위에 덮어쓴다(기둥이 아래층 판을 관통)
                    for (CityLayout.Layer layer : col.layers()) {
                        if (layer == null) continue;
                        for (int y = layer.bottom(); y <= layer.maxY(); y++) {
                            BlockState s = CityLayout.deckBlock(seed, layer, wx, y, wz);
                            if (!s.isAir()) {
                                chunk.setBlockState(pos.set(wx, y, wz), s, false);
                            }
                        }
                    }
                    if (col.hasPillar()) {
                        int topY = Math.min(CityLayout.MIN_Y + CityLayout.HEIGHT - 1, Math.max(col.pillarTop(), col.rebarTop()));
                        for (int y = CityLayout.MIN_Y; y <= topY; y++) {
                            BlockState s = CityLayout.pillarBlock(seed, col, wx, y, wz);
                            if (!s.isAir()) {
                                chunk.setBlockState(pos.set(wx, y, wz), s, false);
                            }
                        }
                    }
                }
            }
            return chunk;
        }, Util.backgroundExecutor());
    }

    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) { }

    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) { }

    @Override public void spawnOriginalMobs(WorldGenRegion region) { }

    @Override public int getGenDepth() { return CityLayout.HEIGHT; }

    @Override public int getSeaLevel() { return CityLayout.MIN_Y; }

    @Override public int getMinY() { return CityLayout.MIN_Y; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return CityLayout.column(TierCityConfig.params(), layoutSeed(random), x, z).topSolid() + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        long seed = layoutSeed(random);
        CityLayout.Column col = CityLayout.column(TierCityConfig.params(), seed, x, z);
        BlockState[] states = new BlockState[CityLayout.HEIGHT];
        for (int i = 0; i < states.length; i++) {
            states[i] = CityLayout.block(seed, col, x, CityLayout.MIN_Y + i, z);
        }
        return new NoiseColumn(CityLayout.MIN_Y, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        CityLayout.Params p = TierCityConfig.params();
        info.add("TierCity radius=" + p.radius() + " layers=" + p.layers());
    }
}
