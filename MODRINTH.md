# No Block Generation — Modrinth page

## Summary

> A void world that keeps its structures. All natural terrain, fluids and vegetation are removed at
> generation: only villages, fortresses, end cities, geodes, fossils and dungeons are left floating.
> Fabric 26.3, no Fabric API needed.

## Description

**No Block Generation** removes every naturally generated block from new chunks. No stone, no dirt,
no netherrack, no end stone, no ocean, no tree: what is left are the **structures** and a few hand-
picked objects, floating in the void exactly where the game would have put them.

Villages hang in the sky, strongholds are open to the air, bastions and fortresses float over an
empty Nether, and the End is reduced to its obsidian pillars. It makes for a very different kind of
skyblock: every resource has to be scavenged from structures, and getting from one to the next is
half the challenge.

### What is removed, what is kept

| Removed | Kept |
|---|---|
| All terrain: stone, deepslate, dirt, grass, sand, gravel, clay, sulfur and cinnabar, netherrack, soul sand, basalt, blackstone, end stone | Every structure: villages, temples, fortresses, bastions, end cities, ruined portals, mineshafts, strongholds, ocean monuments, trial chambers, abandoned camps… |
| Bedrock (world floor and Nether ceiling), unless you keep it | Amethyst geodes |
| **All water and lava**: oceans, the lava sea, aquifers, springs, lakes, and the inside of monuments and shipwrecks | Fossils |
| **All vegetation**: trees (poplars included), fallen trees, flowers, grass, bushes, pumpkins, mushrooms, lush cave plants, coral, chorus | Dungeons, with their spawner and chests |
| Ores, sand and gravel disks, lakes, basalt columns, icebergs, snow and ice, sulfur pools and spikes | Desert wells, the End's obsidian pillars and gateways |

Everything is configurable, down to single features.

### Features

- **Structures stay intact**, including the trees and decorations they place themselves.
- **Features are kept or removed individually**, by type (`minecraft:geode`, `minecraft:tree`…) or
  by id (`minecraft:desert_well`, `minecraft:fossil_coal`…).
- **Fluids are frozen in place.** In a world with nothing to hold them, water and lava would drain
  forever into the void; any fluid that survives, or that you place, no longer spreads.
- **Spawn platform.** A small bedrock platform is placed under the world spawn, so the first thing
  you do is not fall into the void.
- **All dimensions.** The Overworld, the Nether and the End by default, modded dimensions on request.
- **Server-side only.** Install it on the server; players join with a vanilla client. Works in
  single-player too.

### Configuration

`config/no-block-generation.json`, created on first launch.

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `dimensions` | Overworld, Nether, End | Dimensions to strip. `"*"` means all of them, modded ones included |
| `keepStructures` | `true` | Keep everything structures place |
| `keepFeatures` | `true` | Keep what features place, filtered by the lists below |
| `keptFeatures` | geodes, fossils, dungeons, desert wells, End pillars, gateways, platform, bonus chest | The **only** features kept, by type or id. Empty it to switch to `strippedFeatures` |
| `strippedFeatures` | ores, disks, lakes, springs, basalt, icebergs… | Only used when `keptFeatures` is empty: keep every feature except these |
| `keepTerrainFluids` | `false` | Keep oceans, the lava sea, aquifers and springs |
| `keepStructureFluids` | `false` | Keep the water inside monuments, shipwrecks and flooded ruins |
| `preventFluidSpread` | `true` | Stop water and lava from flowing |
| `keepBedrock` | `false` | Keep the world floor and the Nether ceiling |
| `spawnPlatform` | `true` | Place a 3×3 platform under the world spawn |
| `spawnPlatformBlock` | `minecraft:bedrock` | What the platform is made of |

Want the trees back? Add `minecraft:tree` to `keptFeatures`. Flowers and grass? `minecraft:simple_block`.
The full lists are the `worldgen/feature_type` and `worldgen/feature` registries.

### Compatibility

- Minecraft **26.3**, Fabric Loader **0.19.5** or newer, Java 25. The Minecraft 1.21.1 version is
  still available as 1.0.0.
- **Fabric API is not required.**
- Built for the reworked 26.3 world generation, and checked against every structure and feature added
  since 1.21, including abandoned camps, poplar forests, fallen trees and the sulfur caves.
- Only **new** chunks are stripped: create a new world, or explore past what is already generated.

### Good to know

- Ores cannot be kept: the game writes them straight into the chunk, where they cannot be told apart
  from terrain.
- Plants that are kept lose their soil and break on the first block update next to them.
- With `preventFluidSpread`, water elevators and anything else that relies on flowing liquid stop
  working in the stripped dimensions.
- Old worlds upgraded from before 1.18 and the debug world are never touched.

### License

[CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/): free to use, modify and share with
credit, but not to sell. **Videos and streams are explicitly allowed, monetised ones included.**
Source code on [GitHub](https://github.com/LordEris/No-Block-Generation).
