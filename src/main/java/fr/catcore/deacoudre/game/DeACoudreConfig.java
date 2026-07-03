package fr.catcore.deacoudre.game;

import com.google.common.collect.Streams;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.catcore.deacoudre.game.map.DeACoudreMapConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;

import java.util.stream.Stream;

public record DeACoudreConfig(
        Either<DeACoudreMapConfig, Identifier> map,
        WaitingLobbyConfig playerConfig, int life, boolean concurrent) {

    public static final MapCodec<DeACoudreConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        return instance.group(
                Codec.either(DeACoudreMapConfig.CODEC, Identifier.CODEC).fieldOf("map").forGetter(config -> config.map),
                WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(config -> config.playerConfig),
                Codec.INT.optionalFieldOf("life", 3).forGetter(config -> config.life),
                Codec.BOOL.optionalFieldOf("concurrent", false).forGetter(config -> config.concurrent)
        ).apply(instance, DeACoudreConfig::new);
    });

    public static final BlockState[] PLAYER_PALETTE;

    static {
        PLAYER_PALETTE = Streams.concat(Blocks.WOOL.asList().stream(),
                Stream.of(Blocks.TERRACOTTA),
                Blocks.DYED_TERRACOTTA.asList().stream(),
                Stream.of(Blocks.GLASS),
                Blocks.STAINED_GLASS.asList().stream(),
                Blocks.CONCRETE.asList().stream(),
                Blocks.CONCRETE_POWDER.asList().stream()
                ).map(Block::defaultBlockState).toArray(BlockState[]::new);
    }
}
