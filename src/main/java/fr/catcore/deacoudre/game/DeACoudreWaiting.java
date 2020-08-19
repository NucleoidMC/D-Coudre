package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import fr.catcore.deacoudre.game.map.DeACoudreMapGenerator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.world.bubble.BubbleWorldConfig;

import java.util.concurrent.CompletableFuture;

public class DeACoudreWaiting {
    private final GameWorld gameWorld;
    private final DeACoudreMap map;
    private final DeACoudreConfig config;
    private final DeACoudreSpawnLogic spawnLogic;

    private DeACoudreWaiting(GameWorld gameWorld, DeACoudreMap map, DeACoudreConfig config) {
        this.gameWorld = gameWorld;
        this.map = map;
        this.config = config;

        this.spawnLogic = new DeACoudreSpawnLogic(gameWorld, map);
    }

    public static CompletableFuture<GameWorld> open(GameOpenContext<DeACoudreConfig> gameOpenContext) {
        DeACoudreMapGenerator generator = new DeACoudreMapGenerator(gameOpenContext.getConfig().mapConfig);

        return generator.create().thenCompose(map -> {
            BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                    .setGenerator(map.asGenerator(gameOpenContext.getServer()))
                    .setDefaultGameMode(GameMode.SPECTATOR)
                    .setSpawnAt(new Vec3d(0,3,0));

            return gameOpenContext.openWorld(worldConfig).thenApply(gameWorld -> {
                DeACoudreWaiting waiting = new DeACoudreWaiting(gameWorld, map, gameOpenContext.getConfig());

                gameWorld.openGame(builder -> {
                    builder.setRule(GameRule.CRAFTING, RuleResult.DENY);
                    builder.setRule(GameRule.PORTALS, RuleResult.DENY);
                    builder.setRule(GameRule.PVP, RuleResult.DENY);
                    builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
                    builder.setRule(GameRule.HUNGER, RuleResult.DENY);
                    builder.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);

                    builder.on(RequestStartListener.EVENT, waiting::requestStart);
                    builder.on(OfferPlayerListener.EVENT, waiting::offerPlayer);


                    builder.on(PlayerAddListener.EVENT, waiting::addPlayer);
                    builder.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
                });

                return gameWorld;
            });
        });
    }

    private JoinResult offerPlayer(ServerPlayerEntity player) {
        if (this.gameWorld.getPlayerCount() >= this.config.playerConfig.getMaxPlayers()) {
            return JoinResult.gameFull();
        }

        return JoinResult.ok();
    }

    private StartResult requestStart() {
        PlayerConfig playerConfig = this.config.playerConfig;
        if (this.gameWorld.getPlayerCount() < playerConfig.getMinPlayers()) {
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        DeACoudreActive.open(this.gameWorld, this.map, this.config);

        return StartResult.OK;
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
        return ActionResult.FAIL;
    }
}
