package fr.lorderis.noblockgeneration.mixin;

import fr.lorderis.noblockgeneration.gen.KeepMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.BitSet;

/**
 * Attaches the decoration masks to every chunk, so they live and die with the chunk instead of in a
 * global map that would need its own eviction rules.
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements KeepMask {
    @Unique
    private volatile BitSet nbg$keepMask;

    @Unique
    private volatile BitSet nbg$structureMask;

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
    public void nbg$markStructure(int index) {
        if (this.nbg$stripped) {
            return;
        }
        BitSet mask = this.nbg$structureMask;
        if (mask == null) {
            synchronized (this) {
                mask = this.nbg$structureMask;
                if (mask == null) {
                    mask = new BitSet();
                    this.nbg$structureMask = mask;
                }
            }
        }
        synchronized (mask) {
            mask.set(index);
        }
    }

    @Override
    public void nbg$prepareForDecoration() {
        this.nbg$stripped = false;
    }

    @Override
    public boolean nbg$isStripped() {
        return this.nbg$stripped;
    }

    @Override
    public BitSet[] nbg$takeMasks() {
        BitSet keep;
        BitSet structure;
        synchronized (this) {
            // Closed first, so nothing can be written into either mask while they are being copied.
            this.nbg$stripped = true;
            keep = this.nbg$keepMask;
            structure = this.nbg$structureMask;
            this.nbg$keepMask = null;
            this.nbg$structureMask = null;
        }
        return new BitSet[] { nbg$snapshot(keep), nbg$snapshot(structure) };
    }

    @Unique
    private static BitSet nbg$snapshot(BitSet mask) {
        if (mask == null) {
            return null;
        }
        synchronized (mask) {
            return (BitSet) mask.clone();
        }
    }
}
