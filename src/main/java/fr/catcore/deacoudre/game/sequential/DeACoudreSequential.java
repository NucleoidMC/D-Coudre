package fr.catcore.deacoudre.game.sequential;

import com.google.common.collect.Sets;
import fr.catcore.deacoudre.game.DeACoudreConfig;
import fr.catcore.deacoudre.game.DeACoudrePool;
import fr.catcore.deacoudre.game.DeACoudreSpawnLogic;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.*;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerUtil;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class DeACoudreSequential {
    private final DeACoudreConfig config;

    public final GameSpace gameSpace;
    private final DeACoudreMap gameMap;
    public final ServerLevel world;

    private final DeACoudrePool pool;

    private final Set<ServerPlayer> participants;
    private final List<ServerPlayer> jumpOrder;

    private final DeACoudrePlayerLives lives;

    private final DeACoudreSpawnLogic spawnLogic;

    public ServerPlayer currentJumper;
    private int jumperIndex;

    private final DeACoudreSequentialScoreboard scoreboard;

    private final boolean singleplayer;
    private long closeTime = -1;
    private int jumpingTicks;

    private DeACoudreSequential(GameSpace gameSpace, ServerLevel world, DeACoudreMap map, DeACoudreConfig config, Set<ServerPlayer> participants, GlobalWidgets widgets) {
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

    public Set<ServerPlayer> participants() {
        return this.participants;
    }

    public DeACoudrePlayerLives lives() {
        return this.lives;
    }

    public static void open(GameSpace gameSpace, ServerLevel world,  DeACoudreMap map, DeACoudreConfig config) {
        gameSpace.setActivity(game -> {
            var widgets = GlobalWidgets.addTo(game);

            Set<ServerPlayer> participants = Sets.newHashSet(gameSpace.getPlayers().participants());
            var active = new DeACoudreSequential(gameSpace, world, map, config, participants, widgets);

            game.deny(GameRuleType.CRAFTING);
            game.deny(GameRuleType.PORTALS);
            game.deny(GameRuleType.PVP);
            game.deny(GameRuleType.BLOCK_DROPS);
            game.allow(GameRuleType.FALL_DAMAGE);
            game.deny(GameRuleType.HUNGER);

            game.listen(GameActivityEvents.ENABLE, active::onOpen);
            game.listen(GameActivityEvents.DISABLE, active::onClose);
            game.listen(GameActivityEvents.TICK, active::tick);
            game.listen(GameActivityEvents.STATE_UPDATE, state -> state.canPlay(false));


            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
            game.listen(GamePlayerEvents.ACCEPT, active::offerPlayer);

            game.listen(GamePlayerEvents.LEAVE, active::eliminatePlayer);

            game.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        for (ServerPlayer player : this.participants) {
            this.spawnWaiting(player);
        }

        MutableComponent text;
        if (this.config.life() > 1) {
            text = Component.translatable("text.dac.game.start_plural", this.config.life());
        } else {
            text = Component.translatable("text.dac.game.start_singular");
        }

        this.gameSpace.getPlayers().sendMessage(text.withStyle(ChatFormatting.GREEN));

        this.currentJumper = this.jumpOrder.get(0);
        this.spawnJumper(this.currentJumper);
    }

    private void onClose() {
        this.scoreboard.close();
    }

    private JoinAcceptorResult offerPlayer(JoinAcceptor offer) {
        return offer.teleport(this.world, Vec3.atCenterOf(this.gameMap.getSpawn()))
                .thenRunForEach((player, intent) -> {
                    if (!this.participants.contains(player) || intent == JoinIntent.SPECTATE) {
                        this.spawnSpectator(player);
                    }
                });
    }

    private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
        if (player == null) return EventResult.DENY;

        if (player == this.currentJumper) {
            if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.FALL)) {
                this.onPlayerFailJump(player);
            }
        } else {
            return EventResult.PASS;
        }

        return EventResult.DENY;
    }

    private void onPlayerFailJump(ServerPlayer player) {
        int livesRemaining = this.lives.takeLife(player);
        if (livesRemaining == 0) {
            this.eliminatePlayer(player);
            return;
        }

        MutableComponent message = Component.translatable("text.dac.game.lose_life", player.getDisplayName());
        if (livesRemaining > 1) {
            message = message.append(Component.translatable("text.dac.game.lives_left", livesRemaining));
        } else {
            message = message.append(Component.translatable("text.dac.game.life_left"));
        }

        this.gameSpace.getPlayers().sendMessage(message.withStyle(ChatFormatting.YELLOW));
        this.nextJumper();
    }

    private void onPlayerLandInWater(ServerPlayer player) {
        PlayerSet players = this.gameSpace.getPlayers();
        BlockPos pos = player.blockPosition();

        this.nextJumper();

        if (this.pool.canFormCoudreAt(pos)) {
            this.pool.putCoudreAt(pos);

            int remainingLife = this.lives.grantLife(player);
            players.sendMessage(Component.translatable("text.dac.game.dac", player.getDisplayName(), remainingLife).withStyle(ChatFormatting.AQUA));
            players.playSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST);
            players.playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE);
        } else {
            this.pool.putBlockAt(player, pos);
            players.playSound(SoundEvents.AMBIENT_UNDERWATER_ENTER);
        }
    }

    private void nextJumper() {
        ServerPlayer finishedJumper = this.currentJumper;

        int nextJumperIndex = this.getNextJumperIndex();
        ServerPlayer nextJumper = nextJumperIndex != -1 ? this.jumpOrder.get(nextJumperIndex) : null;

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

    private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
        this.eliminatePlayer(player);
        return EventResult.DENY;
    }

    private void eliminatePlayer(ServerPlayer player) {
        if (this.participants.remove(player)) {
            this.jumpOrder.remove(player);
            this.lives.removePlayer(player);

            Component message = Component.translatable("text.dac.game.eliminated", player.getDisplayName())
                    .withStyle(ChatFormatting.RED);

            PlayerSet players = this.gameSpace.getPlayers();
            players.sendMessage(message);
            players.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP);

            this.spawnSpectator(player);

            if (this.singleplayer || player == this.currentJumper) {
                this.nextJumper();
            }
        }
    }

    private void tick() {
        ServerPlayer jumper = this.currentJumper;
        PlayerSet players = this.gameSpace.getPlayers();
        ServerLevel world = this.world;
        long time = world.getGameTime();

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

        if (this.pool.isFreeAt(jumper.blockPosition())) {
            this.onPlayerLandInWater(jumper);
        }

        if (this.jumpingTicks % 20 == 0) {
            int remainingJumpingSeconds = Math.max(20 - jumpingSeconds, 0);
            if (remainingJumpingSeconds == 1) {
                jumper.sendSystemMessage(Component.translatable("text.dac.time.1"), true);
            } else {
                jumper.sendSystemMessage(Component.translatable("text.dac.time.+", remainingJumpingSeconds), true);
            }

            if (remainingJumpingSeconds == 0) {
                int remainingLife = this.lives.takeLife(jumper);

                players.sendMessage(Component.translatable("text.dac.game.slow", jumper.getName().getString(), remainingLife).withStyle(ChatFormatting.YELLOW));
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
    public ServerPlayer getNextJumper() {
        int jumperIndex = this.getNextJumperIndex();
        return jumperIndex != -1 ? this.jumpOrder.get(jumperIndex) : null;
    }

    private int getNextJumperIndex() {
        if (this.jumpOrder.isEmpty()) {
            return -1;
        }
        return (this.jumperIndex + 1) % this.jumpOrder.size();
    }

    private void spawnWaiting(ServerPlayer player) {
        this.spawnLogic.spawnPlayer(player, GameType.ADVENTURE);
        player.fallDistance = 0.0F;
    }

    private void spawnSpectator(ServerPlayer player) {
        this.spawnLogic.spawnPlayer(player, GameType.SPECTATOR);
    }

    private void spawnJumper(ServerPlayer jumper) {
        Vec3 platformSpawn = this.gameMap.getJumpingPlatform().center();

        jumper.teleportTo(this.world, platformSpawn.x, platformSpawn.y, platformSpawn.z, Set.of(), 180F, 0F, false);
        jumper.fallDistance = 0.0F;

        PlayerUtil.playSoundToPlayer(jumper, SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0F, 1.0F);

        this.gameSpace.getPlayers().sendMessage(Component.translatable("text.dac.game.turn", jumper.getDisplayName()).withStyle(ChatFormatting.BLUE));
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.singleplayer) {
            return WinResult.no();
        }

        if (this.pool.isFull()) {
            return WinResult.win(null);
        }

        ServerPlayer winningPlayer = null;

        for (ServerPlayer player : this.participants) {
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
        ServerPlayer winningPlayer = result.getWinningPlayer();

        Component message;
        if (winningPlayer != null) {
            message = Component.translatable("text.dac.game.won", winningPlayer.getDisplayName()).withStyle(ChatFormatting.GOLD);
        } else {
            message = Component.translatable("text.dac.game.won.nobody").withStyle(ChatFormatting.GOLD);
        }

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.playSound(SoundEvents.VILLAGER_YES);
    }

    private void tickClosing(GameSpace game, long time) {
        if (time >= this.closeTime) {
            game.close(GameCloseReason.FINISHED);
        }
    }

    record WinResult(ServerPlayer winningPlayer, boolean win) {

        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(ServerPlayer player) {
            return new WinResult(player, true);
        }

        public boolean isWin() {
            return this.win;
        }

        public ServerPlayer getWinningPlayer() {
            return this.winningPlayer;
        }
    }
}
