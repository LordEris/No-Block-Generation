package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.gen.KeepMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.BitSet;

/**
 * Attaches the "keep this block" mask to every chunk, so it lives and dies with the chunk instead
 * of in a global map that would need its own eviction rules.
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements KeepMask {
    @Unique
    private volatile BitSet nbg$keepMask;

    @Unique
    private volatile boolean nbg$stripped;

    @Override
    public void nbg$mark(int index, boolean keep) {
        if (this.nbg$stripped) {
            return;
        }
        BitSet mask = this.nbg$keepMask;
        if (mask == null) {
            if (!keep) {
                return;
            }
            synchronized (this) {
                mask = this.nbg$keepMask;
                if (mask == null) {
                    mask = new BitSet();
                    this.nbg$keepMask = mask;
                }
            }
        }
        synchronized (mask) {
            if (keep) {
                mask.set(index);
            } else {
                mask.clear(index);
            }
        }
    }

    @Override
    public void nbg$prepareForDecoration() {
        this.nbg$stripped = false;
    }

    @Override
    public BitSet nbg$keepMask() {
        return this.nbg$keepMask;
    }

    @Override
    public void nbg$finishStripping() {
        this.nbg$stripped = true;
        this.nbg$keepMask = null;
    }
}
