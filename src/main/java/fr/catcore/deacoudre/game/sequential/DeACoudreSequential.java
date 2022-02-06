package fr.catcore.deacoudre.game.sequential;

import com.google.common.collect.Sets;
import fr.catcore.deacoudre.game.DeACoudreConfig;
import fr.catcore.deacoudre.game.DeACoudrePool;
import fr.catcore.deacoudre.game.DeACoudreSpawnLogic;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DeACoudreSequential {
    private final DeACoudreConfig config;

    public final GameSpace gameSpace;
    private final DeACoudreMap gameMap;
    public final ServerWorld world;

    private final DeACoudrePool pool;

    private final Set<ServerPlayerEntity> participants;
    private final List<ServerPlayerEntity> jumpOrder;

    private final DeACoudrePlayerLives lives;

    private final DeACoudreSpawnLogic spawnLogic;

    public ServerPlayerEntity currentJumper;
    private int jumperIndex;

    private final DeACoudreSequentialScoreboard scoreboard;

    private final boolean singleplayer;
    private long closeTime = -1;
    private int jumpingTicks;

    private DeACoudreSequential(GameSpace gameSpace, ServerWorld world, DeACoudreMap map, DeACoudreConfig config, Set<ServerPlayerEntity> participants, GlobalWidgets widgets) {
        this.gameSpace = gameSpace;
        this.world = world;
        this.config = config;
        this.gameMap = map;
        this.participants = participants;

        this.jumpOrder = new ArrayList<>(participants);
        Collections.shuffle(this.jumpOrder);

        this.pool = new DeACoudrePool(world, map);

        this.spawnLogic = new DeACoudreSpawnLogic(gameSpace, world, map);

        this.lives = new DeACoudrePlayerLives();
        this.lives.addPlayers(this.participants, config.life());

        this.scoreboard = DeACoudreSequentialScoreboard.create(this, widgets);
        this.singleplayer = this.participants.size() <= 1;
    }

    public Set<ServerPlayerEntity> participants() {
        return this.participants;
    }

    public DeACoudrePlayerLives lives() {
        return this.lives;
    }

    public static void open(GameSpace gameSpace, ServerWorld world,  DeACoudreMap map, DeACoudreConfig config) {
        gameSpace.setActivity(game -> {
            var widgets = GlobalWidgets.addTo(game);

            Set<ServerPlayerEntity> participants = Sets.newHashSet(gameSpace.getPlayers());
            var active = new DeACoudreSequential(gameSpace, world, map, config, participants, widgets);

            game.deny(GameRuleType.CRAFTING);
            game.deny(GameRuleType.PORTALS);
            game.deny(GameRuleType.PVP);
            game.deny(GameRuleType.BLOCK_DROPS);
            game.allow(GameRuleType.FALL_DAMAGE);
            game.deny(GameRuleType.HUNGER);
            game.deny(GameRuleType.FIRE_TICK);
            game.deny(GameRuleType.FLUID_FLOW);
            game.deny(GameRuleType.ICE_MELT);

            game.listen(GameActivityEvents.ENABLE, active::onOpen);
            game.listen(GameActivityEvents.DISABLE, active::onClose);
            game.listen(GameActivityEvents.TICK, active::tick);

            game.listen(GamePlayerEvents.OFFER, active::offerPlayer);

            game.listen(GamePlayerEvents.LEAVE, active::eliminatePlayer);

            game.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        for (ServerPlayerEntity player : this.participants) {
            this.spawnWaiting(player);
        }

        MutableText text;
        if (this.config.life() > 1) {
            text = new TranslatableText("text.dac.game.start_plural", this.config.life());
        } else {
            text = new TranslatableText("text.dac.game.start_singular");
        }

        this.gameSpace.getPlayers().sendMessage(text.formatted(Formatting.GREEN));

        this.currentJumper = this.jumpOrder.get(0);
        this.spawnJumper(this.currentJumper);
    }

    private void onClose() {
        this.scoreboard.close();
    }

    private PlayerOfferResult offerPlayer(PlayerOffer offer) {
        return offer.accept(this.world, Vec3d.ofCenter(this.gameMap.getSpawn()))
                .and(() -> {
                    var player = offer.player();
                    if (!this.participants.contains(player)) {
                        this.spawnSpectator(player);
                    }
                });
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (player == null) return ActionResult.FAIL;

        if (player == this.currentJumper) {
            if (source == DamageSource.OUT_OF_WORLD || source == DamageSource.FALL) {
                this.onPlayerFailJump(player);
            }
        } else {
            return ActionResult.PASS;
        }

        return ActionResult.FAIL;
    }

    private void onPlayerFailJump(ServerPlayerEntity player) {
        int livesRemaining = this.lives.takeLife(player);
        if (livesRemaining == 0) {
            this.eliminatePlayer(player);
            return;
        }

        MutableText message = new TranslatableText("text.dac.game.lose_life", player.getDisplayName());
        if (livesRemaining > 1) {
            message = message.append(new TranslatableText("text.dac.game.lives_left", livesRemaining));
        } else {
            message = message.append(new TranslatableText("text.dac.game.life_left"));
        }

        this.gameSpace.getPlayers().sendMessage(message.formatted(Formatting.YELLOW));
        this.nextJumper();
    }

    private void onPlayerLandInWater(ServerPlayerEntity player) {
        PlayerSet players = this.gameSpace.getPlayers();
        BlockPos pos = player.getBlockPos();

        this.nextJumper();

        if (this.pool.canFormCoudreAt(pos)) {
            this.pool.putCoudreAt(pos);

            int remainingLife = this.lives.grantLife(player);
            players.sendMessage(new TranslatableText("text.dac.game.dac", player.getDisplayName(), remainingLife).formatted(Formatting.AQUA));
            players.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
            players.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE);
        } else {
            this.pool.putBlockAt(player, pos);
            players.playSound(SoundEvents.AMBIENT_UNDERWATER_ENTER);
        }
    }

    private void nextJumper() {
        ServerPlayerEntity finishedJumper = this.currentJumper;

        int nextJumperIndex = this.getNextJumperIndex();
        ServerPlayerEntity nextJumper = nextJumperIndex != -1 ? this.jumpOrder.get(nextJumperIndex) : null;

        if (finishedJumper != null && nextJumper != finishedJumper) {
            this.spawnWaiting(finishedJumper);
        }

        if (nextJumper != null) {
            this.spawnJumper(nextJumper);
        }

        this.jumperIndex = nextJumperIndex;
        this.currentJumper = nextJumper;
        this.jumpingTicks = 0;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.eliminatePlayer(player);
        return ActionResult.FAIL;
    }

    private void eliminatePlayer(ServerPlayerEntity player) {
        if (this.participants.remove(player)) {
            this.jumpOrder.remove(player);
            this.lives.removePlayer(player);

            Text message = new TranslatableText("text.dac.game.eliminated", player.getDisplayName())
                    .formatted(Formatting.RED);

            PlayerSet players = this.gameSpace.getPlayers();
            players.sendMessage(message);
            players.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);

            this.spawnSpectator(player);

            if (this.singleplayer || player == this.currentJumper) {
                this.nextJumper();
            }
        }
    }

    private void tick() {
        ServerPlayerEntity jumper = this.currentJumper;
        PlayerSet players = this.gameSpace.getPlayers();
        ServerWorld world = this.world;
        long time = world.getTime();

        // check for invalid jumper
        if (jumper == null || !players.contains(jumper) || !this.participants.contains(jumper)) {
            this.nextJumper();
            if (jumper != null) {
                this.eliminatePlayer(jumper);
            }
            return;
        }

        this.jumpingTicks++;
        int jumpingSeconds = this.jumpingTicks / 20;

        this.scoreboard.tick();

        if (this.closeTime > 0) {
            this.tickClosing(this.gameSpace, time);
            return;
        }

        if (this.pool.isFreeAt(jumper.getBlockPos())) {
            this.onPlayerLandInWater(jumper);
        }

        if (this.jumpingTicks % 20 == 0) {
            int remainingJumpingSeconds = Math.max(20 - jumpingSeconds, 0);
            if (remainingJumpingSeconds == 1) {
                jumper.sendMessage(new TranslatableText("text.dac.time.1"), true);
            } else {
                jumper.sendMessage(new TranslatableText("text.dac.time.+", remainingJumpingSeconds), true);
            }

            if (remainingJumpingSeconds == 0) {
                int remainingLife = this.lives.takeLife(jumper);

                players.sendMessage(new TranslatableText("text.dac.game.slow", jumper.getName().getString(), remainingLife).formatted(Formatting.YELLOW));
                this.nextJumper();

                if (remainingLife == 0) {
                    this.eliminatePlayer(jumper);
                }
            }
        }

        WinResult result = this.checkWinResult();
        if (result.isWin()) {
            this.broadcastWin(result);
            this.closeTime = time + 20 * 5;
        }
    }

    @Nullable
    public ServerPlayerEntity getNextJumper() {
        int jumperIndex = this.getNextJumperIndex();
        return jumperIndex != -1 ? this.jumpOrder.get(jumperIndex) : null;
    }

    private int getNextJumperIndex() {
        if (this.jumpOrder.isEmpty()) {
            return -1;
        }
        return (this.jumperIndex + 1) % this.jumpOrder.size();
    }

    private void spawnWaiting(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
        player.fallDistance = 0.0F;
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.SPECTATOR);
    }

    private void spawnJumper(ServerPlayerEntity jumper) {
        Vec3d platformSpawn = this.gameMap.getJumpingPlatform().center();

        jumper.teleport(this.world, platformSpawn.x, platformSpawn.y, platformSpawn.z, 180F, 0F);
        jumper.fallDistance = 0.0F;

        jumper.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.0F, 1.0F);

        this.gameSpace.getPlayers().sendMessage(new TranslatableText("text.dac.game.turn", jumper.getDisplayName()).formatted(Formatting.BLUE));
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.singleplayer) {
            return WinResult.no();
        }

        if (this.pool.isFull()) {
            return WinResult.win(null);
        }

        ServerPlayerEntity winningPlayer = null;

        for (ServerPlayerEntity player : this.participants) {
            if (player != null) {
                // we still have more than one player remaining
                if (winningPlayer != null) {
                    return WinResult.no();
                }

                winningPlayer = player;
            }
        }

        return WinResult.win(winningPlayer);
    }

    private void broadcastWin(WinResult result) {
        ServerPlayerEntity winningPlayer = result.getWinningPlayer();

        Text message;
        if (winningPlayer != null) {
            message = new TranslatableText("text.dac.game.won", winningPlayer.getDisplayName()).formatted(Formatting.GOLD);
        } else {
            message = new TranslatableText("text.dac.game.won.nobody").formatted(Formatting.GOLD);
        }

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.playSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private void tickClosing(GameSpace game, long time) {
        if (time >= this.closeTime) {
            game.close(GameCloseReason.FINISHED);
        }
    }

    record WinResult(ServerPlayerEntity winningPlayer, boolean win) {

        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(ServerPlayerEntity player) {
            return new WinResult(player, true);
        }

        public boolean isWin() {
            return this.win;
        }

        public ServerPlayerEntity getWinningPlayer() {
            return this.winningPlayer;
        }
    }
}
