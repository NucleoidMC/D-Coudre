package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.concurrent.DeACoudreConcurrent;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import fr.catcore.deacoudre.game.map.DeACoudreMapGenerator;
import fr.catcore.deacoudre.game.sequential.DeACoudreSequential;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.dimension.DimensionType;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.io.IOException;

public class DeACoudreWaiting {
    private final GameSpace gameSpace;
    private final DeACoudreMap map;
    private final ServerWorld world;
    private final DeACoudreConfig config;
    private final DeACoudreSpawnLogic spawnLogic;

    private DeACoudreWaiting(GameSpace gameSpace, ServerWorld world, DeACoudreMap map, DeACoudreConfig config) {
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

        var worldConfig = new RuntimeWorldConfig()
                .setGenerator(map.asGenerator(context.server()))
                .setDimensionType(DimensionType.OVERWORLD_REGISTRY_KEY);
//
//        BubbleWorldConfig worldConfig = new BubbleWorldConfig()
//                .setGenerator(map.asGenerator(context.getServer()))
//                .setDefaultGameMode(GameMode.SPECTATOR)
//                .setSpawnAt(new Vec3d(map.getSpawn().getX(),map.getSpawn().getY(),map.getSpawn().getZ()));

        return context.openWithWorld(worldConfig, (game, world) -> {
            GameWaitingLobby.addTo(game, config.playerConfig());

            var waiting = new DeACoudreWaiting(game.getGameSpace(), world, map, config);

            game.deny(GameRuleType.CRAFTING);
            game.deny(GameRuleType.PORTALS);
            game.deny(GameRuleType.PVP);
            game.deny(GameRuleType.BLOCK_DROPS);
            game.deny(GameRuleType.HUNGER);
            game.deny(GameRuleType.FALL_DAMAGE);
            game.deny(GameRuleType.FIRE_TICK);
            game.deny(GameRuleType.FLUID_FLOW);
            game.deny(GameRuleType.ICE_MELT);

            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);

            game.listen(GamePlayerEvents.OFFER, waiting::offerPlayer);
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

    private PlayerOfferResult offerPlayer(PlayerOffer offer) {
        var spawn = this.map.getSpawn();
        if (spawn == null) {
            return offer.reject(new LiteralText("No spawn defined on map!"));
        }

        return offer.accept(this.world, this.spawnLogic.getSpawnPos())
                .and(() -> {
                    var player = offer.player();
                    this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
                });
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
        return ActionResult.FAIL;
    }
}
