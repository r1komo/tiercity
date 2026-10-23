package com.example.tiercity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
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

    public LayeredCityChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int wx = minX + lx, wz = minZ + lz;
                    CityLayout.Column col = CityLayout.column(wx, wz);
                    if (col.deckTop() < 0) continue;
                    int top = col.topSolid();
                    for (int y = col.bottomSolid(); y <= top; y++) {
                        BlockState s = CityLayout.block(col, wx, y, wz);
                        if (!s.isAir()) {
                            chunk.setBlockState(pos.set(wx, y, wz), s, false);
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
        return CityLayout.column(x, z).topSolid() + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        CityLayout.Column col = CityLayout.column(x, z);
        BlockState[] states = new BlockState[CityLayout.HEIGHT];
        for (int i = 0; i < states.length; i++) {
            states[i] = CityLayout.block(col, x, CityLayout.MIN_Y + i, z);
        }
        return new NoiseColumn(CityLayout.MIN_Y, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("TierCity cell=" + Math.floorDiv(pos.getX(), CityLayout.PERIOD) + "," + Math.floorDiv(pos.getZ(), CityLayout.PERIOD));
    }
}
