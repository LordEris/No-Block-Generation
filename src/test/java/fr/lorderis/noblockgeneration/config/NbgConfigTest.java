package fr.lorderis.noblockgeneration.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NbgConfigTest {
    @Test
    void featureEntriesThatMatchNothingAreReported() {
        NbgConfig config = new NbgConfig();
        config.keptFeatures = new ArrayList<>(List.of("minecraft:geode", "desert_well", "minecraft:glowstone_blob"));
        config.strippedFeatures = new ArrayList<>(List.of("minecraft:ore", "minecraft:basalt_columns"));
        Set<String> known = Set.of("minecraft:geode", "minecraft:desert_well", "minecraft:ore");

        assertEquals(List.of("keptFeatures: minecraft:glowstone_blob", "strippedFeatures: minecraft:basalt_columns"),
                config.unknownFeatureEntries(known));
    }
}
