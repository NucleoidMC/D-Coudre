package fr.catcore.deacoudre.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public record DeACoudreMapConfig(int radius, int height, String shape, int inCircleRadius,
                                 BlockState spawnBlock,
                                 BlockState poolOutlineBlock,
                                 BlockState jumpPlatformBlock) {

    public static final Codec<DeACoudreMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.INT.fieldOf("radius").forGetter(map -> map.radius),
                Codec.INT.fieldOf("height").forGetter(map -> map.height),
                Codec.STRING.fieldOf("shape").forGetter(map -> map.shape),
                Codec.INT.optionalFieldOf("in_circle_radius", 3).forGetter(map -> map.inCircleRadius),
                BlockState.CODEC.fieldOf("spawn_block").forGetter(map -> map.spawnBlock),
                BlockState.CODEC.fieldOf("pool_outline_block").forGetter(map -> map.poolOutlineBlock),
                BlockState.CODEC.fieldOf("jump_platform_block").forGetter(map -> map.jumpPlatformBlock)
        ).apply(instance, DeACoudreMapConfig::new);
    });

    public DeACoudreMapConfig(int radius, int height, String shape, int inCircleRadius, BlockState spawnBlock, BlockState poolOutlineBlock, BlockState jumpPlatformBlock) {
        this.height = height + 1;
        this.radius = radius;
        this.shape = shape;
        this.inCircleRadius = inCircleRadius;
        this.spawnBlock = spawnBlock;
        this.poolOutlineBlock = poolOutlineBlock;
        this.jumpPlatformBlock = jumpPlatformBlock;
    }

    public enum MapShape {
        square((config, builder, mutablePosWater, mutablePosBorder, cornerRight, cornerLeft, y) -> {
            BlockBounds blockBounds = null;
            var bounds = BlockBounds.of(cornerRight, cornerLeft);

            for (var pos : bounds) {
                mutablePosBorder.set(pos.getX(), y - 1, pos.getZ());
                builder.setBlockState(mutablePosBorder, config.poolOutlineBlock, 0);
            }

            for (int z = 5; z <= 5 + (2 * config.radius); z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    mutablePosBorder.set(x, y, z);
                    mutablePosWater.set(x, y, z);
                    if (z == 5 || z == 5 + (2 * config.radius) || x == -config.radius || x == config.radius)
                        builder.setBlockState(mutablePosBorder, config.poolOutlineBlock, 0);
                    else {
                        builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState(), 0);
                        blockBounds = blockBounds != null ?
                                blockBounds.union(BlockBounds.ofBlock(mutablePosWater))
                                : BlockBounds.ofBlock(mutablePosWater);
                    }
                }
            }

            return blockBounds;
        }),
        circle((config, builder, mutablePosWater, mutablePosBorder, cornerRight, cornerLeft, y) -> {
            BlockBounds blockBounds = null;
            int radius2 = config.radius * config.radius;
            int outlineRadius2 = (config.radius - 1) * (config.radius - 1);
            for (int z = -config.radius; z <= config.radius; z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    int distance2 = x * x + z * z;

                    mutablePosBorder.set(x, y, getRightZ(config, z));
                    builder.setBlockState(mutablePosBorder, config.poolOutlineBlock, 0);

                    if (distance2 <= outlineRadius2) {
                        mutablePosWater.set(x, y, getRightZ(config, z));
                        builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState(), 0);
                        blockBounds = blockBounds != null ?
                                blockBounds.union(BlockBounds.ofBlock(mutablePosWater))
                                : BlockBounds.ofBlock(mutablePosWater);
                    } else {
                        mutablePosBorder.set(x, y, getRightZ(config, z));
                        builder.setBlockState(mutablePosBorder, config.poolOutlineBlock, 0);
                    }
                }
            }

            return blockBounds;
        }),
        donut((config, builder, mutablePosWater, mutablePosBorder, cornerRight, cornerLeft, y) -> {
            BlockBounds blockBounds = null;
            int radius2 = config.radius * config.radius;
            int outlineRadius2 = (config.radius - 1) * (config.radius - 1);
            int inlineRadius = (config.inCircleRadius - 1) * (config.inCircleRadius - 1);
            for (int z = -config.radius; z <= config.radius; z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    int distance2 = x * x + z * z;

                    mutablePosBorder.set(x, y, getRightZ(config, z));
                    builder.setBlockState(mutablePosBorder, config.poolOutlineBlock, 0);

                    if (distance2 <= outlineRadius2 && distance2 > inlineRadius) {
                        mutablePosWater.set(x, y, getRightZ(config, z));
                        builder.setBlockState(mutablePosWater, Blocks.WATER.getDefaultState(), 0);
                        blockBounds = blockBounds != null ?
                                blockBounds.union(BlockBounds.ofBlock(mutablePosWater))
                                : BlockBounds.ofBlock(mutablePosWater);
                    } else {
                        mutablePosBorder.set(x, y, getRightZ(config, z));
                        builder.setBlockState(mutablePosBorder, config.poolOutlineBlock, 0);
                    }
                }
            }

            return blockBounds;
        });

        private final GeneratePool generatePool;

        MapShape(GeneratePool generatePool) {
            this.generatePool = generatePool;
        }

        public BlockBounds generatePool(DeACoudreMapConfig config, StructureWorldAccess world, BlockPos cornerRight, BlockPos cornerLeft, int y) {
            BlockPos.Mutable mutablePosWater = new BlockPos.Mutable();
            BlockPos.Mutable mutablePosBorder = new BlockPos.Mutable();
            return this.generatePool.generatePool(config, world, mutablePosWater, mutablePosBorder, cornerRight, cornerLeft,  y);
        }

        private static int getRightZ(DeACoudreMapConfig config, int z) {
            return 5 + (z - -config.radius);
        }

        private interface GeneratePool {

            BlockBounds generatePool(DeACoudreMapConfig config, StructureWorldAccess world, BlockPos.Mutable mutablePosWater, BlockPos.Mutable mutablePosBorder, BlockPos cornerRight, BlockPos cornerLeft, int y);
        }
    }
}
