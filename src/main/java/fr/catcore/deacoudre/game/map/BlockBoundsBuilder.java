package fr.catcore.deacoudre.game.map;

import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.function.Consumer;

public class BlockBoundsBuilder implements Consumer<BlockPos> {
    private final Consumer<BlockPos> delegate;

    private BlockBounds bounds = null;

    public BlockBoundsBuilder(Consumer<BlockPos> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void accept(BlockPos pos) {
        var addedBounds = BlockBounds.ofBlock(pos.toImmutable());
        this.bounds = this.bounds == null ? addedBounds : this.bounds.union(addedBounds);

        this.delegate.accept(pos);
    }

    public BlockBounds getBounds() {
        return this.bounds;
    }
}
