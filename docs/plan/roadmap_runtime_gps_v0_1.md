# Roadmap runtime GPS v0.1

## 1. Statut

- Document de **planification**, non normatif (les contrats `docs/contrats/` restent la référence).
- Basé sur l'audit du **HEAD actuel** (`50e24fd`) du dépôt `antoinevalentinHA/rallye-trip-meter-android`.
- CI Android **déjà en place et verte** : `testDebugUnitTest` + `assembleDebug` (workflow `.github/workflows/android-ci.yml`).
- Objectif du document : permettre de reprendre le chantier runtime GPS plus tard sans dépendre d'un historique de conversation.

## 2. Contexte

- Application Android **Kotlin / Jetpack Compose**, package `fr.arsenal.rallyetripmeter`.
- Trip meter de rallye orienté navigation au roadbook (cf. `contrat_fonctionnel_v0_2`).
- Architecture en place, séparation stricte :
  - domaine pur (modèle, contrôleur, distance, progression) ;
  - `LocationEngine` (contrat domaine) + `AndroidLocationEngine` (adaptateur Android) ;
  - `TripMeterViewModel` (état UI, routage des événements) ;
  - mapper UI (`TripDisplayMapper`) et écran Compose pur (`TripMeterScreen` / `TripMeterRoute`).
- CI GitHub Actions : un job Linux, JDK 21, `gradle/actions/setup-gradle`, exécutant `testDebugUnitTest assembleDebug`.

## 3. Diagnostic synthétique

La **boucle GPS runtime est encore inerte**. Précisément :

- l'événement `TripMeterUiEvent.ApplyLocationSample` **existe** ;
- le `TripMeterViewModel` **sait le traiter** (`applyLocationEngineSample` : rafraîchit le statut GPS depuis le moteur, accumule la distance via le `TripProgressEngine`) ;
- `AndroidLocationEngine` **stocke réellement des samples** (cache alimenté par `requestLocationUpdates`, commit `cc76865`) ;
- **mais aucun pump runtime ne déclenche périodiquement `ApplyLocationSample`** : un grep sur `app/src/main` ne le trouve que dans sa définition et dans le `when` du ViewModel.

Conséquence : au runtime, le statut GPS reste figé à sa valeur de bootstrap et la distance ne bouge pas depuis le vrai GPS. Le bouton `SimulateLocationStep` (« SIM GPS ») reste la **seule source de mouvement observable** hors patch.

## 4. Composants actuels

| Composant | Statut |
|---|---|
| `domain/model` (`TripState`, `TripSessionState`, `GpsStatus`) | Solide, testé. |
| `domain/controller` (`TripController` + `ImmutableTripController`) | Solide, testé. Cycle start/pause/resume/stop conforme au contrat. |
| `domain/distance` (`DistanceEngine` + `HaversineDistanceEngine`) | Solide, testé. Calcul **naïf**, sans validation de cohérence. |
| `domain/progress` (`TripProgressEngine` + `DistanceTripProgressEngine`) | Solide, testé. Accumule seulement si `Running`, ignore premier sample. **Pas de garde de cohérence** (invariant « points incohérents ignorés » non couvert). |
| `android/location` (`AndroidLocationEngine`, `AndroidLocationSampleMapper`) | Mapper testé JVM. Moteur réel **branché mais jamais validé sur device** (non testable en JVM sans Robolectric, écarté). |
| `ui/viewmodel` (`TripMeterViewModel`, factory) | Solide. Garde permission au `start` correcte. **Boucle pull jamais déclenchée** (pas de pump). |
| `ui/mapper` (`TripDisplayMapper`) | Fonctionnel mais **non testé**. `speedText` et `gpsAccuracyText` encore en placeholder. |
| `ui/screen` (`TripMeterRoute`, `TripMeterScreen`) | Solide. Lifecycle localisation câblé (`ON_START/ON_STOP`). **Pump et keep-screen-on manquants.** |

Dette mineure notée : la référence de distance (`previousLocationSample`) vit dans le ViewModel, alors que le contrat la situe dans l'état métier sous souveraineté `DistanceEngine`/`TripController`. Non bloquant.

## 5. Risques identifiés

- **GPS réel non validé sur device** : `AndroidLocationEngine` n'a jamais tourné en conditions réelles ; aucune couverture instrumentée.
- **Jitter / distance parasite** : sans garde de cohérence, le GPS réel produira de la distance fausse à l'arrêt et des sauts au retour de masquage.
- **Permission runtime incomplète** : pas de demande in-app ; sur un install neuf, aucune popup, le GPS ne démarre jamais. Le cas « permission accordée après coup via les réglages » fonctionne déjà via le refresh `ON_START`.
- **Absence de foreground service** : l'acquisition s'arrête en arrière-plan (`ON_STOP`). Acceptable pour un premier test écran allumé, incompatible avec une vraie étape.
- **Écran verrouillé / arrière-plan** non encore traités.
- **Divergence assumée** entre le MVP **pull** actuel (`getGpsStatus` / `getLastLocationSample` sur `LocationManager`) et la **cible contractuelle** plus ambitieuse (`locationengine_v0_1` : push + événements + watchdog + service + `FusedLocationProviderClient`). Le présent plan est un pont MVP, pas la cible.
- **Mapper UI partiellement non testé** (`TripDisplayMapper`).

## 6. Plan d'action par paliers

Chaque palier est atomique, committable séparément, compatible CI, et se termine par `./gradlew testDebugUnitTest assembleDebug` vert.

### Palier 1 — `feat(location): pump location samples while route is started`
- **Objectif** : déclencher périodiquement `ApplyLocationSample` tant que la route est au premier plan (STARTED), cadence ~1 s ; arrêt automatique hors STARTED.
- **Fichiers** : `ui/screen/TripMeterRoute.kt`.
- **Risque** : cadence/batterie ; pump non borné au lifecycle.
- **Tests / vérifications** : aucun test JVM (UI/lifecycle, non unitaire) ; `testDebugUnitTest assembleDebug` vert ; validation device (statut `GPS ?` → `RECHERCHE` → `OK`, distance qui bouge).
- **Commit** : `feat(location): pump location samples while route is started`.

### Palier 2 — `feat(permission): request fine location at route entry`
- **Objectif** : demander `ACCESS_FINE_LOCATION` à l'entrée de la route (`rememberLauncherForActivityResult`) puis rafraîchir l'état, pour qu'un install neuf puisse accorder et démarrer le GPS.
- **Fichiers** : `ui/screen/TripMeterRoute.kt` (+ éventuel helper).
- **Risque** : gestion du refus / « ne plus demander » ; ne pas dupliquer la garde existante du ViewModel.
- **Tests / vérifications** : aucun JVM ; validation device.
- **Commit** : `feat(permission): request fine location at route entry`.

### Palier 3 — `feat(ui): keep screen on during active session`
- **Objectif** : maintenir l'écran allumé pendant une session Active (invariant fonctionnel §6, et confort de test en roulage).
- **Fichiers** : `ui/screen/TripMeterScreen.kt`.
- **Risque** : faible.
- **Tests / vérifications** : UI / manuel ; compilation.
- **Commit** : `feat(ui): keep screen on during active session`.

### Palier 4 — `test(mapper): cover current trip display formatting`
- **Objectif** : filet de caractérisation sur `TripDisplayMapper` **avant** de l'étendre (formatage km, mapping statut GPS, mapping session).
- **Fichiers** : `app/src/test/java/fr/arsenal/rallyetripmeter/ui/mapper/TripDisplayMapperTest.kt` (nouveau).
- **Risque** : nul.
- **Tests / vérifications** : tests JVM ajoutés ; `testDebugUnitTest` vert.
- **Commit** : `test(mapper): cover current trip display formatting`.

### Palier 5 — `feat(progress): ignore noise and implausible jumps`
- **Objectif** : garde de cohérence minimale — plancher de bruit (~2 m) et rejet d'un delta si la vitesse implicite (distance / Δt via `timestampMillis`) dépasse un plafond plausible (~200 km/h). Sous-ensemble assumé du contrat fonctionnel §8, **sans** modèle `REFERENCE_ONLY` / discontinuité.
- **Fichiers** : domaine progression (`DistanceTripProgressEngine`) ou filtre pur dédié.
- **Risque** : seuils arbitraires (constantes nommées), à garder conservateurs.
- **Tests / vérifications** : tests JVM (rejet saut, rejet bruit, acceptation normale).
- **Commit** : `feat(progress): ignore noise and implausible jumps`.

### Palier 6 — `feat(display): surface gps accuracy from last sample`
- **Objectif** : afficher la précision réelle (`±X m`) issue du dernier sample, au lieu du placeholder `null`.
- **Fichiers** : `domain/model/TripState.kt` (champ pur `accuracyMeters`), chemin d'application du sample (ViewModel), `ui/mapper/TripDisplayMapper.kt`.
- **Risque** : faible ; champ de données pur ajouté au domaine (pas de logique Android).
- **Tests / vérifications** : tests JVM mapper.
- **Commit** : `feat(display): surface gps accuracy from last sample`.

### Palier 7 — `feat(display): surface instantaneous speed from gps`
- **Objectif** : afficher la vitesse instantanée native si présente, sinon `—` (pas de dérivation, pour rester minimal). Remplace le placeholder `0 km/h`.
- **Fichiers** : `domain/model/TripState.kt` (champ pur `speedKmh`), `ui/mapper/TripDisplayMapper.kt`.
- **Risque** : vitesse native absente sur certains fix → fallback explicite.
- **Tests / vérifications** : tests JVM mapper.
- **Commit** : `feat(display): surface instantaneous speed from gps`.

### Palier 8 (optionnel, au choix)
- **Objectif** : soit un coefficient de calibration global (`distance × coefficient`, contrat fonctionnel §7.2) ; soit l'upload d'un artefact APK debug en CI pour récupérer le build depuis le téléphone.
- **Fichiers** : domaine (calibration, pur et testable) **ou** `.github/workflows/android-ci.yml` (artefact).
- **Risque** : calibration = touche le domaine (data pur) ; artefact = YAML uniquement.
- **Tests / vérifications** : JVM si calibration ; n/a si artefact.
- **Commit** : `feat(domain): apply global calibration coefficient` **ou** `ci: upload debug apk artifact`.

## 7. Première étape recommandée : le pump (Palier 1)

Le pump est le premier palier parce qu'il est :

- le **plus petit changement observable** : un seul fichier (`TripMeterRoute.kt`), un effet périodique borné au lifecycle ;
- **sans changement domaine** ;
- **sans changement ViewModel** normalement requis (le traitement de `ApplyLocationSample` existe déjà) ;
- celui qui **transforme la plomberie GPS actuelle en boucle runtime** réellement testable sur appareil — préalable indispensable à toute validation de `AndroidLocationEngine`, et donc à tous les paliers runtime suivants.

## 8. Critères de validation

- `./gradlew testDebugUnitTest assembleDebug` vert localement.
- CI GitHub Actions verte après push (déclenchée sur `push` et `pull_request` vers `main`).
- **Validation device** pour les paliers runtime non couverts par les tests JVM (pump, permission, keep-screen-on, comportement GPS réel) : exécution sur appareil/émulateur, vérification visuelle du statut GPS et de l'accumulation de distance.

## 9. Hors périmètre v0.1

- foreground service (acquisition écran verrouillé / arrière-plan) ;
- persistance (sauvegarde de session, compteurs, calibration) ;
- publication Play Store ;
- calibration avancée (au-delà d'un coefficient global simple) ;
- refonte complète de `LocationEngine` vers le modèle contractuel push + événements ;
- migration vers `FusedLocationProviderClient` ;
- modèle complet discontinuité / watchdog / `REFERENCE_ONLY` (`locationengine_v0_1` §7–§8).

Ces éléments sont des chantiers contractuels à part entière, à planifier après la première validation device de la boucle runtime.
