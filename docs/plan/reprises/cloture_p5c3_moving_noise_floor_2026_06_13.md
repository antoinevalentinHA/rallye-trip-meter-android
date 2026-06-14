# Clôture P5.c-3 étape A — plancher de bruit réduit en MOVING

> **Nature** : document de clôture, non normatif. Il consigne l'étape A de P5.c-3
> (correction basse vitesse). **Aucun code, test, JSONL ou seuil modifié par ce
> document.** La clôture est **actée** : la validation verte est acquise (voir §4).

## 1. Contexte

- **P4.2** avait corrigé la dérive à l'arrêt (gate stationnaire) mais **cassait la
  marche lente** : le plancher de bruit fixe de 2 m rejette les pas piétons ~1,2 m à
  1 Hz (`cloture_chantier_P4_gate_stationnaire_2026_06_12.md`).
- **P5.c-1** a établi la baseline du moteur P4.2 sur le corpus minimal
  (`p5c1_baseline_corpus_minimal_2026_06_13.md`) : arrêt 0 m, route −1,76 %, urbain
  −2,7 %, marche **−89,1 %**, verdict dominant `REJECTED_NOISE` 510/580.
- **P5.c-2** a comparé les options (`p5c2_exploration_options_basse_vitesse_2026_06_13.md`).
- **P5.c-3** a décidé une **correction conservatrice MOVING-only**
  (`p5c3_decision_impl_basse_vitesse_2026_06_13.md`), Option A, en écartant la valeur
  de crossover fragile (~1,2 m) et l'option E (différée).

## 2. Résumé du patch moteur

Commit `5e0d8f3` — `fix(gps): use lower noise floor while moving` :

- **Ajout** de `movingNoiseFloorMeters = 1.4` dans `FilterTuning`.
- **Conservation** de `noiseFloorMeters = 2.0` (comportement historique).
- Plancher réduit **uniquement en état MOVING** (mouvement confirmé), sur la branche
  vitesse-présente : la primitive reçoit un drapeau `moving`, vrai seulement depuis
  `applyWhileMoving`.
- **Crédit de départ STATIONARY→MOVING laissé prudent** (`moving = false` → plancher
  2 m) : le gate n'est pas affaibli.
- **Branche vitesse-absente inchangée** : `max(noiseFloorMeters, accuracy × facteur)`.
- **Aucun changement JSONL/golden.** Contrat filtre mis à jour (plancher dépendant
  de l'état, `I-AVANCE-MOUV` inchangé).

## 3. Résultats avant/après (replay du moteur, corpus minimal)

| Régime | Avant (2,0) | Après (1,4) | Réf |
|---|---:|---:|---|
| Arrêt M1 (×3) | 0,00 m | **0,00 m** | 0 (cible) |
| Route | 6680 m (−1,8 %) | 6703 m (−1,4 %) | 6800 m |
| Urbain | 7976 m (−2,7 %) | 7988 m (−2,6 %) | 8200 m |
| Marche lente | 57,88 m (−89,1 %) | **~300 m (−43,7 %)** | 533 m |

Amélioration marche ≈ **×5,2**, **hors de la zone −60 %**, **sans sur-comptage** ;
arrêt, route et urbain tenus. (Chiffres reproduits par replay hors-dépôt du moteur
poussé ; confirmation autoritative = exécution Gradle / CI, §4.)

## 4. Résultats de validation

- **Commandes à exécuter** (validation autoritative, côté mainteneur) :
  - `./gradlew :app:testDebugUnitTest --tests "fr.arsenal.rallyetripmeter.domain.progress.*"`
  - `./gradlew :app:testDebugUnitTest --tests "fr.arsenal.rallyetripmeter.replay.*"`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `git diff --check` ; `git status --porcelain`
- **Statut tests locaux** : **OK** — `./gradlew :app:testDebugUnitTest` et
  `./gradlew assembleDebug` exécutés verts par le mainteneur en fin de session
  (validation côté poste outillé, hors environnement de préparation).
- **Statut CI GitHub Actions** : **verte** — workflow `android-ci.yml`
  (`testDebugUnitTest` + `assembleDebug`) au vert pour le HEAD de clôture `6cfadf6`.
- **Vérifications statiques faites lors de la préparation** : périmètre (aucun JSONL,
  aucun golden), `git diff --check` propre, `git apply --check` OK, accolades et sites
  d'appel cohérents.

> **Ces validations étant vertes (Gradle local + CI), la clôture est effective.**

## 5. Garanties conservées

- **Gate stationnaire** : inchangé (crédit de départ laissé à 2 m).
- **Arrêt à 0 m** : tenu sur les trois logs M1.
- **Invariant `I-AVANCE-MOUV`** : conservé (l'ancre avance sur rejet en MOVING).
- **Verdicts existants** : ensemble fermé inchangé (aucun nouveau verdict).
- **JSONL v1** : format inchangé.
- **Pas de carte, pas d'UI, pas de refonte GPS.**

## 6. Limites assumées

- La marche lente **n'est pas parfaitement calibrée** (~−44 %, lecture ~300 m pour
  533 m).
- La correction reste **volontairement côté sous-comptage** : on **ne vise pas 533 m
  pile** (la valeur de crossover ~1,2 m serait un sur-ajustement à un seul log, sur
  une courbe raide).
- **L'option E n'est pas ouverte** tant qu'une **preuve réelle supplémentaire** (un
  second log basse vitesse référencé) ne montre pas qu'un plancher MOVING fixe est
  insuffisant.

## 7. Verdict

- **P5.c-3 étape A est clôturée** : tests locaux et CI verts (§4). La validation
  autoritative est **acquise**.
- Validation acquise, la machine GPS est désormais **plus équilibrée** :
  - **arrêt tenu** (0 m) ;
  - **route tenue** (−1,4 %) ;
  - **urbain surveillé** (−2,6 %) ;
  - **basse vitesse fortement améliorée** (−89 % → ~−44 %).

## 8. Suite recommandée

- **Ne pas ouvrir E immédiatement.**
- **Poursuivre le développement applicatif.**
- **Revenir à E uniquement** si un futur usage réel révèle une contradiction claire
  (un plancher MOVING fixe ne tenant plus les bornes sur plusieurs logs).

---

Clôture documentaire P5.c-3 étape A. Aucun code, test, JSONL ou seuil modifié ;
validation autoritative (Gradle local + CI) acquise pour le HEAD de clôture `6cfadf6`.
