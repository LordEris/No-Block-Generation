package fr.lorderis.noblockgeneration.gen;

import fr.lorderis.noblockgeneration.config.NbgConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;

/**
 * Clears every block of a freshly decorated chunk that was not marked as worth keeping.
 */
public final class TerrainStripper {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /**
     * Both the world-gen heightmaps and the regular ones: the regular ones were primed by the
     * FEATURES step just before decoration ran, and the world-gen ones are still whatever the noise
     * step left behind. Both describe a surface that no longer exists once we are done.
     */
    private static final Set<Heightmap.Types> HEIGHTMAPS = EnumSet.of(
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.WORLD_SURFACE_WG);

    private TerrainStripper() {
    }

    public static void strip(ChunkAccess chunk, NbgConfig config) {
        BitSet keep = ((KeepMask) chunk).nbg$takeKeepMask();

        int minBuildHeight = chunk.getMinBuildHeight();
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean removedAnything = false;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) {
                continue;
            }
            int sectionBottomY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));

            for (int localY = 0; localY < 16; localY++) {
                int worldY = sectionBottomY + localY;
                int layerIndex = (worldY - minBuildHeight) << 8;

                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (state.isAir()) {
                            continue;
                        }
                        if (keep != null && keep.get(layerIndex | (localZ << 4) | localX)) {
                            continue;
                        }
                        if (isProtected(state, config)) {
                            continue;
                        }
                        if (state.hasBlockEntity()) {
                            chunk.removeBlockEntity(cursor.set(originX + localX, worldY, originZ + localZ));
                        }
                        section.setBlockState(localX, localY, localZ, AIR, false);
                        removedAnything = true;
                    }
                }
            }
        }

        if (removedAnything) {
            resetAndPrimeHeightmaps(chunk);
        }
    }

    /**
     * Undoes a write that a later-decorated neighbour dropped into this already-stripped chunk.
     *
     * <p>A chunk is stripped at the end of its own decoration, but its neighbours decorate
     * afterwards and are allowed to write one chunk out. Blocks a kept feature or a structure spills
     * over are welcome; blocks a ground-material feature spills over &mdash; the far half of a sand
     * disk, of a lake bowl, of a spring &mdash; would otherwise sit in the void forever, because
     * this chunk has no stripping pass left to remove them.
     */
    public static void undoLateWrite(ChunkAccess chunk, BlockPos pos, NbgConfig config) {
        LevelChunkSection[] sections = chunk.getSections();
        int sectionIndex = chunk.getSectionIndex(pos.getY());
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return;
        }

        LevelChunkSection section = sections[sectionIndex];
        int localX = pos.getX() & 15;
        int localY = pos.getY() & 15;
        int localZ = pos.getZ() & 15;

        BlockState state = section.getBlockState(localX, localY, localZ);
        if (state.isAir() || isProtected(state, config)) {
            return;
        }
        if (state.hasBlockEntity()) {
            chunk.removeBlockEntity(pos);
        }
        section.setBlockState(localX, localY, localZ, AIR, false);
    }

    /**
     * Priming alone is not enough: {@link Heightmap#primeHeightmaps} only writes a height for
     * columns where it finds a matching block, so a column emptied down to bedrock keeps whatever
     * height it had before the wipe. That ghost surface would be saved to the region file, shipped
     * to the client, and used by mob spawning, rain and lightning.
     */
    private static void resetAndPrimeHeightmaps(ChunkAccess chunk) {
        for (Heightmap.Types type : HEIGHTMAPS) {
            // An all-zero backing array reads back as minBuildHeight for every column, which is the
            // right answer for an empty one.
            Arrays.fill(chunk.getOrCreateHeightmapUnprimed(type).getRawData(), 0L);
        }
        Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
    }

    /** Terrain blocks the config asks to spare regardless of how they were generated. */
    private static boolean isProtected(BlockState state, NbgConfig config) {
        if (config.keepBedrock && state.is(Blocks.BEDROCK)) {
            return true;
        }
        return config.keepTerrainFluids && state.getBlock() instanceof LiquidBlock;
    }
}
