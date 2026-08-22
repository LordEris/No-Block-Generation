# No Block Generation

Mod **Fabric 1.21.1** qui supprime tous les blocs de terrain à la génération initiale des chunks.
Il ne reste que les **structures** et une poignée d'objets — **géodes, fossiles, donjons** — flottant
dans le vide. Aucune végétation, aucun fluide.

S'applique à **l'Overworld, le Nether et l'End**.

---

## Ce qui est supprimé / gardé

| Supprimé | Gardé |
|---|---|
| Pierre, deepslate, terre, herbe, sable, gravier, argile… | Toutes les structures (villages, temples, forteresses, bastions, end cities, portails en ruine, mineshafts, strongholds, monuments…) |
| Netherrack, soul sand, basalte, blackstone, end stone | Géodes d'améthyste |
| Bedrock (sol du monde et plafond du Nether) | Fossiles |
| **Toute l'eau et toute la lave, sans exception** — océans, mer de lave, aquifères, sources, eau des lush caves, intérieur des monuments et épaves | Donjons, avec leur spawner et leurs coffres |
| **Toute la végétation** — arbres, fleurs, herbes, citrouilles, melons, champignons, plantes de lush cave, coraux, chorus | Piliers d'obsidienne de l'End (`end_spike`), gateways |
| Minerais, disques de sable/gravier, lacs, colonnes de basalte, icebergs, neige et glace | Plateforme d'obsidienne d'arrivée dans l'End *(générée à l'exécution, jamais touchée)* |

Tout est réglable : voir la config plus bas.

## Comment ça marche

La génération d'un chunk passe par des étapes successives. Le mod se greffe **à la fin de l'étape
`FEATURES`** (`ChunkGenerator#applyBiomeDecoration`), et c'est le point clé :

```
NOISE → SURFACE → CARVERS → [ FEATURES ] → INITIALIZE_LIGHT → LIGHT → FULL
                              ↑        ↑
                              │        └── on efface tout le terrain ici
                              └── structures et objets se placent sur du vrai sol
```

* **Trop tôt** (avant `FEATURES`) : géodes, fossiles et donjons cherchent de la pierre où se loger,
  et les structures calent leur altitude sur le relief. Sans sol, rien ne se place.
* **Trop tard** (après `LIGHT`) : la lumière serait calculée sur un monde plein puis deviendrait fausse.

Pendant la décoration, le mod enregistre chaque position écrite par une structure ou une feature,
via l'unique point de passage qu'est `WorldGenRegion#setBlock`. Tout ce qui **n'a pas** été
enregistré est, par construction, du terrain brut : les étapes de bruit, de surface et de creusage
écrivent directement dans les sections du chunk sans jamais passer par là. Le nettoyage final vide
donc simplement tout ce qui n'est pas marqué, puis recalcule les heightmaps.

Les features imbriquées sont gérées avec une pile : c'est la feature **la plus interne** qui décide,
ce qui rend les features conteneurs transparentes.

Les fluides échappent à ce mécanisme : ils sont retirés **inconditionnellement**.
L'eau arrive dans un chunk par trop de chemins pour être traquée un à un — le bruit du terrain, les
aquifères, les sources, les lacs, l'eau qu'un lush cave pose sous sa mousse — et un seul oubli laisse
une nappe suspendue dans le vide. Un second masque note ce qu'écrivent les structures, ce qui permet
de traiter séparément l'eau du paysage et celle d'un monument — les deux étant vidées par défaut.

Ce nettoyage est un **instantané pris à la génération**, pas une règle permanente : le mod ne
repasse jamais sur un chunk existant. L'eau qu'un joueur pose ensuite, ou qui s'écoule après un
update de bloc, n'est pas concernée.

## Config

Créée au premier lancement dans `config/no-block-generation.json`.

```jsonc
{
  "enabled": true,

  // "*" pour toutes les dimensions, y compris moddées
  "dimensions": ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"],

  // Garder tout ce que posent les structures
  "keepStructures": true,

  // Garder ce que posent les features, filtré par keptFeatures ci-dessous
  "keepFeatures": true,

  // Les SEULES features dont les blocs survivent. Vider cette liste rebascule sur
  // strippedFeatures (on garde tout sauf ce qui y est listé).
  "keptFeatures": ["minecraft:geode", "minecraft:fossil", "minecraft:monster_room",
                   "minecraft:desert_well", "minecraft:end_spike", "minecraft:end_gateway",
                   "minecraft:end_platform", "minecraft:bonus_chest"],

  // Fluides hors structures : océans, mer de lave, aquifères, sources, eau des lush caves
  "keepTerrainFluids": false,

  // Fluides posés par une structure : intérieur d'un monument, épave, ruine immergée,
  // canal d'irrigation d'un village. Mets à true pour ne vider que le paysage.
  "keepStructureFluids": false,

  // Garder la coque de bedrock (sol du monde + plafond du Nether)
  "keepBedrock": false,

  // Plateforme 3x3 sous le point de spawn, pour ne pas apparaître en chute libre
  "spawnPlatform": true,
  "spawnPlatformBlock": "minecraft:bedrock",

  // Consulté uniquement si keptFeatures est vide
  "strippedFeatures": ["minecraft:ore", "minecraft:disk", "minecraft:lake",
                       "minecraft:freeze_top_layer", ...]
}
```

> Un fichier de config déjà existant n'hérite pas des nouvelles entrées par défaut ajoutées par une
> mise à jour du mod. Après une mise à jour, compare ta liste `strippedFeatures` avec celle générée
> dans un dossier de config vierge, ou supprime le fichier pour le laisser se recréer.

Pour **garder** quelque chose qui disparaît, ajoute son type à `keptFeatures` : `minecraft:tree`
pour les arbres, `minecraft:random_patch` pour les fleurs et l'herbe, `minecraft:dripstone_cluster`
pour la dripstone… La liste complète des types est celle du registre `minecraft:worldgen/feature`.

Deux règles priment sur cette liste et méritent d'être connues :

* **Les structures gagnent toujours**, sauf pour les fluides. Un village qui plante ses propres
  arbres les garde, mais son canal d'irrigation est vidé comme le reste — à moins de passer
  `keepStructureFluids` à `true`.
* **C'est la feature la plus interne qui décide.** Les features conteneurs (`random_selector`,
  `vegetation_patch`, `root_system`) sont transparentes : lister `minecraft:tree` suffit à récupérer
  les arbres, y compris ceux plantés par un `root_system`.

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
