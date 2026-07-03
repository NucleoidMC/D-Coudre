package fr.catcore.deacoudre.game.sequential;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

public final class DeACoudrePlayerLives implements Iterable<Object2IntMap.Entry<ServerPlayer>> {
    private final Object2IntOpenHashMap<ServerPlayer> map = new Object2IntOpenHashMap<>();

    public void addPlayers(Set<ServerPlayer> players, int lives) {
        for (ServerPlayer player : players) {
            this.map.put(player, lives);
            player.setExperienceLevels(lives);
        }
    }

    public int grantLife(ServerPlayer player) {
        int remaining = this.map.addTo(player, 1) + 1;
        player.setExperienceLevels(remaining);
        return remaining;
    }

    public int takeLife(ServerPlayer player) {
        int remaining = this.map.addTo(player, -1) - 1;
        remaining = Math.max(remaining, 0);
        player.setExperienceLevels(remaining);
        return remaining;
    }

    public void removePlayer(ServerPlayer player) {
        this.map.removeInt(player);
    }

    @Override
    public Iterator<Object2IntMap.Entry<ServerPlayer>> iterator() {
        return this.map.object2IntEntrySet().fastIterator();
    }
}
