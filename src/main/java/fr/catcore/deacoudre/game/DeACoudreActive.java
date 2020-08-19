package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.DeACoudre;
import fr.catcore.deacoudre.game.map.DeACoudreMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.BlockBounds;
import xyz.nucleoid.plasmid.util.PlayerRef;

import java.util.*;
import java.util.stream.Collectors;

public class DeACoudreActive {

    private final DeACoudreConfig config;

    public final GameWorld gameWorld;
    private final DeACoudreMap gameMap;

    private final Set<PlayerRef> participants;

    private final Map<PlayerRef, BlockState> blockStateMap;

    private final Map<PlayerRef, Integer> lifeMap;

    private final DeACoudreSpawnLogic spawnLogic;

    public PlayerRef nextJumper;

    private boolean turnStarting = true;

    private final DeACoudreScoreboard scoreboard;

    private final boolean singleplayer;
    private long closeTime = -1;
    private long ticks;
    private long seconds;

    private DeACoudreActive(GameWorld gameWorld, DeACoudreMap map, DeACoudreConfig config, Set<PlayerRef> participants) {
        this.gameWorld = gameWorld;
        this.config = config;
        this.gameMap = map;
        this.participants = new HashSet<>(participants);
        this.spawnLogic = new DeACoudreSpawnLogic(gameWorld, map);
        this.nextJumper = (PlayerRef) this.participants.toArray()[0];
        this.blockStateMap = new HashMap<>();
        List<BlockState> blockList = Arrays.asList(this.config.getPlayerBlocks());
        Collections.shuffle(blockList);
        for (PlayerRef playerRef : this.participants) {
            this.blockStateMap.put(playerRef, blockList.get(new Random().nextInt(blockList.size())));
        }
        this.lifeMap = new HashMap<>();
        for (PlayerRef playerRef : this.participants) {
            this.lifeMap.put(playerRef, config.life);
        }
        this.scoreboard = DeACoudreScoreboard.create(this);
        this.singleplayer = this.participants.size() <= 1;
    }

    public Set<PlayerRef> participants() {
        return this.participants;
    }

    public Map<PlayerRef, Integer> lifes() {
        return this.lifeMap;
    }

    public static void open(GameWorld gameWorld, DeACoudreMap map, DeACoudreConfig config) {
        Set<PlayerRef> participants = gameWorld.getPlayers().stream()
                .map(PlayerRef::of)
                .collect(Collectors.toSet());
        DeACoudreActive active = new DeACoudreActive(gameWorld, map, config, participants);

        gameWorld.openGame(builder -> {
            builder.setRule(GameRule.CRAFTING, RuleResult.DENY);
            builder.setRule(GameRule.PORTALS, RuleResult.DENY);
            builder.setRule(GameRule.PVP, RuleResult.DENY);
            builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            builder.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
            builder.setRule(GameRule.HUNGER, RuleResult.DENY);

            builder.on(GameOpenListener.EVENT, active::onOpen);
            builder.on(GameCloseListener.EVENT, active::onClose);

            builder.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            builder.on(PlayerAddListener.EVENT, active::addPlayer);
            builder.on(PlayerRemoveListener.EVENT, active::eliminatePlayer);

            builder.on(GameTickListener.EVENT, active::tick);

            builder.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
            builder.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        ServerWorld world = this.gameWorld.getWorld();
        for (PlayerRef ref : this.participants) {
            ref.ifOnline(world, this::spawnParticipant);
        }
        this.broadcastMessage(new LiteralText(String.format("All player start with %s life/lives.", this.config.life)).formatted(Formatting.GREEN));
    }

    private void onClose() {
        this.scoreboard.close();
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.contains(PlayerRef.of(player))) {
            this.spawnSpectator(player);
        }
    }

    private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (player == null) return true;
        if (!this.singleplayer && !PlayerRef.of(player).equals(this.nextJumper)) return true;

        Vec3d playerPos = player.getPos();
        BlockBounds poolBounds = this.gameMap.getTemplate().getFirstRegion("pool");
        boolean isInPool = poolBounds.contains((int)playerPos.x, (int)playerPos.y, (int)playerPos.z);
        if (source == DamageSource.FALL && ((playerPos.y < 10 && playerPos.y > 6) || isInPool)) {

            if (this.lifeMap.get(PlayerRef.of(player)) < 2) this.eliminatePlayer(player);
            else {
                this.spawnParticipant(player);
                this.lifeMap.replace(PlayerRef.of(player), this.lifeMap.get(PlayerRef.of(player)) - 1);
                this.nextJumper = this.nextPlayer(true);
                Text message = player.getDisplayName().shallowCopy();

                this.broadcastMessage(new LiteralText(String.format("%s lost a life! %s life/lives left!", message.getString(), this.lifeMap.get(PlayerRef.of(player)))).formatted(Formatting.YELLOW));
            }
        } else if (source == DamageSource.OUT_OF_WORLD) {
            BlockPos blockPos = this.gameMap.getSpawn();
            player.teleport(this.gameWorld.getWorld(), blockPos.getX(), 10, blockPos.getZ(), 180F, 0F);
        }
        return true;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.eliminatePlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.ADVENTURE);
    }

    private void eliminatePlayer(ServerPlayerEntity player) {
        Text message = player.getDisplayName().shallowCopy().append(" has been eliminated!")
                .formatted(Formatting.RED);

        this.broadcastMessage(message);
        this.broadcastSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);

        this.spawnSpectator(player);
        PlayerRef eliminated = PlayerRef.of(player);
        for (PlayerRef playerRef : this.participants) {
            if (playerRef.equals(eliminated)) {
                eliminated = playerRef;
                break;
            }
        }
        this.participants.remove(eliminated);
        this.lifeMap.remove(eliminated);
        this.blockStateMap.remove(eliminated);

        if (this.singleplayer || PlayerRef.of(player) == this.nextJumper) {
           this.nextJumper = nextPlayer(true);
        }
    }

    private void broadcastMessage(Text message) {
        this.gameWorld.getPlayerSet().sendMessage(message);
    }

    private void broadcastSound(SoundEvent sound) {
        this.gameWorld.getPlayerSet().sendSound(sound);
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.spawnPlayer(player, GameMode.SPECTATOR);
    }

    private void tick() {
        if (this.nextJumper == null) {
            this.nextJumper = nextPlayer(true);
            return;
        }
        this.ticks++;
        this.updateExperienceBar();
        this.scoreboard.tick();
        ServerPlayerEntity playerEntity = this.nextJumper.getEntity(this.gameWorld.getWorld());
        long time = this.gameWorld.getWorld().getTime();
        if (this.closeTime > 0) {
            this.tickClosing(this.gameWorld, time);
            return;
        }
        if (this.turnStarting && this.participants.contains(this.nextJumper)) {
            this.ticks = 0;
            this.seconds = 0;
            BlockBounds jumpBoundaries = this.gameMap.getTemplate().getFirstRegion("jumpingPlatform");
            Vec3d vec3d = jumpBoundaries.getCenter().add(0, 2, 0);
            if (playerEntity == null || this.nextJumper == null) {
                this.broadcastMessage(new LiteralText("nextJumper is null! Attempting to get the next player.").formatted(Formatting.RED));
                this.nextJumper = nextPlayer(false);
                playerEntity = this.nextJumper.getEntity(this.gameWorld.getWorld());
                this.broadcastMessage(new LiteralText("Next player is " + playerEntity.getName().getString()));
                this.scoreboard.tick();
            }
            playerEntity.teleport(this.gameWorld.getWorld(), vec3d.x, vec3d.y, vec3d.z, 180F, 0F);
            Text message = playerEntity.getDisplayName().shallowCopy();

            this.broadcastMessage(new LiteralText(String.format("It's %s's turn!", message.getString())).formatted(Formatting.BLUE));
            this.turnStarting = false;
        }
        if (playerEntity == null || this.nextJumper == null) {
            if (playerEntity == null) {
                DeACoudre.LOGGER.warn("playerEntity is null!");
            }
            if (this.nextJumper == null) {
                DeACoudre.LOGGER.warn("nextJumper is null! Attempting to get the next player.");
                this.nextJumper = nextPlayer(true);
            }
        } else if (this.gameWorld.getWorld().getBlockState(playerEntity.getBlockPos()).equals(Blocks.WATER.getDefaultState()) && this.participants.contains(this.nextJumper)) {
            BlockPos pos = playerEntity.getBlockPos();
            boolean coudre = false;
            if (this.gameWorld.getWorld().getBlockState(pos.west()) != Blocks.WATER.getDefaultState()
                && this.gameWorld.getWorld().getBlockState(pos.east()) != Blocks.WATER.getDefaultState()
                && this.gameWorld.getWorld().getBlockState(pos.north()) != Blocks.WATER.getDefaultState()
                && this.gameWorld.getWorld().getBlockState(pos.south()) != Blocks.WATER.getDefaultState()
            ) {
                this.gameWorld.getWorld().setBlockState(pos, Blocks.EMERALD_BLOCK.getDefaultState());
                this.lifeMap.replace(this.nextJumper, this.lifeMap.get(this.nextJumper) + 1);
                Text message = playerEntity.getDisplayName().shallowCopy();

                this.broadcastMessage(new LiteralText(String.format("%s made a dé à coudre! They are winning an additional life! %s lives left!", message.getString(), this.lifeMap.get(this.nextJumper))).formatted(Formatting.AQUA));
                coudre = true;
            } else {
                this.gameWorld.getWorld().setBlockState(pos, this.blockStateMap.get(this.nextJumper));
            }
            this.nextJumper = nextPlayer(true);
            spawnParticipant(playerEntity);
            if (coudre) {
                this.broadcastSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
                this.broadcastSound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE);
            } else {
                this.broadcastSound(SoundEvents.AMBIENT_UNDERWATER_ENTER);
            }
        }
        if (this.ticks % 20 == 0 && !this.turnStarting) {
            this.seconds++;
        }
        BlockBounds jumpingArea = this.gameMap.getTemplate().getFirstRegion("jumpingArea");
        if (this.seconds % 20 == 0 && !this.turnStarting && this.nextJumper != null && playerEntity != null && jumpingArea.contains(playerEntity.getBlockPos())) {
            this.broadcastMessage(new LiteralText(String.format("%s was to slow to jump and lost a life (%s)", playerEntity.getName().getString(), this.lifeMap.get(this.nextJumper) - 1)).formatted(Formatting.YELLOW));
            this.lifeMap.replace(this.nextJumper, this.lifeMap.get(this.nextJumper) - 1);
            this.nextJumper = nextPlayer(true);
            spawnParticipant(playerEntity);
            if (this.lifeMap.get(PlayerRef.of(playerEntity)) < 1) {
                eliminatePlayer(playerEntity);
            }
        }
        WinResult result = this.checkWinResult();
        if (result.isWin()) {
            this.broadcastWin(result);
            this.closeTime = time + 20 * 5;
        }
    }

    private void updateExperienceBar() {
        for (PlayerRef playerRef : this.participants) {
            if (playerRef == null) continue;
            ServerPlayerEntity playerEntity = playerRef.getEntity(this.gameWorld.getWorld());
            if (playerEntity == null) continue;
            if (this.lifeMap.containsKey(playerRef)) playerEntity.setExperienceLevel(this.lifeMap.get(playerRef));
        }
    }

    private void broadcastWin(WinResult result) {
        ServerPlayerEntity winningPlayer = result.getWinningPlayer();

        Text message;
        if (winningPlayer != null) {
            message = winningPlayer.getDisplayName().shallowCopy().append(" has won the game!").formatted(Formatting.GOLD);
        } else {
            message = new LiteralText("The game ended, but nobody won!").formatted(Formatting.GOLD);
        }

        this.broadcastMessage(message);
        this.broadcastSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private void tickClosing(GameWorld game, long time) {
        if (time >= this.closeTime) {
            game.close();
        }
    }

    public PlayerRef nextPlayer(boolean newTurn) {
        boolean bool = false;
        boolean bool2 = true;
        PlayerRef next = this.nextJumper;
        for (PlayerRef playerRef : participants) {
            if (playerRef == next) {
                bool = true;
                bool2 = false;
                continue;
            }
            if (bool) {
                bool = false;
                next = playerRef;
                if (newTurn) {
                    this.turnStarting = true;
                }
                break;
            }
        }
        if ((bool || bool2) && next == this.nextJumper) {
            for (PlayerRef playerRef : participants) {
                next = playerRef;
                if (newTurn) {
                    this.turnStarting = true;
                }
                break;
            }
        }
        if (next == this.nextJumper && !this.singleplayer) {
            DeACoudre.LOGGER.warn("next is equals to nextJumper, something might be wrong!");
            return null;
        }
        return next;
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.singleplayer) {
            return WinResult.no();
        }

        ServerWorld world = this.gameWorld.getWorld();

        ServerPlayerEntity winningPlayer = null;

        for (PlayerRef ref : this.participants) {
            ServerPlayerEntity player = ref.getEntity(world);
            if (player != null) {
                // we still have more than one player remaining
                if (winningPlayer != null) {
                    return WinResult.no();
                }

                winningPlayer = player;
            }
        }

        BlockBounds pool = this.gameMap.getTemplate().getFirstRegion("pool");
        boolean isFull = true;
        for (BlockPos pos : pool.iterate()) {
            if (this.gameWorld.getWorld().getBlockState(pos) == Blocks.WATER.getDefaultState()) isFull = false;
        }

        if (isFull) {
            return WinResult.win(null);
        }

        return WinResult.win(winningPlayer);
    }

    static class WinResult {
        final ServerPlayerEntity winningPlayer;
        final boolean win;

        private WinResult(ServerPlayerEntity winningPlayer, boolean win) {
            this.winningPlayer = winningPlayer;
            this.win = win;
        }

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
