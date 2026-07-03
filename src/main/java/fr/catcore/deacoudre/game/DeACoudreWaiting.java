package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.concurrent.DeACoudreConcurrent;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import fr.catcore.deacoudre.game.map.DeACoudreMapGenerator;
import fr.catcore.deacoudre.game.sequential.DeACoudreSequential;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.api.game.*;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.io.IOException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class DeACoudreWaiting {
    private final GameSpace gameSpace;
    private final DeACoudreMap map;
    private final ServerLevel world;
    private final DeACoudreConfig config;
    private final DeACoudreSpawnLogic spawnLogic;

    private DeACoudreWaiting(GameSpace gameSpace, ServerLevel world, DeACoudreMap map, DeACoudreConfig config) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.world = world;
        this.spawnLogic = new DeACoudreSpawnLogic(gameSpace, world, map);
    }

    public static GameOpenProcedure open(GameOpenContext<DeACoudreConfig> context) {
        var config = context.config();
        var map = config.map().map(
                mapConfig -> {
                    DeACoudreMapGenerator generator = new DeACoudreMapGenerator(mapConfig);
                    return generator.build();
                },
                identifier -> {
                    try {
                        MapTemplate template = MapTemplateSerializer.loadFromResource(context.server(), identifier);
                        return DeACoudreMap.fromTemplate(template);
                    } catch (IOException e) {
                        return DeACoudreMap.fromTemplate(MapTemplate.createEmpty());
                    }
                }
        );

        var worldConfig = new RuntimeLevelConfig()
                .setGenerator(map.asGenerator(context.server()));
//
//        BubbleWorldConfig worldConfig = new BubbleWorldConfig()
//                .setGenerator(map.asGenerator(context.getServer()))
//                .setDefaultGameMode(GameMode.SPECTATOR)
//                .setSpawnAt(new Vec3d(map.getSpawn().getX(),map.getSpawn().getY(),map.getSpawn().getZ()));

        return context.openWithLevel(worldConfig, (game, world) -> {
            GameWaitingLobby.addTo(game, config.playerConfig());

            var waiting = new DeACoudreWaiting(game.getGameSpace(), world, map, config);

            game.deny(GameRuleType.CRAFTING);
            game.deny(GameRuleType.PORTALS);
            game.deny(GameRuleType.PVP);
            game.deny(GameRuleType.BLOCK_DROPS);
            game.deny(GameRuleType.HUNGER);
            game.deny(GameRuleType.FALL_DAMAGE);

            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);

            game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            game.listen(GamePlayerEvents.ACCEPT, waiting::acceptPlayer);
            game.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);
        });
    }

    private GameResult requestStart() {
        if (this.config.concurrent()) {
            DeACoudreConcurrent.open(this.gameSpace, this.world, this.map);
        } else {
            DeACoudreSequential.open(this.gameSpace, this.world, this.map, this.config);
        }
        return GameResult.ok();
    }

    private JoinAcceptorResult acceptPlayer(JoinAcceptor offer) {
        var spawn = this.map.getSpawn();
        if (spawn == null) {
            return offer.pass();
        }

        return offer.teleport(this.world, Vec3.atCenterOf(spawn))
                .thenRunForEach((player) -> {
                    this.spawnLogic.spawnPlayer(player, GameType.ADVENTURE);
                });
    }

    private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
        this.spawnLogic.spawnPlayer(player, GameType.ADVENTURE);
        return EventResult.DENY;
    }
}
