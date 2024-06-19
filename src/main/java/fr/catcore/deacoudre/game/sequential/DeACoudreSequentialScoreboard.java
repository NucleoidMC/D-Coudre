package fr.catcore.deacoudre.game.sequential;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.common.widget.SidebarWidget;

import java.util.Collection;

public class DeACoudreSequentialScoreboard implements AutoCloseable {

    private final SidebarWidget sidebar;
    private final DeACoudreSequential game;
    private final ScoreboardObjective lifeObjective;

    private boolean dirty = true;

    private long ticks;

    public DeACoudreSequentialScoreboard(DeACoudreSequential game, SidebarWidget sidebar, ScoreboardObjective lifeObjective) {
        this.sidebar = sidebar;
        this.game = game;
        this.lifeObjective = lifeObjective;
    }

    public static DeACoudreSequentialScoreboard create(DeACoudreSequential game, GlobalWidgets widgets) {
        ServerScoreboard scoreboard = game.world.getServer().getScoreboard();

        Text title = Text.literal("Dé à Coudre").formatted(Formatting.BLUE, Formatting.BOLD);
        SidebarWidget sidebar = widgets.addSidebar(title);

        ScoreboardObjective scoreboardObjective2 = new ScoreboardObjective(
                scoreboard, "de_a_coudre_life",
                ScoreboardCriterion.DUMMY, title,
                ScoreboardCriterion.RenderType.INTEGER,
                false, null);
        scoreboard.addScoreboardObjective(scoreboardObjective2);

        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.LIST, scoreboardObjective2);

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

            content.add(Text.of(String.format("%sTime: %s%02d:%02d", Formatting.RED.toString() + Formatting.BOLD, Formatting.RESET, minutes, seconds)));

            long playersAlive = this.game.participants().size();
            content.add(Text.of(Formatting.BLUE.toString() + playersAlive + " players alive"));
            content.add(Text.of(""));

            ServerPlayerEntity currentJumper = this.game.currentJumper;
            ServerPlayerEntity nextJumper = this.game.getNextJumper();

            if (currentJumper != null) {
                content.add(Text.of("Jumping: " + currentJumper.getName().getString()));
            }
            if (nextJumper != null) {
                content.add(Text.of("Up Next: " + nextJumper.getName().getString()));
            }
        });

        ServerScoreboard scoreboard = this.game.world.getServer().getScoreboard();
        clear(scoreboard, lifeObjective);
        for (Object2IntMap.Entry<ServerPlayerEntity> entry : this.game.lives()) {
            if (entry.getKey() == null) continue;
            ServerPlayerEntity playerEntity = entry.getKey();
            ScoreHolder scoreHolder = ScoreHolder.fromProfile(playerEntity.getGameProfile());
            scoreboard.getOrCreateScore(scoreHolder, lifeObjective)
                .setScore(entry.getIntValue());
        }
    }

    private static void clear(ServerScoreboard scoreboard, ScoreboardObjective objective) {
        Collection<ScoreHolder> existing = scoreboard.getKnownScoreHolders();
        for (ScoreHolder scoreHolder : existing) {
            scoreboard.removeScore(scoreHolder, objective);
        }
    }

    @Override
    public void close() {
        ServerScoreboard scoreboard = this.game.world.getServer().getScoreboard();
        scoreboard.removeObjective(this.lifeObjective);
    }
}
