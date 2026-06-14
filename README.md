# rallye-trip-meter-android

Android rally trip meter with GPS distance tracking, stage distance, total distance and manual calibration.

Application Android (Kotlin / Jetpack Compose, package `fr.arsenal.rallyetripmeter`)
de **trip meter de rallye orienté navigation au roadbook** : compteur partiel
remis à zéro et corrigé au mètre, kilométrage total, vitesse instantanée. Ce n'est
pas un roadbook numérique ni une carte de navigation (cf. contrat fonctionnel).

## État actuel

Tag courant : **`v0.3.0-preview`** (utilisable, validé sur appareil ; validation
terrain finale non encore faite).

Livré : trip meter fonctionnel, runtime GPS foreground, filtre anti-dérive,
observabilité JSONL, export GPX a posteriori, mode paysage, relecture locale de
trace en polyligne. Non encore : validation terrain (P6.a), précision certifiée,
vraie carte, Play Store, Android Auto.

Détail vivant (capacités, limites, chantiers ouverts, prochaine étape) :
**[`docs/ETAT.md`](docs/ETAT.md)**.

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

## Licence

Ce projet est distribué sous **GNU General Public License v3.0 ou ultérieure**
(`GPL-3.0-or-later`). Texte intégral : [`LICENSE`](LICENSE).

En pratique, cela te donne la liberté d'**utiliser**, d'**étudier**, de
**modifier** et de **redistribuer** le logiciel. En contrepartie (copyleft) : toute
version **redistribuée**, y compris un APK, doit l'être **sous la même licence** et
avec son **code source** disponible. Le code ne peut donc pas être repris dans un
produit **propriétaire fermé**.

Copyright (C) 2026 Antoine VALENTIN.

## Contributions

Les contributions sont **bienvenues**. Le projet est maintenu principalement par son
auteur, sans bureaucratie inutile :

- **pas de CLA** ; tu conserves ton copyright ;
- ta contribution est acceptée **sous la licence du projet** (`GPL-3.0-or-later`,
  inbound = outbound) ;
- un **DCO** (Developer Certificate of Origin) léger est requis : chaque commit est
  signé via `git commit -s`.

Détails (workflow PR, DCO, exemple de signature) : [`CONTRIBUTING.md`](CONTRIBUTING.md).
