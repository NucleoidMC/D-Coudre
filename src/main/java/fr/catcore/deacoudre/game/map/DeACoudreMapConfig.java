package fr.catcore.deacoudre.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

import java.util.function.Consumer;

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
        square((config, setWater, setBorder) -> {
            var pos = new BlockPos.Mutable();
            for (int z = 5; z <= 5 + (2 * config.radius); z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    pos.set(x, 1, z);
                    setBorder.accept(pos);
                }
            }
            for (int z = 5; z <= 5 + (2 * config.radius); z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    pos.set(x, 2, z);
                    if (z == 5 || z == 5 + (2 * config.radius) || x == -config.radius || x == config.radius) {
                        setBorder.accept(pos);
                    } else {
                        setWater.accept(pos);
                    }
                }
            }
        }),
        circle((config, setWater, setBorder) -> {
            var pos = new BlockPos.Mutable();
            int radius2 = config.radius * config.radius;
            int outlineRadius2 = (config.radius - 1) * (config.radius - 1);
            for (int z = -config.radius; z <= config.radius; z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    int distance2 = x * x + z * z;

                    pos.set(x, 1, getRightZ(config, z));
                    setBorder.accept(pos);

                    pos.move(Direction.UP);

                    if (distance2 <= outlineRadius2) {
                        setWater.accept(pos);
                    } else {
                        setBorder.accept(pos);
                    }
                }
            }
        }),
        donut((config, setWater, setBorder) -> {
            var pos = new BlockPos.Mutable();
            int radius2 = config.radius * config.radius;
            int outlineRadius2 = (config.radius - 1) * (config.radius - 1);
            int inlineRadius = (config.inCircleRadius - 1) * (config.inCircleRadius - 1);
            for (int z = -config.radius; z <= config.radius; z++) {
                for (int x = -config.radius; x <= config.radius; x++) {
                    int distance2 = x * x + z * z;

                    pos.set(x, 1, getRightZ(config, z));
                    setBorder.accept(pos);

                    pos.move(Direction.UP);

                    if (distance2 <= outlineRadius2 && distance2 > inlineRadius) {
                        setWater.accept(pos);
                    } else {
                        setBorder.accept(pos);
                    }
                }
            }
        });

        private final PoolGenerator generator;

        MapShape(PoolGenerator generator) {
            this.generator = generator;
        }

        public BlockBounds generatePool(DeACoudreMapConfig config, MapTemplate builder) {
            var setWater = new BlockBoundsBuilder(pos -> builder.setBlockState(pos, Blocks.WATER.getDefaultState()));
            Consumer<BlockPos> setBorder = pos -> builder.setBlockState(pos, config.poolOutlineBlock);

            this.generator.generatePool(config, setWater, setBorder);

            return setWater.getBounds();
        }

        private static int getRightZ(DeACoudreMapConfig config, int z) {
            return 5 + (z - -config.radius);
        }

        private interface PoolGenerator {
            void generatePool(DeACoudreMapConfig config, Consumer<BlockPos> setWater, Consumer<BlockPos> setBorder);
        }
    }
}
