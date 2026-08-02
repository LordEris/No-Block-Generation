package fr.lorderis.noblockgeneration.gen;

import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.BitSet;

/**
 * Implemented on {@link ChunkAccess} through a mixin. Records, per block position of the chunk,
 * whether that position was written by something worth keeping (a structure or a kept feature)
 * during the decoration step.
 *
 * <p>Everything that is <em>not</em> marked is, by definition, raw terrain: the noise/surface/carver
 * steps write straight into the chunk sections and never go through the decoration path.
 */
public interface KeepMask {
    /** Local index of a position inside the chunk, in {@code [0, 256 * height)}. */
    static int index(ChunkAccess chunk, int localX, int worldY, int localZ) {
        return ((worldY - chunk.getMinBuildHeight()) << 8) | (localZ << 4) | localX;
    }

    /** Marks (or unmarks) a position. Ignored once the chunk has already been stripped. */
    void nbg$mark(int index, boolean keep);

    /**
     * Re-arms marking for a chunk that is about to be decorated. Existing marks are kept on
     * purpose: a neighbour that decorated first may already have dropped a tree into this chunk.
     */
    void nbg$prepareForDecoration();

    /** The mask, or {@code null} when nothing has been marked yet. */
    BitSet nbg$keepMask();

    /** Releases the mask and stops any further marking for this chunk. */
    void nbg$finishStripping();
}
