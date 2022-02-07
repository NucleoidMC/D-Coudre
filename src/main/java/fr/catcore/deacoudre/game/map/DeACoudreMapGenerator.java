package fr.catcore.deacoudre.game.map;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public class DeACoudreMapGenerator {
    private static final BlockPos[] PLATFORM_BARRIER = new BlockPos[] {
            new BlockPos(-2, 1, 1),
            new BlockPos(-2, 1, 2),
            new BlockPos(-2, 1, 3),
            new BlockPos(-2, 1, 4),
            new BlockPos(-1, 1, 4),
            new BlockPos(0, 1, 4),
            new BlockPos(1, 1, 4),
            new BlockPos(2, 1, 4),
            new BlockPos(2, 1, 3),
            new BlockPos(2, 1, 2),
            new BlockPos(2, 1, 1)
    };

    private final DeACoudreMapConfig config;
    private final DeACoudreMapConfig.MapShape shape;

    public DeACoudreMapGenerator(DeACoudreMapConfig config) {
        this.config = config;
        this.shape = DeACoudreMapConfig.MapShape.valueOf(config.shape());
    }

    public DeACoudreMap build() {
        MapTemplate template = MapTemplate.createEmpty();
        DeACoudreMap map = new DeACoudreMap(template, config);

        this.buildSpawn(template);
        this.buildPool(map, template);
        this.buildSequentialJumpingPlatform(map, template);

        map.setSpawn(new BlockPos(0, 3, 0));

        return map;
    }

    private void buildSpawn(MapTemplate builder) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                mutable.set(x, 2, z);
                builder.setBlockState(mutable, this.config.spawnBlock());
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 3; y <= 6; y++) {
                    if (x == -4 || x == 4 || z == -4 || z == 4) {
                        mutable.set(x, y, z);
                        builder.setBlockState(mutable, Blocks.BARRIER.getDefaultState());
                    }
                    if (y == 6) {
                        mutable.set(x, y, z);
                        builder.setBlockState(mutable, Blocks.BARRIER.getDefaultState());
                    }
                }
            }
        }
    }

    private void buildPool(DeACoudreMap map, MapTemplate builder) {
//        map.setPool(this.shape.generatePool(this.config, builder));
    }

    private void buildSequentialJumpingPlatform(DeACoudreMap map, MapTemplate builder) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable barrierPos = new BlockPos.Mutable();
        int minZ = 5 + (2 * this.config.radius()) + 1;
        int minY = this.config.height();

        for (int z = minZ; z <= minZ + 3; z++) {
            for (int x = -1; x <= 1; x++) {
                mutable.set(x, minY + 1, z);
                builder.setBlockState(mutable, this.config.jumpPlatformBlock());
            }
        }

        for (BlockPos pos : PLATFORM_BARRIER) {
            for (int y = 0; y < 3; y++) {
                barrierPos.set(pos.getX(), pos.getY() + minY + y, minZ + pos.getZ());
                builder.setBlockState(barrierPos, Blocks.BARRIER.getDefaultState());
            }
        }

        map.setJumpingPlatform(BlockBounds.of(
                new BlockPos(-1, minY + 2, minZ - 1),
                new BlockPos(1, minY + 2, minZ + 3)
        ));
        map.setJumpingArea(BlockBounds.of(
                new BlockPos(-2, minY, minZ - 1),
                new BlockPos(2, minY, minZ + 4)
        ));
    }
}
