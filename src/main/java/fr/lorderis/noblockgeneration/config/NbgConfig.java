package fr.lorderis.noblockgeneration.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import fr.lorderis.noblockgeneration.NoBlockGeneration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
     * Keep the water and lava found outside structures: oceans, the Nether lava sea, aquifers,
     * springs, the layer a lush cave patch lays under its moss. With ground gone they have nothing
     * to rest on, so they spill everywhere on chunk load — spectacular but very heavy on large
     * oceans. Off by default.
     */
    public boolean keepTerrainFluids = false;

    /**
     * Keep the water and lava a structure placed itself: the inside of an ocean monument, a flooded
     * ruin, a shipwreck, a village irrigation channel. Separate from {@link #keepTerrainFluids}
     * because it is the one case where the water is part of the build rather than part of the
     * landscape. Off by default, so a monument comes out drained like everything else.
     */
    public boolean keepStructureFluids = false;

    /**
     * Stop water and lava from spreading at all. In a world with nothing to hold them, any source
     * that survives — a bucket, a structure left flooded by config, an ocean in a chunk generated
     * before the mod was installed — drains endlessly into the void.
     *
     * <p>Existing sources are not deleted, only frozen in place. This affects every fluid in the
     * targeted dimensions, so water elevators, farmland hydration by flow and anything else built
     * on flowing liquid stop working too.
     */
    public boolean preventFluidSpread = true;

    /** Keep the bedrock shell, so the world still has a floor and a Nether ceiling. */
    public boolean keepBedrock = false;

    /** Put a 3x3 platform under the world spawn so the player does not start in free fall. */
    public boolean spawnPlatform = true;

    /** Block the spawn platform is made of. Falls back to bedrock if the id is unknown. */
    public String spawnPlatformBlock = "minecraft:bedrock";

    /**
     * When non-empty, the ONLY features whose blocks survive. Everything else a feature places is
     * removed, which is how a world ends up with its structures and its geodes but not a single
     * tree, flower, pumpkin or lush cave plant. Empty it to fall back to {@link #strippedFeatures},
     * which keeps everything except what is listed there.
     *
     * <p>An entry is either a feature type ({@code minecraft:geode}, {@code minecraft:tree}, from
     * the {@code worldgen/feature_type} registry) or the id of one feature ({@code
     * minecraft:desert_well}, {@code minecraft:fossil_coal}, from {@code worldgen/feature}). Since
     * 26.3 some features are only an assembly of generic building blocks (a desert well is an
     * {@code overlay} of two {@code template}s), and only their id says what they are.
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
     * "ground" and are removed even though features are kept. Types and ids, as for
     * {@link #keptFeatures}.
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
            // Sulfur caves (26.2): pools and springs are ground and fluid, like lakes
            "minecraft:sulfur_pool",
            "minecraft:sulfur_spring",
            // Nether terrain
            "minecraft:netherrack_replace_blobs",
            "minecraft:small_basalt_columns",
            "minecraft:large_basalt_columns",
            "minecraft:basalt_pillar",
            "minecraft:delta_feature",
            "minecraft:glowstone_extra",
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

    public boolean appliesToDimension(Identifier dimension) {
        Set<String> cache = this.dimensionCache;
        if (cache == null) {
            cache = normalize(this.dimensions);
            this.dimensionCache = cache;
        }
        return cache.contains("*") || cache.contains(dimension.toString());
    }

    /**
     * Whether the blocks a feature writes itself survive the stripping pass.
     *
     * <p>A feature listed by type or id settles it. Otherwise a registered feature, or a top-level
     * one, gets the default of the current mode (removed with {@link #keptFeatures}, kept with
     * {@link #strippedFeatures}), and a feature written inline inside another one follows the
     * feature it belongs to: the {@code template} pieces of a desert well are the well.
     *
     * @param type the feature type, {@code null} if it is not registered
     * @param id the feature id, {@code null} for an inline feature
     * @param nested whether the feature is placed by another feature
     * @param enclosingKeeps the verdict of that enclosing feature
     */
    public boolean keepsFeature(Identifier type, Identifier id, boolean nested, boolean enclosingKeeps) {
        Set<String> allowed = this.keptCache;
        if (allowed == null) {
            allowed = normalize(this.keptFeatures);
            this.keptCache = allowed;
        }
        boolean allowList = !allowed.isEmpty();
        Set<String> listed = allowed;
        if (!allowList) {
            listed = this.strippedCache;
            if (listed == null) {
                listed = normalize(this.strippedFeatures);
                this.strippedCache = listed;
            }
        }

        if ((type != null && listed.contains(type.toString())) || (id != null && listed.contains(id.toString()))) {
            return allowList;
        }
        if (id != null || !nested) {
            return !allowList;
        }
        return enclosingKeeps;
    }

    /** The block state the spawn platform is built from. */
    public BlockState spawnPlatformState() {
        Identifier id = this.spawnPlatformBlock == null ? null : Identifier.tryParse(this.spawnPlatformBlock);
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
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
