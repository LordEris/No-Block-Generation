package fr.lorderis.noblockgeneration.gen;

import fr.lorderis.noblockgeneration.NoBlockGeneration;
import fr.lorderis.noblockgeneration.config.NbgConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;

/**
 * Drops a small platform under the world spawn, otherwise the very first thing a player does in a
 * stripped world is fall through the void.
 *
 * <p>It cannot be done during world generation: the spawn point is picked before the spawn chunks
 * are generated, so nothing knows yet which chunk will hold it. This runs once the server has
 * finished loading its levels instead.
 */
public final class SpawnPlatform {
    /** 3x3, so the player has room to land whichever way the respawn logic nudges them. */
    private static final int RADIUS = 1;

    private SpawnPlatform() {
    }

    public static void placeIfMissing(MinecraftServer server, NbgConfig config) {
        if (!config.enabled || !config.spawnPlatform) {
            return;
        }
        // Since 1.21.9 the world spawn carries its dimension: it is not always in the Overworld.
        LevelData.RespawnData respawn = server.getRespawnData();
        ServerLevel level = server.getLevel(respawn.dimension());
        if (level == null) {
            level = server.overworld();
        }
        if (!config.appliesToDimension(level.dimension().identifier())) {
            return;
        }

        BlockPos spawn = respawn.pos();
        int y = spawn.getY() - 1;
        if (y < level.getMinY() || y > level.getMaxY()) {
            NoBlockGeneration.LOGGER.warn("World spawn {} leaves no room for a platform, skipping it.", spawn);
            return;
        }

        // Idempotent: a platform that is already there, or anything a player has built at spawn,
        // means there is nothing to do. Bedrock never breaks, so this normally runs exactly once.
        // The same goes for a spawn the game found on top of a kept structure or feature.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos below = new BlockPos(spawn.getX() + dx, y, spawn.getZ() + dz);
                BlockState existing = level.getBlockState(below);
                if (!existing.isAir()) {
                    NoBlockGeneration.LOGGER.info("The world spawn at {}, {}, {} already has {} under it, no spawn platform needed.",
                            spawn.getX(), spawn.getY(), spawn.getZ(), BuiltInRegistries.BLOCK.getKey(existing.getBlock()));
                    return;
                }
            }
        }

        BlockState platform = config.spawnPlatformState();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                level.setBlockAndUpdate(new BlockPos(spawn.getX() + dx, y, spawn.getZ() + dz), platform);
            }
        }

        NoBlockGeneration.LOGGER.info("Placed the spawn platform at {}, {}, {}", spawn.getX(), y, spawn.getZ());
    }
}
