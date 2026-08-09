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

    /** Whether this chunk has already been through its stripping pass. */
    boolean nbg$isStripped();

    /**
     * Closes the chunk to further marking and hands back a snapshot of the marks, or {@code null}
     * when nothing was ever marked.
     *
     * <p>A snapshot rather than the live set: a neighbouring chunk decorating at the same moment
     * can grow the bitset from under the stripping loop, and reading a resized {@link BitSet}
     * without holding its monitor throws.
     */
    BitSet nbg$takeKeepMask();
}
