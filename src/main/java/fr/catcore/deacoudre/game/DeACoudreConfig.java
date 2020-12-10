package fr.catcore.deacoudre.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.catcore.deacoudre.game.map.DeACoudreMapConfig;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public class DeACoudreConfig {

    public static final Codec<DeACoudreConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                DeACoudreMapConfig.CODEC.fieldOf("map").forGetter(config -> config.mapConfig),
                PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.playerConfig),
                Codec.INT.fieldOf("life").forGetter(config -> config.life)
        ).apply(instance, DeACoudreConfig::new);
    });

    public static final BlockState[] PLAYER_PALETTE;

    public final DeACoudreMapConfig mapConfig;
    public final PlayerConfig playerConfig;
    public final int life;

    public DeACoudreConfig(
            DeACoudreMapConfig mapConfig,
            PlayerConfig playerConfig,
            int life
    ) {
        this.mapConfig = mapConfig;
        this.playerConfig = playerConfig;
        this.life = life;
    }

    static {
        PLAYER_PALETTE = new BlockState[]{
                Blocks.BLACK_WOOL.getDefaultState(),
                Blocks.BROWN_WOOL.getDefaultState(),
                Blocks.GRAY_WOOL.getDefaultState(),
                Blocks.GREEN_WOOL.getDefaultState(),
                Blocks.LIGHT_GRAY_WOOL.getDefaultState(),
                Blocks.LIME_WOOL.getDefaultState(),
                Blocks.MAGENTA_WOOL.getDefaultState(),
                Blocks.ORANGE_WOOL.getDefaultState(),
                Blocks.PINK_WOOL.getDefaultState(),
                Blocks.PURPLE_WOOL.getDefaultState(),
                Blocks.RED_WOOL.getDefaultState(),
                Blocks.WHITE_WOOL.getDefaultState(),
                Blocks.YELLOW_WOOL.getDefaultState(),
                Blocks.TERRACOTTA.getDefaultState(),
                Blocks.BLACK_TERRACOTTA.getDefaultState(),
                Blocks.BROWN_TERRACOTTA.getDefaultState(),
                Blocks.GRAY_TERRACOTTA.getDefaultState(),
                Blocks.GREEN_TERRACOTTA.getDefaultState(),
                Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState(),
                Blocks.LIME_TERRACOTTA.getDefaultState(),
                Blocks.MAGENTA_TERRACOTTA.getDefaultState(),
                Blocks.ORANGE_TERRACOTTA.getDefaultState(),
                Blocks.PINK_TERRACOTTA.getDefaultState(),
                Blocks.PURPLE_TERRACOTTA.getDefaultState(),
                Blocks.RED_TERRACOTTA.getDefaultState(),
                Blocks.WHITE_TERRACOTTA.getDefaultState(),
                Blocks.YELLOW_TERRACOTTA.getDefaultState(),
                Blocks.GLASS.getDefaultState(),
                Blocks.BLACK_STAINED_GLASS.getDefaultState(),
                Blocks.BROWN_STAINED_GLASS.getDefaultState(),
                Blocks.GRAY_STAINED_GLASS.getDefaultState(),
                Blocks.GREEN_STAINED_GLASS.getDefaultState(),
                Blocks.LIGHT_GRAY_STAINED_GLASS.getDefaultState(),
                Blocks.LIME_STAINED_GLASS.getDefaultState(),
                Blocks.MAGENTA_STAINED_GLASS.getDefaultState(),
                Blocks.ORANGE_STAINED_GLASS.getDefaultState(),
                Blocks.PINK_STAINED_GLASS.getDefaultState(),
                Blocks.PURPLE_STAINED_GLASS.getDefaultState(),
                Blocks.RED_STAINED_GLASS.getDefaultState(),
                Blocks.WHITE_STAINED_GLASS.getDefaultState(),
                Blocks.YELLOW_STAINED_GLASS.getDefaultState(),
                Blocks.BLACK_CONCRETE.getDefaultState(),
                Blocks.BROWN_CONCRETE.getDefaultState(),
                Blocks.GRAY_CONCRETE.getDefaultState(),
                Blocks.GREEN_CONCRETE.getDefaultState(),
                Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),
                Blocks.LIME_CONCRETE.getDefaultState(),
                Blocks.MAGENTA_CONCRETE.getDefaultState(),
                Blocks.ORANGE_CONCRETE.getDefaultState(),
                Blocks.PINK_CONCRETE.getDefaultState(),
                Blocks.PURPLE_CONCRETE.getDefaultState(),
                Blocks.RED_CONCRETE.getDefaultState(),
                Blocks.WHITE_CONCRETE.getDefaultState(),
                Blocks.YELLOW_CONCRETE.getDefaultState(),
                Blocks.BLACK_CONCRETE_POWDER.getDefaultState(),
                Blocks.BROWN_CONCRETE_POWDER.getDefaultState(),
                Blocks.GRAY_CONCRETE_POWDER.getDefaultState(),
                Blocks.GREEN_CONCRETE_POWDER.getDefaultState(),
                Blocks.LIGHT_GRAY_CONCRETE_POWDER.getDefaultState(),
                Blocks.LIME_CONCRETE_POWDER.getDefaultState(),
                Blocks.MAGENTA_CONCRETE_POWDER.getDefaultState(),
                Blocks.ORANGE_CONCRETE_POWDER.getDefaultState(),
                Blocks.PINK_CONCRETE_POWDER.getDefaultState(),
                Blocks.PURPLE_CONCRETE_POWDER.getDefaultState(),
                Blocks.RED_CONCRETE_POWDER.getDefaultState(),
                Blocks.WHITE_CONCRETE_POWDER.getDefaultState(),
                Blocks.YELLOW_CONCRETE_POWDER.getDefaultState()
        };
    }
}
