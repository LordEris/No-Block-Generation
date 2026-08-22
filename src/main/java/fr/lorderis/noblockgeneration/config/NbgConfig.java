package fr.lorderis.noblockgeneration.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import fr.lorderis.noblockgeneration.NoBlockGeneration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON config, written to {@code config/no-block-generation.json} on first launch.
 */
public final class NbgConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "no-block-generation.json";

    /** Master switch. When false the mod does nothing at all. */
    public boolean enabled = true;

    /**
     * Dimensions the stripping applies to. Use the single entry {@code "*"} to target every
     * dimension, including modded ones.
     */
    public List<String> dimensions = new ArrayList<>(List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    ));

    /** Keep everything placed by a structure (villages, fortresses, end cities, ruined portals, ...). */
    public boolean keepStructures = true;

    /** Keep everything placed by a world feature (trees, plants, pumpkins, geodes, fossils, dungeons, ...). */
    public boolean keepFeatures = true;

    /**
     * Keep the water and lava that the terrain generator itself produced (oceans, the Nether lava
     * sea, aquifers). With ground gone they have nothing to rest on, so they spill everywhere on
     * chunk load — visually spectacular but very heavy on large oceans. Off by default.
     */
    public boolean keepTerrainFluids = false;

    /** Keep the bedrock shell, so the world still has a floor and a Nether ceiling. */
    public boolean keepBedrock = false;

    /** Put a 3x3 platform under the world spawn so the player does not start in free fall. */
    public boolean spawnPlatform = true;

    /** Block the spawn platform is made of. Falls back to bedrock if the id is unknown. */
    public String spawnPlatformBlock = "minecraft:bedrock";

    /**
     * When non-empty, the ONLY feature types whose blocks survive. Everything else a feature places
     * is removed, which is how a world ends up with its structures and its geodes but not a single
     * tree, flower, pumpkin or lush cave plant. Empty it to fall back to {@link #strippedFeatures},
     * which keeps everything except the types listed there.
     *
     * <p>Nested features are judged individually, so this stays predictable: a structure that plants
     * its own trees keeps them, because structures outrank features entirely.
     */
    public List<String> keptFeatures = new ArrayList<>(List.of(
            "minecraft:geode",
            "minecraft:fossil",
            "minecraft:monster_room",
            "minecraft:desert_well",
            "minecraft:end_spike",
            "minecraft:end_gateway",
            "minecraft:end_platform",
            "minecraft:bonus_chest"
    ));

    /**
     * Only consulted when {@link #keptFeatures} is empty. Features whose own blocks count as
     * "ground" and are removed even though features are kept.
     * Nested features are unaffected: stripping {@code minecraft:root_system} removes its rooted
     * dirt but keeps the azalea tree it plants, because the tree is a feature of its own.
     */
    public List<String> strippedFeatures = new ArrayList<>(List.of(
            // ores
            "minecraft:ore",
            "minecraft:scattered_ore",
            "minecraft:replace_single_block",
            // bulk terrain material
            "minecraft:disk",
            "minecraft:lake",
            "minecraft:spring_feature",
            "minecraft:underwater_magma",
            "minecraft:fill_layer",
            "minecraft:vegetation_patch",
            "minecraft:waterlogged_vegetation_patch",
            "minecraft:root_system",
            // Nether terrain
            "minecraft:replace_blobs",
            "minecraft:basalt_columns",
            "minecraft:basalt_pillar",
            "minecraft:delta_feature",
            "minecraft:glowstone_blob",
            // frozen ocean terrain
            "minecraft:iceberg",
            "minecraft:blue_ice",
            // Runs last, over every column of the chunk: it lays a snow layer AND rewrites the
            // ground block below it as snowy, or turns ocean water into ice. Left in, it would mark
            // that whole 16x16 surface plate as decoration and keep it.
            "minecraft:freeze_top_layer",
            // End terrain (the outer islands; end_spike and end_gateway are kept)
            "minecraft:end_island"
    ));

    // Volatile: chunks are decorated on several worker threads, and an immutable set published
    // through a plain field could be seen half-built.
    private transient volatile Set<String> dimensionCache;
    private transient volatile Set<String> strippedCache;
    private transient volatile Set<String> keptCache;

    public boolean appliesToDimension(ResourceLocation dimension) {
        Set<String> cache = this.dimensionCache;
        if (cache == null) {
            cache = normalize(this.dimensions);
            this.dimensionCache = cache;
        }
        return cache.contains("*") || cache.contains(dimension.toString());
    }

    /** Whether the blocks written by this feature type survive the stripping pass. */
    public boolean keepsFeature(Feature<?> feature) {
        ResourceLocation id = BuiltInRegistries.FEATURE.getKey(feature);

        Set<String> allowed = this.keptCache;
        if (allowed == null) {
            allowed = normalize(this.keptFeatures);
            this.keptCache = allowed;
        }
        if (!allowed.isEmpty()) {
            // Allow-list mode. An unregistered feature has no way to be listed, so it goes.
            return id != null && allowed.contains(id.toString());
        }

        Set<String> stripped = this.strippedCache;
        if (stripped == null) {
            stripped = normalize(this.strippedFeatures);
            this.strippedCache = stripped;
        }
        return id == null || !stripped.contains(id.toString());
    }

    /** The block state the spawn platform is built from. */
    public BlockState spawnPlatformState() {
        ResourceLocation id = this.spawnPlatformBlock == null ? null : ResourceLocation.tryParse(this.spawnPlatformBlock);
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        }
        NoBlockGeneration.LOGGER.warn("Unknown spawnPlatformBlock '{}', using bedrock.", this.spawnPlatformBlock);
        return Blocks.BEDROCK.defaultBlockState();
    }

    /** Gson leaves a field null when its key is missing from the file; fall back to the defaults. */
    private NbgConfig sanitize() {
        NbgConfig defaults = new NbgConfig();
        if (this.dimensions == null) {
            this.dimensions = defaults.dimensions;
        }
        if (this.strippedFeatures == null) {
            this.strippedFeatures = defaults.strippedFeatures;
        }
        // A config written by an earlier version has no keptFeatures key at all. Filling it from the
        // defaults is what moves an existing install onto the allow list.
        if (this.keptFeatures == null) {
            this.keptFeatures = defaults.keptFeatures;
        }
        return this;
    }

    private static Set<String> normalize(List<String> raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.add(trimmed.indexOf(':') < 0 && !trimmed.equals("*") ? "minecraft:" + trimmed : trimmed);
        }
        return out;
    }

    public static NbgConfig loadOrCreate() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                NbgConfig loaded = GSON.fromJson(reader, NbgConfig.class);
                if (loaded != null) {
                    return loaded.sanitize();
                }
                NoBlockGeneration.LOGGER.warn("{} is empty, falling back to the defaults.", FILE_NAME);
            } catch (IOException | JsonSyntaxException e) {
                NoBlockGeneration.LOGGER.error("Could not read {}, falling back to the defaults.", FILE_NAME, e);
            }
            return new NbgConfig();
        }

        NbgConfig defaults = new NbgConfig();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, writer);
            }
        } catch (IOException e) {
            NoBlockGeneration.LOGGER.error("Could not write the default {}.", FILE_NAME, e);
        }
        return defaults;
    }
}
