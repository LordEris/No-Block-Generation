package fr.lorderis.noblockgeneration.gen;

import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.BitSet;

/**
 * Implemented on {@link ChunkAccess} through a mixin. Records, per block position of the chunk,
 * what wrote there during the decoration step.
 *
 * <p>Two masks, because the two answers are different. The keep mask says "something worth keeping
 * wrote here". The structure mask says "a structure wrote here", and only structures are allowed to
 * hold on to their water and lava.
 *
 * <p>Everything in neither mask is, by definition, raw terrain: the noise/surface/carver steps write
 * straight into the chunk sections and never go through the decoration path.
 */
public interface KeepMask {
    /** Local index of a position inside the chunk, in {@code [0, 256 * height)}. */
    static int index(ChunkAccess chunk, int localX, int worldY, int localZ) {
        return ((worldY - chunk.getMinY()) << 8) | (localZ << 4) | localX;
    }

    /** Marks (or unmarks) a position. Ignored once the chunk has already been stripped. */
    void nbg$mark(int index, boolean keep);

    /**
     * Marks a position as written by a structure. Never cleared: a feature that later paints over a
     * village floor does not stop that block from belonging to the village.
     */
    void nbg$markStructure(int index);

    /**
     * Re-arms marking for a chunk that is about to be decorated. Existing marks are kept on
     * purpose: a neighbour that decorated first may already have dropped part of a structure in.
     */
    void nbg$prepareForDecoration();

    /** Whether this chunk has already been through its stripping pass. */
    boolean nbg$isStripped();

    /**
     * Closes the chunk to further marking and hands back snapshots of both masks, as
     * {@code [keep, structure]}. Either entry may be {@code null} when nothing was marked.
     *
     * <p>Snapshots rather than the live sets: a neighbouring chunk decorating at the same moment
     * can grow a {@link BitSet} from under the stripping loop, and reading a resized one without
     * holding its monitor throws.
     */
    BitSet[] nbg$takeMasks();
}
