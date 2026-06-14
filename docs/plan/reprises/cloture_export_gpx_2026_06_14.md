# Clôture — export GPX a posteriori de la trace de session

> **Nature** : document de clôture, non normatif. Il consigne le chantier d'export
> GPX de la trace parcourue. **Aucun moteur GPS, seuil, logique d'accumulation,
> contrat moteur, test métier ni schéma JSONL modifié.** Le changement est un ajout
> de sortie (export). La clôture est **actée** : la validation verte est acquise
> (voir §4).

## 1. Contexte

- L'application journalise déjà, par session, un fichier JSONL d'observabilité
  (un tick par seconde) exporté dans `Downloads/gpslogs/` à l'arrêt du service
  (`TickLogSink` → `FileTickLogSink` → `TickLogExporter`).
- Chaque ligne `tick` porte `lat`, `lon`, `accuracy_m`, `sample_ts_ms`
  (cf. contrat `observabilite_jsonl_v0_1.md`).
- Le besoin : un **artefact de sortie a posteriori** — un GPX de la trace réellement
  parcourue, consultable hors course, comme souvenir/historique. C'est exactement
  le sous-ensemble « export » de l'idée `docs/features/trace_rallye_osm.md`, dont
  la partie **carte** reste hors périmètre.

## 2. Résumé des changements

Commit `c314397` — `feat(export): add a-posteriori GPX export on session end` :

- **`domain/diag/GpxWriter`** (domaine pur, zéro dépendance) : sérialise une
  séquence de `TickLogEntry` en GPX 1.1 (`trk` / `trkseg` / `trkpt`), `time` si
  disponible, échappement XML, rejet des points invalides. Entièrement testable JVM.
- **`android/diag/GpxExporter`** (infrastructure) : lit le JSONL de session,
  le parse via `TickLogJsonl.parseEntry`, génère le GPX via `GpxWriter`, l'écrit
  dans `Downloads/gpslogs/` via MediaStore (Android 10+).
- **`TripMeterForegroundService.onDestroy`** : déclenche l'export GPX juste après
  l'export JSONL existant (3 lignes), donc **uniquement à l'arrêt de session**.
- **`GpxWriterTest`** : 9 tests (structure GPX, `time` ISO 8601 UTC présent/absent,
  échappement XML, rejet null/NaN/hors bornes, trace vide → null, séparateur point).

Aucune seconde acquisition GPS : la trace est **dérivée du JSONL déjà écrit**.

## 3. Caractéristiques de l'export (état validé)

- **Dérivé du JSONL de session existant** — pas de voie d'acquisition parallèle.
- **Déclenché à l'arrêt de session** (`onDestroy`), strictement **a posteriori**.
- **Écrit dans `Downloads/gpslogs/`**, même dossier et même base de nom que le JSONL
  (`gpslog_AAAAMMJJ_HHMMSS.gpx`), accessible via l'app Fichiers et Termux.
- **Contenu GPX** : `<trk>` / `<trkseg>` / `<trkpt lat lon>` par échantillon
  exploitable, `<time>` (ISO 8601 UTC) si `sample_ts_ms` présent.
- **Altitude (`<ele>`) non exportée** : non portée par le schéma JSONL v1
  (`TickLogEntry` n'a pas d'altitude). `<ele>` est optionnel en GPX ; son absence
  est conforme. L'ajouter exigerait de modifier le système de logs — hors périmètre.
- **N'intervient jamais dans le calcul de distance** : le GPX est une sortie ;
  le moteur d'accumulation et `TripState` sont inchangés.
- **Plateforme** : Android 10+ (comme l'export JSONL) ; pas d'export sur Android 9.
- **Trace vide** : si aucun point exploitable, aucun fichier n'est créé.

## 4. Résultats de validation

Validation autoritative **acquise** côté mainteneur pour le commit `c314397` :

- **Gradle local** : **OK** — `./gradlew.bat :app:testDebugUnitTest` et
  `./gradlew.bat :app:assembleDebug` verts.
- **CI GitHub Actions** : **verte**.
- **Test appareil** : l'export GPX fonctionne ; le `.gpx` est produit après arrêt
  de session, à côté du `.jsonl`, dans `Downloads/gpslogs/`.

## 5. Interdiction de carte temps réel — intacte

Cet incrément **ne touche pas** à l'interdiction ferme du contrat fonctionnel, qui
demeure inconditionnelle :

- **aucune carte** pendant une session active (`Running` / `Paused`) ;
- **aucune position live, aucun guidage, aucune aide à la navigation** en cours ;
- la trace est **exploitable uniquement a posteriori**, après l'arrêt ou la fin de
  la session.

L'export GPX est précisément un artefact a posteriori : il ne produit ni n'affiche
rien pendant la course.

## 6. Mise en cohérence documentaire (ce chantier)

- **`docs/features/trace_rallye_osm.md`** : le statut distingue désormais l'**export
  GPX (engagé et validé)** de la **carte de relecture (non engagée)**.
- **`docs/contrats/contrat_fonctionnel_v0_2.md`** : la note « trace a posteriori ≠
  carte » acte que l'**export** est livré, la **visualisation cartographique**
  restant non engagée ; l'interdiction ferme est explicitement laissée inchangée.
- **`docs/contrats/observabilite_jsonl_v0_1.md`** : **non modifié** — le schéma JSONL
  est inchangé ; le GPX en est seulement un consommateur.

## 7. Verdict

- **Chantier export GPX a posteriori clôturé** : la fonctionnalité est livrée,
  validée sur appareil, et la documentation reflète l'état réel.
- Le projet dispose désormais d'un **artefact de sortie** de la trace (souvenir /
  historique), sans aucune cartographie ni assistance à la course.

## 8. Suite recommandée

- **Ne pas ouvrir** de nouveau chantier par ce document.
- Éventuel chantier ultérieur **distinct** (non engagé) : une **carte de relecture
  a posteriori** de la trace GPX (rendu hors session). Elle resterait soumise à
  l'interdiction ferme : aucune carte pendant une session active. À cadrer
  séparément si elle est un jour planifiée — ce document ne l'engage pas.
- Restent par ailleurs ouverts et distincts : P6.a (campagne terrain finale) et
  P6.c (doctrine de clôture du filtre).

---

Clôture documentaire de l'export GPX a posteriori. Aucun code applicatif, test,
JSONL ou seuil modifié par ce document ; le changement (`c314397`) et sa validation
(Gradle local + CI verte + test appareil) sont acquis côté mainteneur.
