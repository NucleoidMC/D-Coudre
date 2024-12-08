package fr.catcore.deacoudre.game;

import fr.catcore.deacoudre.game.map.DeACoudreMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.api.game.GameSpace;

import java.util.Map;

public final class DeACoudrePool {
    private final BlockBounds bounds;
    private final ServerWorld world;

    private final Map<ServerPlayerEntity, BlockState> playerPalette = new Object2ObjectOpenHashMap<>();

    public DeACoudrePool(ServerWorld world, DeACoudreMap map) {
        this.bounds = map.getPool();
        this.world = world;
    }

    private BlockState getBlockForPlayer(ServerPlayerEntity player) {
        BlockState block = this.playerPalette.get(player);
        if (block == null) {
            Random random = this.world.random;
            block = DeACoudreConfig.PLAYER_PALETTE[random.nextInt(DeACoudreConfig.PLAYER_PALETTE.length)];
            this.playerPalette.put(player, block);
        }
        return block;
    }

    public void putBlockAt(ServerPlayerEntity player, BlockPos pos) {
        BlockState block = this.getBlockForPlayer(player);
        this.world.setBlockState(pos, block);
    }

    public void putCoudreAt(BlockPos pos) {
        this.world.setBlockState(pos, Blocks.EMERALD_BLOCK.getDefaultState());
    }

    public boolean canFormCoudreAt(BlockPos pos) {
        return !this.isFreeAt(pos.west()) && !this.isFreeAt(pos.east())
                && !this.isFreeAt(pos.north()) && !this.isFreeAt(pos.south());
    }

    public boolean isFreeAt(BlockPos pos) {
        ServerWorld world = this.world;
        return world.getBlockState(pos) == Blocks.WATER.getDefaultState();
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
