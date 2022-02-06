package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import xyz.nucleoid.plasmid.game.GameSpace;

public record DeACoudreSpawnLogic(GameSpace gameSpace, ServerWorld world,
                                  DeACoudreMap map) {

    public void spawnPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.changeGameMode(gameMode);

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                20 * 60 * 60,
                1,
                true,
                false
        ));

        var spawnPos = getSpawnPos();

        player.teleport(this.world, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0.0F, 0.0F);
    }

    public Vec3d getSpawnPos() {
        var spawnChunk = this.world.getChunk(0, 0);
        var spawnChunkPos = spawnChunk.getPos();
        var spawnChunkHeight = this.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawnChunkPos.getCenterX(), spawnChunkPos.getCenterZ());

        return new Vec3d(spawnChunkPos.getCenterX() + 0.5, spawnChunkHeight + 0.5, spawnChunkPos.getCenterZ() + 0.5);
    }
}
