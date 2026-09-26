package fr.lorderis.noblockgeneration;

import fr.lorderis.noblockgeneration.config.NbgConfig;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Dimensions already announced this server session, so the line below is logged once each. */
    private static final Set<String> announced = ConcurrentHashMap.newKeySet();

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

    /**
     * Called when a server starts loading its worlds, before the spawn chunks are generated. The
     * registries are complete by then, datapacks included, so the feature lists of the config can
     * be checked against them.
     */
    public static void onServerStarting(MinecraftServer server) {
        // Cleared so the announcement below reappears for every world.
        announced.clear();
        NbgConfig current = config;
        if (!current.enabled) {
            return;
        }
        Set<String> known = new HashSet<>();
        BuiltInRegistries.FEATURE_TYPE.keySet().forEach(type -> known.add(type.toString()));
        server.registryAccess().lookupOrThrow(Registries.FEATURE).listElementIds()
                .forEach(key -> known.add(key.identifier().toString()));
        for (String entry : current.unknownFeatureEntries(known)) {
            LOGGER.warn("{} is neither a feature type nor a feature id, it matches nothing.", entry);
        }
    }

    /**
     * Says once per dimension that chunks really are being stripped. Without it, a world that
     * generates normally gives no way to tell whether the hook never fired, the dimension is not
     * targeted, or the chunks were simply generated before the mod was installed.
     */
    public static void announceFirstStrip(Identifier dimension, ChunkPos pos) {
        if (announced.add(dimension.toString())) {
            LOGGER.info("Stripping terrain in {} - first chunk at {}", dimension, pos);
        }
    }

    /** Whether newly generated chunks of this level should have their terrain stripped. */
    public static boolean appliesTo(WorldGenLevel level) {
        NbgConfig current = config;
        return current.enabled && current.appliesToDimension(level.getLevel().dimension().identifier());
    }
}
