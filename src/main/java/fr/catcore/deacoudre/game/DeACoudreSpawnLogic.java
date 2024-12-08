package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.api.game.GameSpace;

import java.util.Set;

public record DeACoudreSpawnLogic(GameSpace gameSpace, ServerWorld world,
                                  DeACoudreMap map) {

    public void spawnPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.changeGameMode(gameMode);

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                StatusEffectInstance.INFINITE,
                1,
                true,
                false
        ));

        BlockPos pos = this.map.getSpawn();
        player.teleport(this.world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Set.of(), 0.0F, 0.0F, false);
    }
}
