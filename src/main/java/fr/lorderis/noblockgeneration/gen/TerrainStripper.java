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
        KeepMask holder = (KeepMask) chunk;
        BitSet keep = holder.nbg$keepMask();

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

        holder.nbg$finishStripping();

        if (removedAnything) {
            Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
        }
    }

    /** Terrain blocks the config asks to spare regardless of how they were generated. */
    private static boolean isProtected(BlockState state, NbgConfig config) {
        if (config.keepBedrock && state.is(Blocks.BEDROCK)) {
            return true;
        }
        return config.keepTerrainFluids && state.getBlock() instanceof LiquidBlock;
    }
}
