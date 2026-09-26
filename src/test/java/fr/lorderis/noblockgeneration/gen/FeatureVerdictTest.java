package fr.lorderis.noblockgeneration.gen;

import fr.lorderis.noblockgeneration.config.NbgConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which feature writes survive, as the decoration of a chunk walks into and out of nested
 * features. The nestings are the ones the 26.3 data uses: a desert well is an {@code overlay} of
 * two inline {@code template}s, a basalt pillar an {@code overlay} of an inline pillar capped by
 * another inline {@code overlay}, and so on.
 */
class FeatureVerdictTest {
    private NbgConfig config;
    private DecorationTracker.Context context;

    @BeforeEach
    void start() {
        this.config = new NbgConfig();
        DecorationTracker.start(this.config, null);
        this.context = DecorationTracker.active();
    }

    @AfterEach
    void clear() {
        DecorationTracker.clear();
    }

    private boolean enter(String type, String id) {
        this.context.pushFeature(type, id);
        return this.context.shouldKeepWrites();
    }

    private void leave() {
        this.context.popFeature();
    }

    /** Must come before the first verdict: the config reads its lists once. */
    private void useStrippedFeatures() {
        this.config.keptFeatures = new ArrayList<>();
    }

    @Test
    void nothingIsKeptOutsideFeaturesAndStructures() {
        assertFalse(this.context.shouldKeepWrites());
    }

    @Test
    void keptTypesAreKept() {
        assertTrue(enter("minecraft:geode", "minecraft:amethyst_geode"));
        leave();
        assertTrue(enter("minecraft:fossil", "minecraft:fossil_coal"));
        leave();
        assertTrue(enter("minecraft:monster_room", "minecraft:monster_room"));
    }

    @Test
    void theDesertWellIsKeptThroughItsIdWithItsInlineTemplates() {
        assertTrue(enter("minecraft:overlay", "minecraft:desert_well"));
        assertTrue(enter("minecraft:template", null));
        leave();
        assertTrue(enter("minecraft:template", null));
        leave();
        leave();
        assertFalse(this.context.shouldKeepWrites());
    }

    @Test
    void theGenericTypesOfTheWellKeepNothingElse() {
        // Kept through the id only: another overlay or template is not a desert well.
        assertFalse(enter("minecraft:overlay", "minecraft:weeping_vines"));
        assertFalse(enter("minecraft:block_column", null));
        leave();
        leave();
        assertFalse(enter("minecraft:template", "minecraft:some_other_template"));
    }

    @Test
    void treesAndPlantsAreRemoved() {
        assertFalse(enter("minecraft:random_selector", "minecraft:trees_plains"));
        assertFalse(enter("minecraft:tree", "minecraft:fancy_oak_bees_005"));
        leave();
        leave();
        assertFalse(enter("minecraft:simple_block", null));
    }

    @Test
    void aRegisteredFeatureIsJudgedOnItsOwnEvenInsideAKeptOne() {
        assertTrue(enter("minecraft:overlay", "minecraft:desert_well"));
        assertFalse(enter("minecraft:tree", "minecraft:oak"));
        leave();
        assertTrue(this.context.shouldKeepWrites());
    }

    @Test
    void entriesWithoutNamespaceAreMinecraftOnes() {
        this.config.keptFeatures = new ArrayList<>(List.of("tree", " desert_well "));
        assertTrue(enter("minecraft:tree", "minecraft:oak"));
        leave();
        assertTrue(enter("minecraft:overlay", "minecraft:desert_well"));
        leave();
        assertFalse(enter("minecraft:geode", "minecraft:amethyst_geode"));
    }

    @Test
    void structuresKeepEverythingTheyPlace() {
        this.context.pushStructure();
        assertTrue(enter("minecraft:tree", "minecraft:oak"));
        leave();
        this.context.popStructure();
        assertFalse(enter("minecraft:tree", "minecraft:oak"));
    }

    @Test
    void keepFeaturesOffRemovesEvenListedFeatures() {
        this.config.keepFeatures = false;
        assertFalse(enter("minecraft:geode", "minecraft:amethyst_geode"));
    }

    @Test
    void strippedFeaturesKeepWhatIsNotListed() {
        useStrippedFeatures();
        assertTrue(enter("minecraft:random_selector", "minecraft:trees_plains"));
        assertTrue(enter("minecraft:tree", "minecraft:fancy_oak_bees_005"));
        leave();
        leave();
        assertTrue(enter("minecraft:simple_block", null));
        leave();
        assertFalse(enter("minecraft:ore", "minecraft:ore_diamond"));
    }

    @Test
    void aStrippedRootSystemKeepsTheTreeItPlants() {
        useStrippedFeatures();
        assertFalse(enter("minecraft:root_system", "minecraft:rooted_azalea_tree"));
        assertTrue(enter("minecraft:tree", "minecraft:azalea_tree"));
        leave();
        assertFalse(this.context.shouldKeepWrites());
    }

    @Test
    void aStrippedBasaltPillarTakesItsInlinePartsAlong() {
        useStrippedFeatures();
        assertFalse(enter("minecraft:overlay", "minecraft:basalt_pillar"));
        assertFalse(enter("minecraft:single_block_pillar", null));
        assertFalse(enter("minecraft:overlay", null));
        assertFalse(enter("minecraft:projected_random_patchy_square", null));
        leave();
        leave();
        leave();
        leave();
        assertTrue(enter("minecraft:overlay", "minecraft:weeping_vines"));
        assertTrue(enter("minecraft:block_column", null));
    }

    @Test
    void deepNestingGrowsTheStack() {
        assertTrue(enter("minecraft:overlay", "minecraft:desert_well"));
        for (int i = 0; i < 40; i++) {
            assertTrue(enter("minecraft:overlay", null));
        }
        for (int i = 0; i < 40; i++) {
            leave();
        }
        assertTrue(this.context.shouldKeepWrites());
        leave();
        assertFalse(this.context.shouldKeepWrites());
    }
}
