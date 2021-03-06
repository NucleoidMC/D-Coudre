package fr.catcore.deacoudre.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.map.template.MapTemplateMetadata;
import xyz.nucleoid.plasmid.map.template.TemplateChunkGenerator;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.util.stream.Collectors;

public class DeACoudreMap {
    private final MapTemplate template;
    private BlockPos spawn = BlockPos.ORIGIN;

    private BlockBounds pool = BlockBounds.EMPTY;
    private BlockBounds jumpingPlatform = BlockBounds.EMPTY;
    private BlockBounds jumpingArea = BlockBounds.EMPTY;

    public DeACoudreMap(MapTemplate template) {
        this.template = template;
    }

    public static DeACoudreMap fromTemplate(MapTemplate template) {
        DeACoudreMap map = new DeACoudreMap(template);

        MapTemplateMetadata templateMetadata = template.getMetadata();

        BlockBounds poolBounds = templateMetadata.getFirstRegionBounds("pool");
        map.setPool(poolBounds);

        BlockBounds jumpingPlatform = templateMetadata.getFirstRegionBounds("jumping_platform");
        map.setJumpingPlatform(jumpingPlatform);

        BlockBounds jumpingArea = templateMetadata.getFirstRegionBounds("jumping_area");
        map.setJumpingArea(jumpingArea);

        Vec3d spawn = templateMetadata.getFirstRegionBounds("spawn").getCenter();

        map.setSpawn(new BlockPos(spawn));

        return map;
    }

    public void setSpawn(BlockPos spawn) {
        this.spawn = spawn;
    }

    public void setPool(BlockBounds pool) {
        this.pool = pool;
    }

    public void setJumpingPlatform(BlockBounds platform) {
        this.jumpingPlatform = platform;
    }

    public void setJumpingArea(BlockBounds area) {
        this.jumpingArea = area;
    }

    public BlockPos getSpawn() {
        return spawn;
    }

    public BlockBounds getPool() {
        return this.pool;
    }

    public BlockBounds getJumpingPlatform() {
        return this.jumpingPlatform;
    }

    public BlockBounds getJumpingArea() {
        return this.jumpingArea;
    }

    public MapTemplate getTemplate() {
        return template;
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }
}
