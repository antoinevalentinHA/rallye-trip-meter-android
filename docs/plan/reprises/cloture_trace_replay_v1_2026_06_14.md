# Clôture — V1 relecture de trace a posteriori (visualiseur local)

> **Navigation** — [← index des reprises](README.md) ·
> Actualise : feature [`trace_rallye_osm.md`](../../features/trace_rallye_osm.md),
> cadrage [`trace_replay_cadrage_2026_06_14.md`](../../features/trace_replay_cadrage_2026_06_14.md),
> contrat [`contrat_fonctionnel_v0_2.md`](../../contrats/contrat_fonctionnel_v0_2.md) ·
> [état courant](../../ETAT.md)

> **Nature** : document de clôture, non normatif. Il consigne la V1 du visualiseur
> de trace **hors session**. **Aucun moteur GPS, seuil, accumulation, schéma JSONL,
> export GPX, contrat moteur ni test métier modifié par ce document.** La clôture
> est **actée** : la validation verte est acquise (voir §4).

## 1. Contexte

- Le cadrage `docs/features/trace_replay_cadrage_2026_06_14.md` (phase 1) avait
  tranché une **V1 sans carte** : rendu vectoriel local de la polyligne sur Canvas
  Compose, **sans bibliothèque cartographique, sans réseau, sans clé, sans
  permission nouvelle**.
- Cette V1 est désormais **implémentée et validée sur appareil** : la chaîne
  complète fonctionne de bout en bout — session GPS → JSONL → GPX → copie interne →
  liste des traces → parse GPX → Canvas → résumé.

## 2. Résumé des changements

Commit `1f29cc5` — `feat(replay): add a-posteriori local GPX trace viewer` :

- **Domaine pur** : `GpxTrack` / `GpxTrackPoint` (modèle de trace) et
  `GpxTrackReader` (parseur GPX symétrique de `GpxWriter`, ignore les points
  invalides, calcule début/fin/durée) — testé en JVM (`GpxTrackReaderTest`).
- **Infrastructure** : `TraceFileSource` liste les `.gpx` du répertoire **interne**
  `getExternalFilesDir("gpslogs")` (tri par date, **aucune permission**) et lit/parse
  une trace. `GpxExporter` dépose désormais aussi une **copie interne** du GPX
  (l'export utilisateur vers `Downloads/gpslogs/` est inchangé).
- **UI Compose** : `TraceListScreen` (liste + état vide) et `TraceDetailScreen`
  (polyligne Canvas auto-cadrée + résumé). Navigation **locale sans Navigation
  Compose** dans `TripMeterRoute` (None / List / Detail). Entrée « Traces » au menu
  d'options.

## 3. État livré et validé (V1)

Validé sur appareil :

- **Liste des traces** : énumération du dossier interne, plus récent d'abord,
  message clair si aucune trace.
- **Écran détail** : polyligne de la trace sur **Canvas**, point de départ et point
  d'arrivée, auto-cadrage sur l'emprise lat/lon, respect du ratio, marge.
- **Résumé** : nombre de points, début / fin (si `time`), durée (si calculable).
- **Garde-fous session active** : entrée « Traces » désactivée hors session ;
  l'écran de relecture se referme si une session redevient active ; aucun accès au
  `LocationEngine`, au service ni à la position courante.
- **Aucune dépendance cartographique, aucune permission nouvelle, aucun réseau.**

## 4. Résultats de validation

Validation autoritative **acquise** côté mainteneur pour le commit `1f29cc5` :

- **Gradle local** : **OK** — `testDebugUnitTest` et `assembleDebug` verts.
- **CI GitHub Actions** : **verte**.
- **Test appareil** : APK installé, chaîne complète vérifiée ; l'écran détail
  affiche polyligne, départ/arrivée, points, début/fin, durée.

## 5. Ce que la V1 n'est PAS (important)

La V1 est un **visualiseur de polyligne**, **pas une vraie carte** :

- **pas de fond géographique** ;
- **pas de tuiles** ;
- **pas de réseau** ;
- **pas de repères cartographiques** (villes, routes, échelle géographique) ;
- uniquement **la forme** de la trace, à partir des coordonnées exportées.

L'objectif validé est minimal et assumé : « j'ai terminé mon rallye, je peux revoir
la forme de ma trace ».

## 6. Interdiction de carte live — intacte

Cet incrément **ne touche pas** à l'interdiction ferme du contrat fonctionnel :

- **aucune carte** pendant une session active (`Running` / `Paused`) ;
- **aucune position live**, aucun guidage, aucune aide à la navigation ;
- la relecture est strictement **a posteriori**, hors session, sur fichiers déjà
  écrits.

## 7. Mise en cohérence documentaire (ce chantier)

- **`docs/features/trace_replay_cadrage_2026_06_14.md`** : statut passé de « V1
  proposée » à « V1 réalisée et validée ».
- **`docs/features/trace_rallye_osm.md`** : statut distingue désormais trois niveaux
  — export GPX **validé**, relecture polyligne Canvas **validée**, vrai fond de
  carte **non engagé**.
- **`docs/contrats/contrat_fonctionnel_v0_2.md`** : **non modifié** — la relecture
  polyligne n'est pas une carte ; l'interdiction ferme reste exacte et l'export y est
  déjà acté.

## 8. Verdict

- **V1 de relecture de trace a posteriori clôturée** : livrée, validée sur appareil,
  documentation en cohérence.
- Le projet dispose d'un **visualiseur local de la forme de la trace**, sans
  cartographie ni assistance à la course.

## 9. Suite recommandée

- **Ne pas ouvrir** de nouveau chantier par ce document.
- Éventuel chantier ultérieur **distinct** (non engagé) : une **V2 avec fond de
  carte** (p. ex. osmdroid), qui ajouterait réseau et licence de tuiles et exigerait
  son propre cadrage. À n'envisager que si la relecture vectorielle se révèle
  insuffisante. Elle resterait soumise à l'interdiction ferme (aucune carte en
  session active).
- Restent par ailleurs ouverts et distincts : P6.a (campagne terrain finale) et
  P6.c (doctrine de clôture du filtre).

---

Clôture documentaire de la V1 de relecture de trace a posteriori. Aucun code, test,
JSONL, seuil, moteur ou export GPX modifié par ce document ; le changement
(`1f29cc5`) et sa validation (Gradle local + CI verte + test appareil) sont acquis
côté mainteneur.
