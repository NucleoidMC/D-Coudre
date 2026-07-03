package fr.catcore.deacoudre.game.concurrent;

import com.google.common.collect.Sets;
import fr.catcore.deacoudre.game.DeACoudrePool;
import fr.catcore.deacoudre.game.DeACoudreSpawnLogic;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.SidebarWidget;
import xyz.nucleoid.plasmid.api.game.event.*;
import xyz.nucleoid.plasmid.api.game.player.*;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.Comparator;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class DeACoudreConcurrent {
    public final GameSpace gameSpace;
    private final DeACoudreMap gameMap;
    public final ServerLevel world;

    private final DeACoudrePool pool;

    private final Set<ServerPlayer> jumpers;
    private final DeACoudreSpawnLogic spawnLogic;

    private final Object2IntOpenHashMap<ServerPlayer> points = new Object2IntOpenHashMap<>();

    private final SidebarWidget sidebar;

    private long closeTime = -1;

    private DeACoudreConcurrent(GameSpace gameSpace, ServerLevel world, DeACoudreMap map, Set<ServerPlayer> jumpers, GlobalWidgets widgets) {
        this.gameSpace = gameSpace;
        this.gameMap = map;
        this.jumpers = jumpers;
        this.world = world;

        this.pool = new DeACoudrePool(world, map);

        this.spawnLogic = new DeACoudreSpawnLogic(gameSpace, world, map);

        this.sidebar = widgets.addSidebar(Component.literal("Dé à Coudre").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
    }

    public static void open(GameSpace gameSpace, ServerLevel world, DeACoudreMap map) {
        gameSpace.setActivity(game -> {
            GlobalWidgets widgets = GlobalWidgets.addTo(game);

            Set<ServerPlayer> jumpers = Sets.newHashSet(gameSpace.getPlayers().participants());
            DeACoudreConcurrent active = new DeACoudreConcurrent(gameSpace, world, map, jumpers, widgets);

            game.deny(GameRuleType.CRAFTING);
            game.deny(GameRuleType.PORTALS);
            game.deny(GameRuleType.PVP);
            game.deny(GameRuleType.BLOCK_DROPS);
            game.allow(GameRuleType.FALL_DAMAGE);
            game.deny(GameRuleType.HUNGER);

            game.listen(GameActivityEvents.ENABLE, active::onOpen);
            game.listen(GameActivityEvents.TICK, active::tick);

            game.listen(GamePlayerEvents.ACCEPT, active::offerPlayer);
            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);

            game.listen(GamePlayerEvents.LEAVE, active::removePlayer);

            game.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        for (ServerPlayer player : this.jumpers) {
            this.spawnJumper(player);
        }

        for (ServerPlayer player : this.gameSpace.getPlayers().spectators()) {
            this.spawnSpectator(player);
        }

        this.updateSidebar();
    }

    private JoinAcceptorResult offerPlayer(JoinAcceptor offer) {
        return offer.teleport(this.world, Vec3.atCenterOf(this.gameMap.getSpawn()))
                .thenRunForEach((player, intent) -> {
                    if (!this.jumpers.contains(player) || intent == JoinIntent.SPECTATE) {
                        this.spawnSpectator(player);
                    }
                });
    }

    private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
        if (player == null) return EventResult.DENY;

        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.FALL)) {
            this.onPlayerFailJump(player);
            return EventResult.DENY;
        }

        return EventResult.DENY;
    }

    private void onPlayerFailJump(ServerPlayer player) {
        this.spawnJumper(player);
    }

    private void onPlayerLandInWater(ServerPlayer player) {
        PlayerSet players = this.gameSpace.getPlayers();
        BlockPos pos = player.blockPosition();

        this.spawnJumper(player);

        if (this.pool.canFormCoudreAt(pos)) {
            this.pool.putCoudreAt(pos);

            this.points.addTo(player, 10);
            this.updateSidebar();

            players.playSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST);
            players.playSound(SoundEvents.FIREWORK_ROCKET_TWINKLE);
        } else {
            this.points.addTo(player, 1);
            this.updateSidebar();

            this.pool.putBlockAt(player, pos);
            players.playSound(SoundEvents.AMBIENT_UNDERWATER_ENTER);
        }
    }

    private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
        this.spawnJumper(player);
        return EventResult.DENY;
    }

    private void removePlayer(ServerPlayer player) {
        this.jumpers.remove(player);
    }

    private void tick() {
        ServerLevel world = this.world;
        long time = world.getGameTime();

        if (this.closeTime > 0) {
            this.tickClosing(this.gameSpace, time);
            return;
        }

        for (ServerPlayer jumper : this.jumpers) {
            BlockPos pos = jumper.blockPosition();
            if (this.pool.contains(pos) && this.pool.isFreeAt(pos)) {
                this.onPlayerLandInWater(jumper);
            }
        }

        ServerPlayer winningPlayer = this.checkWinResult();
        if (winningPlayer != null) {
            this.broadcastWin(winningPlayer);
            this.closeTime = time + 20 * 5;
        }
    }

    private void spawnSpectator(ServerPlayer player) {
        this.spawnLogic.spawnPlayer(player, GameType.SPECTATOR);
    }

    private void spawnJumper(ServerPlayer jumper) {
        Vec3 platformSpawn = this.gameMap.getJumpingPlatform().center();

        jumper.teleportTo(this.world, platformSpawn.x, platformSpawn.y, platformSpawn.z, Set.of(), 180F, 0F, false);
        jumper.fallDistance = 0.0F;
    }

    private void updateSidebar() {
        this.sidebar.set(content -> this.jumpers.stream()
                .sorted(Comparator.comparingInt(this.points::getInt).reversed())
                .forEach(player -> {
                    int points = this.points.getInt(player);
                    content.add(Component.nullToEmpty(ChatFormatting.AQUA + player.getDisplayName().getString() + ": " + ChatFormatting.GOLD + points));
                }));
    }

    @Nullable
    private ServerPlayer checkWinResult() {
        if (!this.pool.isFull()) {
            return null;
        }

        ServerPlayer winner = null;
        int winnerPoints = 0;

        for (ServerPlayer jumper : this.jumpers) {
            int points = this.points.getInt(jumper);
            if (points > winnerPoints) {
                winnerPoints = points;
                winner = jumper;
            }
        }

        return winner;
    }

    private void broadcastWin(ServerPlayer winningPlayer) {
        Component message = Component.translatable("text.dac.game.won", winningPlayer.getDisplayName()).withStyle(ChatFormatting.GOLD);

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.playSound(SoundEvents.VILLAGER_YES);
    }

    private void tickClosing(GameSpace game, long time) {
        if (time >= this.closeTime) {
            game.close(GameCloseReason.FINISHED);
        }
    }
}
