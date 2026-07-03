package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import xyz.nucleoid.plasmid.api.game.GameSpace;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

public record DeACoudreSpawnLogic(GameSpace gameSpace, ServerLevel world,
                                  DeACoudreMap map) {

    public void spawnPlayer(ServerPlayer player, GameType gameMode) {
        player.setGameMode(gameMode);

        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                MobEffectInstance.INFINITE_DURATION,
                1,
                true,
                false
        ));

        BlockPos pos = this.map.getSpawn();
        player.teleportTo(this.world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Set.of(), 0.0F, 0.0F, false);
    }
}
