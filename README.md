# rallye-trip-meter-android

Android rally trip meter with GPS distance tracking, stage distance, total distance and manual calibration.

Application Android (Kotlin / Jetpack Compose, package `fr.arsenal.rallyetripmeter`)
de **trip meter de rallye orienté navigation au roadbook** : compteur partiel
remis à zéro et corrigé au mètre, kilométrage total, vitesse instantanée. Ce n'est
pas un roadbook numérique ni une carte de navigation (cf. contrat fonctionnel).

## État actuel

- **MVP validé sur appareil** : acquisition GPS runtime, foreground service,
  permissions, pause/reprise, persistance de session.
- **Filtre anti-dérive (série P1→P4) clôturé fonctionnellement** : machine
  stationnaire/mouvement neutralisant la dérive à l'arrêt sans dégrader la mesure
  en mouvement. Contrat : `docs/contrats/gps_accumulation_filter_v0_1.md`.
- **À venir** : P5 (calage fin des constantes internes du filtre par replay).

## Doctrine

- **Contract-first** : les documents de `docs/contrats/` sont la référence ; le
  code s'y conforme.
- **Mesure brute ⟂ correction d'affichage** : le filtre GPS produit une distance
  brute ; le coefficient utilisateur de calibration n'est appliqué qu'à
  l'affichage. Les deux étages sont étanches.

## Carte du dépôt

| Chemin | Contenu |
|---|---|
| `app/src/main/java/fr/arsenal/rallyetripmeter/` | Code applicatif (domaine pur, runtime, UI Compose, adaptateurs Android). |
| `app/src/test/` | Tests JVM (domaine, runtime, mappers) et corpus de replay GPS. |
| `docs/contrats/` | **Contrats normatifs** (fonctionnel, TripState, TripController, LocationEngine, DistanceEngine, TripDisplay, filtre d'accumulation GPS). |
| `docs/plan/` | Plans, audits et validations (non normatifs). |
| `docs/plan/reprises/` | Reprises de chantier (clôtures P2→P4, design, captures). |
| `docs/features/` | Idées de fonctionnalités (ex. trace rallye OSM), non planifiées. |

## Build & tests

```bash
./gradlew testDebugUnitTest assembleDebug
```

CI : GitHub Actions (`.github/workflows/android-ci.yml`) exécute la même commande
sur `push` et `pull_request`. minSdk 28, targetSdk 36.
