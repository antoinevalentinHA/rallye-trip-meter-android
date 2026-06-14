# Clôture P6.b — purge du vestige `calibrationFactor` du moteur brut

> **Navigation** — [← index des reprises](README.md) ·
> Actualise : [`distanceengine_v0_1_2.md`](../../contrats/distanceengine_v0_1_2.md),
> [`gps_accumulation_filter_v0_1.md`](../../contrats/gps_accumulation_filter_v0_1.md) ·
> [état courant](../../ETAT.md)

> **Nature** : document de clôture, non normatif. Il consigne le chantier P6.b
> (purification de la mesure brute). **Aucun code, test, JSONL ou seuil modifié par
> ce document** : le changement de code est porté par le commit `4e1e350`, déjà
> poussé et validé. La clôture est **actée** : la validation verte est acquise
> (voir §4).

## 1. Contexte

- Le moteur brut `DistanceTripProgressEngine` portait historiquement un paramètre
  interne `calibrationFactor: Double = 1.0`, multiplié à la distance retenue avant
  accumulation. Neutre à sa valeur par défaut, **non câblé** par `TripRuntime` ni
  par le ViewModel ; seul un test unitaire le positionnait à une autre valeur.
- Ce paramètre était un **vestige contraire à la doctrine moteur brut** (audit
  `audit_filtrage_gps_anti_derive_2026_06_11.md`, F6 ; plan directeur
  `plan_action_filtrage_gps_P1_P6_2026_06_11.md`, précision (a) et palier P6.b).
  Risque latent : qu'un futur câblage « corrige » la dérive par coefficient à
  l'intérieur du moteur, brouillant la frontière mesure brute / affichage (I-SEP).
- La purge était planifiée comme **étape P6.b** : suppression du paramètre et de la
  multiplication, suppression du test associé, neutralité prouvée par replay.

## 2. Résumé du patch moteur

Commit `4e1e350` — `refactor(engine): purge vestigial calibrationFactor from raw engine` :

- **Suppression** du paramètre de constructeur `calibrationFactor: Double = 1.0` de
  `DistanceTripProgressEngine`.
- **Suppression** de la multiplication `distance × calibrationFactor` : le moteur
  accumule désormais la **distance brute non corrigée**
  (`total += distance`, `partiel += distance`).
- **Suppression** du seul test qui validait ce vestige
  (`applyLocationSample_appliesCalibrationFactorToAccumulatedDistance`).
- Commentaire de doctrine du moteur mis à jour (accumulation brute ; correction
  utilisateur exclusivement à l'affichage).
- **Aucun seuil GPS, aucun JSONL, aucun golden, aucune UI touchés.**

## 3. Résultat : frontière mesure brute / affichage

Après `4e1e350`, la séparation des deux étages est **structurellement complète** :

- **Moteur brut** (`DistanceTripProgressEngine`) : accumule une distance **non
  corrigée par l'utilisateur**. Plus aucun mécanisme de correction utilisateur n'y
  subsiste.
- **Coefficient utilisateur de calibration** : appliqué **exclusivement à
  l'affichage**, via `ui/mapper/TripDisplayMapper` (`distance × coefficient` sur le
  texte affiché). Inchangé par ce chantier ; couvert par `TripDisplayMapperTest`
  (`withDefaultCalibration_leavesDistancesUnchanged`,
  `withCalibration_scalesPartialAndTotal`,
  `withCalibration_doesNotAffectSpeedOrGps`).

L'invariant **I-SEP** du contrat `gps_accumulation_filter_v0_1.md` (§0) est désormais
sans exception : le filtre n'applique jamais de coefficient utilisateur, et celui-ci
ne modifie jamais la distance brute.

## 4. Résultats de validation

Validation autoritative **acquise** côté mainteneur pour le commit `4e1e350` :

- **Tests locaux** : **OK** — `./gradlew.bat :app:testDebugUnitTest` et
  `./gradlew.bat :app:assembleDebug` verts.
- **CI GitHub Actions** : **verte** sur le commit `4e1e350` (workflow
  `android-ci.yml` : `testDebugUnitTest` + `assembleDebug`).
- **Preuve de neutralité** (replay du corpus réel, exécutée côté mainteneur) :
  - neutralité bit-à-bit avant/après sur tous les logs : **True** ;
  - reconstruction == total enregistré sur tous les logs : **True**.

La suppression est donc un **no-op comportemental** sur la mesure brute : à facteur
`1.0` (valeur de tous les appelants de production), `distance × 1.0 ≡ distance`
exactement en double IEEE-754 ; la branche supprimée ne modifiait aucune accumulation.

## 5. Garanties conservées

- **Seuils GPS** : inchangés (aucune valeur de `FilterTuning` touchée).
- **Machine STATIONARY / MOVING** : inchangée.
- **Verdicts** : ensemble fermé inchangé (aucun verdict ajouté ni retiré).
- **JSONL** : format et corpus inchangés ; goldens inchangés.
- **Calibration utilisateur** : intacte, côté affichage uniquement.
- **Pas de carte, pas d'UI, pas de refonte GPS.**

## 6. Mise en cohérence documentaire (ce document)

- **Contrat `gps_accumulation_filter_v0_1.md`** (normatif) mis en cohérence avec
  `4e1e350` : la note §0 acte la purge ; l'étape 7 de la primitive (§6) décrit une
  accumulation brute directe, sans `calibrationFactor`.
- **Documents historiques non touchés** (doctrine du dépôt : les documents datés de
  `docs/plan/` et `docs/plan/reprises/` consignent un état passé et ne sont pas
  réécrits) : `plan_action_filtrage_gps_P1_P6_2026_06_11.md`,
  `audit_filtrage_gps_anti_derive_2026_06_11.md`,
  `runtime_gps_v0_1_calibration_protocol.md`,
  `cloture_chantier_P2_reprise_P3_2026_06_11.md`. Leurs mentions de
  `calibrationFactor` décrivent l'état d'alors et la planification de la purge ;
  elles restent valides comme historique.

## 7. Verdict

- **P6.b est clôturé** : le vestige `calibrationFactor` est purgé du moteur brut
  (commit `4e1e350`), la neutralité est prouvée, les tests et la CI sont verts, et
  la documentation normative est en cohérence avec l'état réel.
- Le moteur brut produit désormais une **mesure brute pure** ; la correction
  utilisateur vit exclusivement à l'affichage. L'invariant **I-SEP** est tenu sans
  exception.

## 8. Suite recommandée

- **Ne pas ouvrir** de nouveau chantier technique par ce document.
- Le palier **P6.b est isolé** : il ne déclenche ni P6.a (campagne terrain finale +
  nouvelles traces de régression) ni P6.c (doctrine de clôture du filtre,
  emplacement de preuve des invariants), qui restent à engager séparément.
- L'**option E** (plancher MOVING non fixe) demeure fermée tant qu'un second log
  basse vitesse référencé ne montre pas que le plancher fixe est insuffisant.

---

Clôture documentaire P6.b. Aucun code, test, JSONL ou seuil modifié par ce document ;
le changement de code (`4e1e350`) et sa validation (Gradle local + CI verte + preuve
de neutralité) sont acquis côté mainteneur.
