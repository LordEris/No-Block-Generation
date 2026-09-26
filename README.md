# No Block Generation

Mod **Fabric pour Minecraft 26.3** qui supprime tous les blocs de terrain à la génération initiale des chunks.
Il ne reste que les **structures** et une poignée d'objets — **géodes, fossiles, donjons** — flottant
dans le vide. Aucune végétation, aucun fluide.

S'applique à **l'Overworld, le Nether et l'End**.

---

## Ce qui est supprimé / gardé

| Supprimé | Gardé |
|---|---|
| Pierre, deepslate, terre, herbe, sable, gravier, argile, soufre et cinabre des grottes de soufre… | Toutes les structures (villages, temples, forteresses, bastions, end cities, portails en ruine, mineshafts, strongholds, monuments, camps abandonnés de la 26.3…) |
| Netherrack, soul sand, basalte, blackstone, end stone | Géodes d'améthyste |
| Bedrock (sol du monde et plafond du Nether) | Fossiles |
| **Toute l'eau et toute la lave, sans exception** — océans, mer de lave, aquifères, sources, eau des lush caves, intérieur des monuments et épaves | Donjons, avec leur spawner et leurs coffres |
| **Toute la végétation** — arbres (peupliers compris), arbres couchés, fleurs, herbes, buissons, citrouilles, melons, champignons, plantes de lush cave, coraux, chorus | Piliers d'obsidienne de l'End (`end_spike`), gateways |
| Minerais, disques de sable/gravier, lacs, colonnes de basalte, icebergs, neige et glace, bassins, sources et pics de soufre | Puits du désert, plateforme d'obsidienne d'arrivée dans l'End *(générée à l'exécution, jamais touchée)* |

Tout est réglable : voir la config plus bas.

## Comment ça marche

La génération d'un chunk passe par des étapes successives. Le mod se greffe **à la fin de l'étape
`FEATURES`** (`ChunkGenerator#applyBiomeDecoration`), et c'est le point clé :

```
TERRAIN → [ FEATURES ] → INITIALIZE_LIGHT → LIGHT → FULL
            ↑        ↑
            │        └── on efface tout le terrain ici
            └── structures et objets se placent sur du vrai sol
```

Depuis la 26.3, les anciennes étapes `NOISE`, `SURFACE` et `CARVERS` n'en font plus qu'une,
`TERRAIN`.

* **Trop tôt** (avant `FEATURES`) : géodes, fossiles et donjons cherchent de la pierre où se loger,
  et les structures calent leur altitude sur le relief. Sans sol, rien ne se place.
* **Trop tard** (après `LIGHT`) : la lumière serait calculée sur un monde plein puis deviendrait fausse.

Pendant la décoration, le mod enregistre chaque position écrite par une structure ou une feature,
via l'unique point de passage qu'est `WorldGenRegion#setBlock`. Tout ce qui **n'a pas** été
enregistré est, par construction, du terrain brut : l'étape `TERRAIN` écrit directement dans les
sections du chunk sans jamais passer par là. Le nettoyage final vide
donc simplement tout ce qui n'est pas marqué, puis recalcule les heightmaps.

Les features imbriquées sont gérées avec une pile, alimentée par `FeaturePlacer`, le point de
passage de toute feature placée depuis la 26.3 : c'est la feature **la plus interne** qui décide, ce
qui rend les features conteneurs transparentes. Une feature écrite *en ligne* dans une autre, sans
identifiant à elle, suit celle qui la contient : les deux `template` d'un puits du désert sont jugés
comme le puits.

Les fluides échappent à ce mécanisme : ils sont retirés **inconditionnellement**.
L'eau arrive dans un chunk par trop de chemins pour être traquée un à un — le bruit du terrain, les
aquifères, les sources, les lacs, l'eau qu'un lush cave pose sous sa mousse — et un seul oubli laisse
une nappe suspendue dans le vide. Un second masque note ce qu'écrivent les structures, ce qui permet
de traiter séparément l'eau du paysage et celle d'un monument — les deux étant vidées par défaut.

Ce nettoyage est un **instantané pris à la génération**, pas une règle permanente : le mod ne
repasse jamais sur un chunk existant. L'eau qu'un joueur pose ensuite n'est pas supprimée.

En revanche, elle ne **coule** plus : `preventFluidSpread` annule `FlowingFluid#spreadTo`, le point
unique par lequel un fluide s'inscrit dans un bloc voisin, écoulement vers le bas compris. Sans ça,
la moindre source rescapée — un seau, une structure laissée inondée par config, un océan dans un
chunk généré avant l'installation du mod — se vide indéfiniment dans le vide et fait tomber le
serveur avec elle.

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

  // Les SEULES features dont les blocs survivent, par type ou par identifiant. Vider cette
  // liste rebascule sur strippedFeatures (on garde tout sauf ce qui y est listé).
  "keptFeatures": ["minecraft:geode", "minecraft:fossil", "minecraft:monster_room",
                   "minecraft:desert_well", "minecraft:end_spike", "minecraft:end_gateway",
                   "minecraft:end_platform", "minecraft:bonus_chest"],

  // Fluides hors structures : océans, mer de lave, aquifères, sources, eau des lush caves
  "keepTerrainFluids": false,

  // Fluides posés par une structure : intérieur d'un monument, épave, ruine immergée,
  // canal d'irrigation d'un village. Mets à true pour ne vider que le paysage.
  "keepStructureFluids": false,

  // Empêcher l'eau et la lave de s'écouler. Ne supprime rien : fige les sources en place.
  // Utile surtout aux frontières d'un monde déjà exploré avant l'installation du mod.
  "preventFluidSpread": true,

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

Pour **garder** quelque chose qui disparaît, ajoute-le à `keptFeatures`, au choix :

* par **type**, pour toute une famille : `minecraft:tree` pour les arbres, `minecraft:fallen_tree`
  pour les arbres couchés, `minecraft:simple_block` pour les fleurs et l'herbe,
  `minecraft:speleothem_cluster` pour la dripstone et les pics de soufre… Liste complète : registre
  `minecraft:worldgen/feature_type`.
* par **identifiant**, pour une feature précise : `minecraft:desert_well`, `minecraft:fossil_coal`,
  `minecraft:pumpkin`, `minecraft:ice_spike`… Liste complète : registre `minecraft:worldgen/feature`.

Depuis la 26.3, beaucoup de features ne sont plus qu'un assemblage de briques génériques
(`overlay`, `template`, `simple_block`…) : seul leur identifiant dit ce qu'elles sont. Le puits du
désert, par exemple, n'a plus de type à lui : il se garde par `minecraft:desert_well`.

Deux règles priment sur cette liste et méritent d'être connues :

* **Les structures gagnent toujours**, sauf pour les fluides. Un village qui plante ses propres
  arbres les garde, mais son canal d'irrigation est vidé comme le reste — à moins de passer
  `keepStructureFluids` à `true`.
* **C'est la feature la plus interne qui décide.** Les features conteneurs (`random_selector`,
  `vegetation_patch`, `root_system`, `overlay`…) sont transparentes : lister `minecraft:tree` suffit à
  récupérer les arbres, y compris ceux plantés par un `root_system`. Seules les features écrites en
  ligne, sans identifiant, héritent de la décision de leur conteneur.

## Compilation

```bash
./gradlew build
```

Le jar sort dans `build/libs/`. Nécessite un **JDK 25**.

> Depuis la 26.1, Minecraft n'est plus obfusqué : le projet n'utilise aucun mapping, les mixins
> visent directement les noms officiels. La version 1.21.1 du mod reste disponible dans la release
> `v1.0.0`.

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
* **`preventFluidSpread` fige tous les fluides** des dimensions ciblées, pas seulement ceux issus de
  la génération. Ascenseurs à eau, hydratation des terres labourées par écoulement et tout ce qui
  repose sur un liquide en mouvement cessent de fonctionner. Passe-le à `false` si tu veux les
  récupérer.

## Licence

**CC BY-NC 4.0** — [texte complet](https://creativecommons.org/licenses/by-nc/4.0/legalcode) ·
[résumé](https://creativecommons.org/licenses/by-nc/4.0/)

Utilisation, modification et redistribution libres tant que c'est **non commercial** et que
LordEris est crédité. Interdit : vendre le mod, vendre des licences ou des clés, l'inclure dans un
modpack payant ou dans un grade de serveur payant.

**Les vidéos sont expressément autorisées**, monétisation comprise — pub, sponsors, adhésions,
dons. La permission porte sur le contenu que tu fais *à propos* du mod, pas sur la distribution du
mod lui-même contre paiement. Voir le fichier [LICENSE](LICENSE).
