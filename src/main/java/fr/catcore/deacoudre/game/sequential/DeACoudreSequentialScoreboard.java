package fr.catcore.deacoudre.game.sequential;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.SidebarWidget;

import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class DeACoudreSequentialScoreboard implements AutoCloseable {

    private final SidebarWidget sidebar;
    private final DeACoudreSequential game;
    private final Objective lifeObjective;

    private boolean dirty = true;

    private long ticks;

    public DeACoudreSequentialScoreboard(DeACoudreSequential game, SidebarWidget sidebar, Objective lifeObjective) {
        this.sidebar = sidebar;
        this.game = game;
        this.lifeObjective = lifeObjective;
    }

    public static DeACoudreSequentialScoreboard create(DeACoudreSequential game, GlobalWidgets widgets) {
        ServerScoreboard scoreboard = game.world.getServer().getScoreboard();

        Component title = Component.literal("Dé à Coudre").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD);
        SidebarWidget sidebar = widgets.addSidebar(title);

        var scoreboardObjective2 = scoreboard.addObjective("de_a_coudre_life",
                ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER,
                false, null);

        scoreboard.setDisplayObjective(DisplaySlot.LIST, scoreboardObjective2);

        return new DeACoudreSequentialScoreboard(game, sidebar, scoreboardObjective2);
    }

    public void tick() {
        this.ticks++;

        if (this.dirty || this.ticks % 20 == 0) {
            this.rerender();
            this.dirty = false;
        }
    }

    private void rerender() {
        this.sidebar.set(content -> {
            long seconds = (this.ticks / 20) % 60;
            long minutes = this.ticks / (20 * 60);

            content.add(Component.nullToEmpty(String.format("%sTime: %s%02d:%02d", ChatFormatting.RED.toString() + ChatFormatting.BOLD, ChatFormatting.RESET, minutes, seconds)));

            long playersAlive = this.game.participants().size();
            content.add(Component.nullToEmpty(ChatFormatting.BLUE.toString() + playersAlive + " players alive"));
            content.add(Component.nullToEmpty(""));

            ServerPlayer currentJumper = this.game.currentJumper;
            ServerPlayer nextJumper = this.game.getNextJumper();

            if (currentJumper != null) {
                content.add(Component.nullToEmpty("Jumping: " + currentJumper.getName().getString()));
            }
            if (nextJumper != null) {
                content.add(Component.nullToEmpty("Up Next: " + nextJumper.getName().getString()));
            }
        });

        ServerScoreboard scoreboard = this.game.world.getServer().getScoreboard();
        clear(scoreboard, lifeObjective);
        for (Object2IntMap.Entry<ServerPlayer> entry : this.game.lives()) {
            if (entry.getKey() == null) continue;
            ServerPlayer playerEntity = entry.getKey();
            ScoreHolder scoreHolder = ScoreHolder.fromGameProfile(playerEntity.getGameProfile());
            scoreboard.getOrCreatePlayerScore(scoreHolder, lifeObjective)
                .set(entry.getIntValue());
        }
    }

    private static void clear(ServerScoreboard scoreboard, Objective objective) {
        Collection<ScoreHolder> existing = scoreboard.getTrackedPlayers();
        for (ScoreHolder scoreHolder : existing) {
            scoreboard.resetSinglePlayerScore(scoreHolder, objective);
        }
    }

    @Override
    public void close() {
        ServerScoreboard scoreboard = this.game.world.getServer().getScoreboard();
        scoreboard.removeObjective(this.lifeObjective);
    }
}
