package fr.catcore.deacoudre.game.map;

import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.world.generator.GameChunkGenerator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DeACoudreChunkGenerator extends GameChunkGenerator {

    private final ChunkGenerator chunkGenerator;
    private final DeACoudreMap map;

    private int averageY = 70;
    private BlockPos cornerRight = null;
    private BlockPos cornerLeft = null;

    protected DeACoudreChunkGenerator(MinecraftServer server, long seed, DeACoudreMap map) {
        super(server);

        this.chunkGenerator = GeneratorOptions.createGenerator(server.getRegistryManager(), seed, ChunkGeneratorSettings.OVERWORLD);
        this.map = map;
    }

    @Override
    public MultiNoiseUtil.MultiNoiseSampler getMultiNoiseSampler() {
        return this.chunkGenerator.getMultiNoiseSampler();
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver generationStep) {
        this.chunkGenerator.carve(chunkRegion, seed, biomeAccess, structureAccessor, chunk, generationStep);
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, Chunk chunk) {
        this.chunkGenerator.buildSurface(region, structures, chunk);
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, StructureAccessor structureAccessor, Chunk chunk) {
        return this.chunkGenerator.populateNoise(executor, blender, structureAccessor, chunk);
    }

    @Override
    public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
        this.chunkGenerator.generateFeatures(world, chunk, structureAccessor);

        var chunkPos = chunk.getPos();

        if (chunkPos.x == 0 && chunkPos.z == 0) {
            generateSpawnPlatform(world, chunk);
        } else generatePool(world, chunk);
    }

    private void generatePool(StructureWorldAccess world, Chunk chunk) {
        var config = this.map.getConfig();

        if (this.cornerLeft != null && this.cornerRight != null) {
            if (this.map.getPool() == null) {
                this.map.setPool(DeACoudreMapConfig.MapShape.valueOf(config.shape()).generatePool(
                        config,
                        world,
                        cornerRight,
                        cornerLeft,
                        averageY - 1
                ));
            } else {
                DeACoudreMapConfig.MapShape.valueOf(config.shape()).generatePool(
                        config,
                        world,
                        cornerRight,
                        cornerLeft,
                        averageY - 1
                );
            }
        }
    }

    private void generateSpawnPlatform(StructureWorldAccess world, Chunk chunk) {
        var startPos = chunk.getPos().getStartPos();

        int y = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                y += this.getHeight(startPos.getX() + x, startPos.getZ() + z, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, world);
            }
        }

        this.averageY = y / (16*16);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                world.setBlockState(new BlockPos(x, averageY - 1, z), Blocks.SPRUCE_PLANKS.getDefaultState(), 0);

                for (int airY = averageY; airY < 300; airY++) {
                    world.setBlockState(new BlockPos(x, airY, z), Blocks.AIR.getDefaultState(), 0);
                }
            }
        }

        var config = this.map.getConfig();
        var radius = config.radius();

        var centerZ = ChunkSectionPos.getOffsetPos(0, 8);
        var bottomX = ChunkSectionPos.getBlockCoord(3);
        var centerBottomPos = new BlockPos(bottomX, 0, centerZ);

        this.cornerRight = centerBottomPos.add(0, 0, radius);
        this.cornerLeft = centerBottomPos.add(radius*2 + 1, 0, -radius);
    }

    @Override
    public int getSeaLevel() {
        return this.chunkGenerator.getSeaLevel();
    }

    @Override
    public int getMinimumY() {
        return this.chunkGenerator.getMinimumY();
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        this.chunkGenerator.populateEntities(region);
    }

    @Override
    public int getWorldHeight() {
        return this.chunkGenerator.getWorldHeight();
    }

    @Override
    public int getSpawnHeight(HeightLimitView world) {
        return this.chunkGenerator.getSpawnHeight(world);
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world) {
        return this.chunkGenerator.getHeight(x, z, heightmap, world);
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world) {
        return this.chunkGenerator.getColumnSample(x, z, world);
    }

    @Override
    public CompletableFuture<Chunk> populateBiomes(Registry<Biome> biomeRegistry, Executor executor, Blender blender, StructureAccessor structureAccessor, Chunk chunk) {
        return this.chunkGenerator.populateBiomes(biomeRegistry, executor, blender, structureAccessor, chunk);
    }

    @Override
    public BiomeSource getBiomeSource() {
        return this.chunkGenerator.getBiomeSource();
    }
}
