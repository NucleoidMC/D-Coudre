package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.api.game.GameSpace;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class DeACoudrePool {
    private final BlockBounds bounds;
    private final ServerLevel world;

    private final Map<ServerPlayer, BlockState> playerPalette = new Object2ObjectOpenHashMap<>();

    public DeACoudrePool(ServerLevel world, DeACoudreMap map) {
        this.bounds = map.getPool();
        this.world = world;
    }

    private BlockState getBlockForPlayer(ServerPlayer player) {
        BlockState block = this.playerPalette.get(player);
        if (block == null) {
            RandomSource random = this.world.getRandom();
            block = DeACoudreConfig.PLAYER_PALETTE[random.nextInt(DeACoudreConfig.PLAYER_PALETTE.length)];
            this.playerPalette.put(player, block);
        }
        return block;
    }

    public void putBlockAt(ServerPlayer player, BlockPos pos) {
        BlockState block = this.getBlockForPlayer(player);
        this.world.setBlockAndUpdate(pos, block);
    }

    public void putCoudreAt(BlockPos pos) {
        this.world.setBlockAndUpdate(pos, Blocks.EMERALD_BLOCK.defaultBlockState());
    }

    public boolean canFormCoudreAt(BlockPos pos) {
        return !this.isFreeAt(pos.west()) && !this.isFreeAt(pos.east())
                && !this.isFreeAt(pos.north()) && !this.isFreeAt(pos.south());
    }

    public boolean isFreeAt(BlockPos pos) {
        ServerLevel world = this.world;
        return world.getBlockState(pos) == Blocks.WATER.defaultBlockState();
    }

    public boolean isFull() {
        for (BlockPos pos : this.bounds) {
            if (this.isFreeAt(pos)) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(BlockPos pos) {
        return this.bounds.contains(pos);
    }
}
