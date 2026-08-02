package fr.lorderis.noblockgeneration;

import fr.lorderis.noblockgeneration.config.NbgConfig;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.level.WorldGenLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point.
 *
 * <p>The mod strips every naturally generated terrain block out of freshly generated chunks and
 * keeps only what world decoration puts on top of it: structures, trees, plants, pumpkins, geodes,
 * fossils, dungeons, ... The stripping happens at the end of the {@code FEATURES} generation step,
 * which is late enough for structures and trees to have been placed on real ground, and early
 * enough for lighting and heightmaps to be computed on the final, empty world.
 */
public final class NoBlockGeneration implements ModInitializer {
    public static final String MOD_ID = "noblockgeneration";
    public static final Logger LOGGER = LoggerFactory.getLogger("No Block Generation");

    private static volatile NbgConfig config = new NbgConfig();

    @Override
    public void onInitialize() {
        config = NbgConfig.loadOrCreate();
        if (config.enabled) {
            LOGGER.info("Terrain stripping enabled for: {}", String.join(", ", config.dimensions));
        } else {
            LOGGER.info("Terrain stripping is disabled in the config, world generation is untouched.");
        }
    }

    public static NbgConfig config() {
        return config;
    }

    /** Whether newly generated chunks of this level should have their terrain stripped. */
    public static boolean appliesTo(WorldGenLevel level) {
        NbgConfig current = config;
        return current.enabled && current.appliesToDimension(level.getLevel().dimension().location());
    }
}
