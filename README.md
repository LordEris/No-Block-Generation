# No Block Generation

Mod **Fabric 1.21.1** qui supprime tous les blocs de terrain à la génération initiale des chunks.
Il ne reste que ce que la décoration du monde pose par-dessus : **structures, arbres, plantes,
citrouilles, melons, géodes, fossiles, donjons…** le tout flottant dans le vide.

S'applique à **l'Overworld, le Nether et l'End**.

---

## Ce qui est supprimé / gardé

| Supprimé | Gardé |
|---|---|
| Pierre, deepslate, terre, herbe, sable, gravier, argile… | Toutes les structures (villages, temples, forteresses, bastions, end cities, portails en ruine, mineshafts, strongholds, monuments…) |
| Netherrack, soul sand, basalte, blackstone | Arbres, champignons géants, cactus, bambou, vignes, fleurs, herbes, citrouilles, melons |
| End stone | Géodes d'améthyste, fossiles, donjons (avec leur spawner et leurs coffres) |
| Eau des océans, mer de lave du Nether, aquifères | Dripstone, sculk, chorus, coraux |
| Bedrock | Piliers d'obsidienne de l'End (`end_spike`), gateways |
| Filons de minerai, disques de sable/gravier/argile, lacs, sources | Plateforme d'obsidienne d'arrivée dans l'End *(générée à l'exécution, jamais touchée)* |
| Colonnes/piliers de basalte, deltas, blobs de glowstone, icebergs | |

Tout est réglable : voir la config plus bas.

## Comment ça marche

La génération d'un chunk passe par des étapes successives. Le mod se greffe **à la fin de l'étape
`FEATURES`** (`ChunkGenerator#applyBiomeDecoration`), et c'est le point clé :

```
NOISE → SURFACE → CARVERS → [ FEATURES ] → INITIALIZE_LIGHT → LIGHT → FULL
                              ↑        ↑
                              │        └── on efface tout le terrain ici
                              └── structures + arbres se placent sur du vrai sol
```

* **Trop tôt** (avant `FEATURES`) : les arbres ne trouveraient plus de sol et ne pousseraient pas.
* **Trop tard** (après `LIGHT`) : la lumière serait calculée sur un monde plein puis deviendrait fausse.

Pendant la décoration, le mod enregistre chaque position écrite par une structure ou une feature,
via l'unique point de passage qu'est `WorldGenRegion#setBlock`. Tout ce qui **n'a pas** été
enregistré est, par construction, du terrain brut : les étapes de bruit, de surface et de creusage
écrivent directement dans les sections du chunk sans jamais passer par là. Le nettoyage final vide
donc simplement tout ce qui n'est pas marqué, puis recalcule les heightmaps.

Les features imbriquées sont gérées avec une pile : c'est la feature **la plus interne** qui décide.
Supprimer `minecraft:root_system` retire sa terre enracinée mais garde l'azalée qu'elle plante,
parce que l'arbre est une feature à part entière.

## Config

Créée au premier lancement dans `config/no-block-generation.json`.

```jsonc
{
  "enabled": true,

  // "*" pour toutes les dimensions, y compris moddées
  "dimensions": ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"],

  // Garder tout ce que posent les structures
  "keepStructures": true,

  // Garder tout ce que posent les features (arbres, plantes, géodes, fossiles, donjons…)
  "keepFeatures": true,

  // Garder l'eau et la lave produites par le terrain lui-même.
  // À true : les océans et la mer de lave restent posés dans le vide et se déversent au
  // chargement du chunk — spectaculaire, mais très lourd sur les grands océans.
  "keepTerrainFluids": false,

  // Garder la coque de bedrock (sol du monde + plafond du Nether)
  "keepBedrock": false,

  // Features dont les blocs comptent comme du "sol" et sont retirés malgré keepFeatures
  "strippedFeatures": ["minecraft:ore", "minecraft:disk", "minecraft:lake", ...]
}
```

Pour garder quelque chose qui disparaît, retire simplement son type de `strippedFeatures`.
Pour supprimer quelque chose qui reste, ajoute son type — la liste complète des types est celle du
registre `minecraft:worldgen/feature` (`tree`, `geode`, `fossil`, `monster_room`, `random_patch`…).

## Compilation

```bash
./gradlew build
```

Le jar sort dans `build/libs/`. Nécessite un **JDK 21**.

> Le projet utilise les mappings officiels Mojang (`loom.officialMojangMappings()`), c'est pourquoi
> les mixins sont écrits avec les noms Mojang.

## Limitations connues

* **Les minerais ne peuvent pas être conservés.** Vanilla écrit les features `ore` et
  `scattered_ore` directement dans les sections du chunk via `BulkSectionAccess`, sans passer par
  `setBlock`. Elles ne peuvent donc pas être enregistrées et sont toujours retirées, même si tu les
  enlèves de `strippedFeatures`.
* **Le spawn est dans le vide.** Sans sol, tu tombes à l'apparition. Mets `keepBedrock` à `true`, ou
  utilise `/gamemode spectator` puis `/tp` vers une structure pour explorer.
* **L'End** : l'île centrale disparaît, les piliers d'obsidienne restent en l'air. La plateforme
  d'obsidienne d'arrivée et le portail de sortie sont générés à l'exécution, ils sont intacts.
* Le mod n'agit que sur les **nouveaux** chunks. Les chunks déjà générés d'un monde existant ne sont
  pas modifiés.
* Les plantes conservées n'ont plus de bloc support : elles cassent au premier update de voisinage.
