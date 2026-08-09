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
| Neige et glace de surface (`freeze_top_layer`, qui réécrit aussi le bloc de sol dessous) | |
| Bedrock (sol du monde et plafond du Nether) | Piliers d'obsidienne de l'End (`end_spike`), gateways |
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

  // Plateforme 3x3 sous le point de spawn, pour ne pas apparaître en chute libre
  "spawnPlatform": true,
  "spawnPlatformBlock": "minecraft:bedrock",

  // Features dont les blocs comptent comme du "sol" et sont retirés malgré keepFeatures
  "strippedFeatures": ["minecraft:ore", "minecraft:disk", "minecraft:lake",
                       "minecraft:freeze_top_layer", ...]
}
```

> Un fichier de config déjà existant n'hérite pas des nouvelles entrées par défaut ajoutées par une
> mise à jour du mod. Après une mise à jour, compare ta liste `strippedFeatures` avec celle générée
> dans un dossier de config vierge, ou supprime le fichier pour le laisser se recréer.

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
  enlèves de `strippedFeatures`. Revers de la médaille : un filon qui déborde sur un chunk voisin
  **déjà nettoyé** échappe aussi à l'annulation, donc quelques fragments de minerai peuvent flotter
  le long des frontières de chunks. Les features de sol qui passent par `setBlock` (disques, lacs,
  sources) sont bien annulées dans ce cas.
* **Le spawn.** Le monde n'a plus de sol du tout, bedrock comprise, donc le mod pose une plateforme
  3x3 en bedrock sous le point de spawn. Elle ne peut pas être posée pendant la worldgen : le point
  de spawn est choisi *avant* que les chunks de spawn soient générés, donc rien ne sait encore quel
  chunk va le contenir. Elle est donc posée à la fin du chargement des mondes par le serveur, une
  seule fois — si les 9 blocs ne sont pas vides, le mod ne touche à rien.
* **L'End** : l'île centrale disparaît, les piliers d'obsidienne restent en l'air. La plateforme
  d'obsidienne d'arrivée et le portail de sortie sont générés à l'exécution, ils sont intacts.
* Le mod n'agit que sur les **nouveaux** chunks. Les chunks déjà générés d'un monde existant ne sont
  pas modifiés. Les chunks rejoués par le *below-zero retrogen* (monde créé avant la 1.18 puis
  remonté) sont explicitement ignorés : leurs blocs ne viennent pas de cette génération et les
  effacer détruirait le terrain d'origine et les constructions du joueur.
* **Les mondes en mode debug ne sont pas touchés** : `DebugLevelSource` remplace l'étape de
  décoration, donc le point d'accroche du mod n'y existe pas.
* **Marques perdues si un chunk est déchargé avant sa propre décoration.** Les blocs qu'un voisin
  déjà décoré a débordés dans un chunk sont mémorisés en RAM uniquement. Si ce chunk est sauvegardé
  et déchargé avant d'être décoré à son tour, puis rechargé, ces blocs ne sont plus reconnus et
  disparaissent — un arbre coupé net à la frontière. Rare, cosmétique, et corriger demanderait de
  sérialiser le masque dans le NBT du chunk.
* **Coût de génération.** Chaque chunk est parcouru bloc par bloc puis ses heightmaps sont
  recalculées. La génération est sensiblement plus lente que le vanilla ; c'est perceptible sur une
  prégénération de grande zone.
* **Légèrement moins de petite végétation aux bordures de chunks.** Un chunk est nettoyé à la fin de
  sa propre décoration, donc un chunk voisin décoré plus tard y voit déjà du vide. Les structures et
  les arbres n'en souffrent pas (leur position est décidée avant, et ils écrivent sans condition),
  mais les features qui essaient plusieurs positions autour de leur origine — touffes d'herbe,
  fleurs — en placent un peu moins quand elles débordent sur un voisin déjà vidé.
* Les plantes conservées n'ont plus de bloc support : elles cassent au premier update de voisinage.
