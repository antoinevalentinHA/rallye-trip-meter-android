# Clôture de chantier — P3 terminé, reprise P4

Document de clôture du chantier P3 (refactor neutre de propriété d'ancre) et de
préparation de P4 (correction contrôlée de l'avancement d'ancre sur rejet).
Aucun code P4 n'est écrit ici : ce document décrit l'état réel du dépôt après
P3.b et le périmètre de reprise.

Documents amont :
- Plan d'ensemble : `docs/plan/plan_action_filtrage_gps_P1_P6_2026_06_11.md`
- Pivot P2 → P3 : `docs/plan/reprises/cloture_chantier_P2_reprise_P3_2026_06_11.md`

## 1. État du dépôt à l'instant T

- Branche : `main`.
- HEAD courant : `2525056`.
- Arbre de travail : propre (`git status --porcelain` vide).
- CI GitHub Actions : verte après P3.b.
- Validation locale PC (rapportée) : `./gradlew :app:testDebugUnitTest` et
  `./gradlew assembleDebug` verts.
- Corpus de replay inchangé dans `app/src/test/resources/replay/`.
- Schéma JSONL inchangé : `TickLogJsonl.SCHEMA_VERSION = 1`.

## 2. Commits concernés

| Commit | Message | Empreinte |
|---|---|---|
| `ad437e5` | `refactor(gps): introduce accumulation filter contract` | 5 fichiers, +383 / −1 |
| `2525056` | `refactor(gps): route runtime through accumulation filter` | 5 fichiers, +259 / −80 |

`ad437e5` est P3.a, `2525056` est P3.b. Aucun autre commit applicatif n'est
intervenu entre les deux paliers.

## 3. Ce que P3.a a livré (`ad437e5`)

Contrat d'accumulation et types d'état, sans toucher au runtime.

Fichiers créés / modifiés :
- `app/src/main/java/fr/arsenal/rallyetripmeter/domain/progress/GpsAccumulationFilter.kt`
  — contrat : `fun apply(tripState: TripState, filterState: FilterState, currentSample: LocationSample): FilterResult`.
- `app/src/main/java/fr/arsenal/rallyetripmeter/domain/progress/FilterState.kt`
  — `data class FilterState(val anchor: LocationSample? = null)` (v0 : porte uniquement l'ancre).
- `app/src/main/java/fr/arsenal/rallyetripmeter/domain/progress/FilterResult.kt`
  — `data class FilterResult(val state: TripState, val verdict: SampleVerdict, val nextState: FilterState)`.
- `app/src/main/java/fr/arsenal/rallyetripmeter/domain/progress/DistanceTripProgressEngine.kt`
  — implémente désormais `GpsAccumulationFilter` ; `apply()` délègue à
  `applyLocationSampleWithVerdict` (gardes, ordre, constantes inchangés) et
  retourne `nextState = FilterState(anchor = currentSample)` de façon
  inconditionnelle.
- `app/src/test/java/fr/arsenal/rallyetripmeter/domain/progress/GpsAccumulationFilterContractTest.kt`
  — test contractuel (parité verdict/état par branche + sémantique d'ancre).

Aucun changement de comportement runtime, de schéma JSONL, de constante ou de
golden en P3.a.

## 4. Ce que P3.b a livré (`2525056`)

Basculement du runtime sur le contrat, sans changement de comportement observable.

Fichiers modifiés / créés :
- `app/src/main/java/fr/arsenal/rallyetripmeter/runtime/TripRuntime.kt`
  — suppression du champ direct `previousLocationSample` ; ajout de
  `filterState: FilterState` ; paramètre `gpsAccumulationFilter: GpsAccumulationFilter`
  (au lieu de `progressEngine: TripProgressEngine`) ; `ApplyLocationSample` et
  `SimulateLocationStep` passent tous deux par `gpsAccumulationFilter.apply(...)` ;
  l'ancre n'avance que via `filterState = result.nextState`.
- `app/src/main/java/fr/arsenal/rallyetripmeter/ui/viewmodel/TripMeterViewModel.kt`
  — câblage DI uniquement (`gpsAccumulationFilter` forwardé au runtime), aucune
  logique modifiée.
- `app/src/test/java/fr/arsenal/rallyetripmeter/runtime/TripRuntimeFilterStateTest.kt`
  — nouveau test de neutralité P3.b.
- `app/src/test/java/fr/arsenal/rallyetripmeter/runtime/TripRuntimeTest.kt`
  — adaptation mécanique du fake au contrat `GpsAccumulationFilter`, assertions
  inchangées.
- `app/src/test/java/fr/arsenal/rallyetripmeter/ui/viewmodel/TripMeterViewModelTest.kt`
  — même adaptation mécanique du fake.

Décision explicite : **le schéma JSONL v2 n'a pas été introduit en P3.b**. Le
cutover runtime émet exactement les mêmes champs (`prev_ts_ms` trace déjà
l'ancienne ancre) ; v2 (`anchor_*`, `machine_state`) aurait modifié le JSONL émis
et impliqué les goldens pour aucun gain de neutralité. `machine_state` relève par
ailleurs de la machine P4. v2 est donc reporté en P4.

## 5. Invariants encore valides

- Constantes du moteur intactes : `NOISE_FLOOR_METERS = 2.0`,
  `ACCURACY_FLOOR_FACTOR = 1.0`, `STATIONARY_SPEED_MPS = 0.5`,
  `MAX_PLAUSIBLE_SPEED_KMH = 200.0`.
- I11 : chaque tick reçoit exactement un verdict explicable.
- `IGNORED_NO_SAMPLE` et `IGNORED_DUPLICATE` restent des verdicts de niveau
  runtime : ils court-circuitent avant le filtre et **ne font pas avancer
  l'ancre**.
- Écrivain unique de l'ancre : c'est désormais le filtre (via `nextState`), plus
  jamais le runtime — qui ne lit `filterState.anchor` que pour la déduplication
  de cache.
- Séparation des couches Perception / Décision / Exécution préservée.
- Schéma JSONL v1 stable (`SCHEMA_VERSION = 1`), rétrocompatible avec le corpus.

## 6. Preuve de neutralité

`app/src/test/java/fr/arsenal/rallyetripmeter/replay/TickLogPipelineReplay.kt`
réinjecte chaque tick du corpus dans un **vrai `TripRuntime`** (chemin P3.b réel),
et la comparaison porte sur le total et les comptages de verdicts.

Tests de replay verrouillant la neutralité
(`app/src/test/java/fr/arsenal/rallyetripmeter/replay/TickLogReplayHarnessTest.kt`) :
- `syntheticCouchFixture_matchesPinnedGoldens` : 900 ticks, 29 segments acceptés,
  310 `REJECTED_STATIONARY`, 560 `REJECTED_NOISE`, total 65.25 m (goldens pinnés).
- `syntheticCouchFixture_replaysFaithfullyThroughRealPipeline` : `totalsMatch`
  et `verdictsMatch` à travers le runtime réel.
- `renderText_isDeterministic` : rendu déterministe.

`app/src/test/java/fr/arsenal/rallyetripmeter/replay/RealLogReplayTest.kt`
auto-découvre les `real_*.jsonl` du corpus et vérifie l'invariant structurel
(somme des verdicts = nombre de ticks).

Ces tests traversent le chemin modifié en P3.b et restent verts : le comportement
observable est conservé.

## 7. Tests qui verrouillent le comportement

Contrat filtre — `GpsAccumulationFilterContractTest` :
- `apply_withoutAnchor_mirrorsIgnoredNoAnchor`,
  `apply_whenStopped_mirrorsIgnoredNotRunning`,
  `apply_whenStationarySpeed_mirrorsRejectedStationary`,
  `apply_whenBelowNoiseFloor_mirrorsRejectedNoise`,
  `apply_whenImplausibleJump_mirrorsRejectedImplausibleJump`,
  `apply_whenPlausibleMovement_mirrorsAcceptedSegment` (parité verdict + état).
- `apply_advancesAnchorEvenWhenSampleRejected`,
  `apply_advancesAnchorOnAcceptedSegment`,
  `apply_fromEmptyState_setsAnchorAndIgnoresNoAnchor`,
  `apply_transportedState_accumulatesAcrossTicks` (sémantique d'ancre).

Neutralité runtime — `TripRuntimeFilterStateTest` :
- `applyAndSimulate_bothRouteThroughTheSameFilter` (chemin filtre unique).
- `rejectedSample_stillAdvancesAnchor_carriedByFilterState` (ancre sur rejet).

Observabilité / verdicts runtime — `TripRuntimeTest` (sélection) :
- `applyLocationSample_firstSample_logsIgnoredNoAnchor`,
  `applyLocationSample_acceptedByEngine_logsEngineVerdictAndDelta`,
  `applyLocationSample_rejectedByEngine_logsEngineVerdict_andKeepsDistances`,
  `applyLocationSample_onSameFixReread_logsIgnoredDuplicate_andKeepsDistances`,
  `applyLocationSample_withoutSample_logsIgnoredNoSample_andKeepsDistances`,
  `applyLocationSample_emitsExactlyOneEntryPerTick`,
  `applyLocationSample_repeatedTicksOnSameFix_doNotDoubleAccumulate`.

## 8. Description explicite du bug restant

L'ancre avance **même lorsqu'un échantillon est rejeté**.

- Localisation : `DistanceTripProgressEngine.apply()`,
  `nextState = FilterState(anchor = currentSample)` (≈ ligne 124), inconditionnel
  quel que soit le verdict.
- Mécanisme : sur `REJECTED_STATIONARY`, `REJECTED_NOISE` ou
  `REJECTED_IMPLAUSIBLE_JUMP`, l'ancre passe quand même à l'échantillon courant.
  Le segment suivant est alors mesuré depuis l'échantillon rejeté, et non depuis
  la dernière ancre légitimement retenue.
- Effet observable : crédit possible de distance « fantôme » à travers un point
  rejeté (source documentée de la dérive canapé).
- Statut : reproduit à l'identique en P3.a et P3.b, par décision de palier. Figé
  par les tests sentinelles `apply_advancesAnchorEvenWhenSampleRejected` et
  `rejectedSample_stillAdvancesAnchor_carriedByFilterState`.

## 9. Pourquoi ce bug n'a pas été corrigé en P3

P3 est, par mandat, un **refactor neutre** (plan-maître §6 « Détail P3 », pivot
P2 → P3 §4) : transférer la propriété de l'ancre vers le filtre via `FilterState`,
sans changer le comportement, avec preuve par replay bit-à-bit. Corriger l'ancre
modifierait l'accumulation, donc :
- casserait la preuve de neutralité (les goldens et la comparaison de replay) qui
  est précisément la raison d'être de l'ordre P2 → P3 ;
- empièterait sur P4, désigné par le plan-maître comme « le seul palier qui change
  l'accumulation ».

La règle de séquencement est volontaire : poser et prouver le refactor neutre sur
une base stable, puis mesurer la correction comportementale contre cette base.

## 10. Périmètre recommandé pour P4

Objectif : **inverser l'avancement d'ancre sur rejet**, avec preuve par replay et
tests dédiés. Le plan-maître (§7 « Détail P4 ») et le pivot prévoient :

- Point de bascule unique côté logique : le calcul de `nextState` dans
  `DistanceTripProgressEngine.apply()` (ou une nouvelle implémentation
  `StationaryAwareAccumulationFilter` remplaçant l'implémentation, l'ancienne
  restant dans l'historique git). Invariant cible : tout verdict `REJECTED_*`
  laisse `FilterState` (ancre comprise) inchangé.
- Enrichissement de `FilterState` (ancre qualifiée, état machine
  STATIONNAIRE / MOUVEMENT, fenêtre de retour) — prévu, non encore existant.
- Extension de `SampleVerdict` prévue par le plan : `ACCEPTED_NET_ON_TRANSITION`,
  `REJECTED_STALE`, `REANCHORED_AFTER_GAP`.
- Schéma JSONL v2 (champs d'ancre + `machine_state`) reporté de P3, à introduire
  ici en conservant la lecture v1.
- Inversion (et non suppression) des tests sentinelles
  `apply_advancesAnchorEvenWhenSampleRejected` et
  `rejectedSample_stillAdvancesAnchor_carriedByFilterState`, avec commentaire de
  traçabilité.
- Le plan-maître propose un sous-séquencement : **P4.a** extraction d'un objet de
  constantes à comportement constant (`FilterTuning`), puis **P4.b** la machine et
  la correction avec mise à jour justifiée des goldens.

Non concernés (le contrat P3 absorbe le changement) : `TripRuntime`,
`HaversineDistanceEngine`, `TripMeterForegroundService`, persistance, UI.

## 11. Risques P4

- Modification d'accumulation → mise à jour **inévitable** des goldens : chaque
  golden ne se met à jour qu'avec justification écrite par fichier (pas de
  correction par coefficient, doctrine I10).
- Inversion des tests sentinelles : risque de masquer une régression si faite sans
  miroir explicite ; chaque inversion doit porter un commentaire de traçabilité.
- Conditions terrain non reproductibles : chaque sortie devient un replay
  permanent ; un échec terrain = un nouveau fichier de corpus + retour ciblé, pas
  un correctif au feeling.
- Introduction de la machine d'état : préserver la séparation des couches et ne
  pas faire fuiter d'état vers le runtime (le transport opaque de `FilterState`
  est déjà en place).

## 12. Critères d'acceptation P4

- Invariant inversé vérifié : tout `REJECTED_*` laisse `FilterState` inchangé.
- Replay corpus, double condition (plan-maître §7, M1 / M2) : logs d'arrêt →
  total ≤ M1 (10 m / 15 min) ; logs de mouvement → écart ≤ 2 % par rapport au
  total P3 (et au GPX de référence quand disponible).
- Goldens mis à jour avec justification écrite par fichier.
- Suite complète verte ; APK compilé (validation lourde assurée par la CI).
- Schéma JSONL v2 lisible et rétrocompatible v1.

## 13. Commandes de validation

Audit léger (local, sans build) :

```bash
git rev-parse --short HEAD          # attendu : 2525056 (avant P4)
git log -3 --format="%h %s"
git status --porcelain              # attendu vide
```

Application du présent patch documentaire :

```bash
git apply --check cloture_p3_reprise_p4.patch
git apply       cloture_p3_reprise_p4.patch
```

Aucune commande Gradle n'est requise sur Termux pour cette clôture documentaire.
La validation lourde (`:app:testDebugUnitTest`, `assembleDebug`, replay) reste
assurée par GitHub Actions sur le push.

## 14. Prochain chantier recommandé

P4 — correction contrôlée de l'avancement d'ancre sur rejet. Commencer par
**P4.a** (extraction des constantes à comportement constant), puis **P4.b** (la
correction proprement dite + machine d'état + goldens justifiés), conformément au
plan-maître §8 « Ordre de patchs ».

---

P3 est clôturé. Le prochain chantier recommandé est P4 : correction contrôlée de
l'avancement d'ancre sur rejet, avec preuve par replay et tests dédiés.

## Recalage P4 (2026-06-12)

Le découpage « P4.b = correction isolée de l'ancre » a été **invalidé** après
simulation fidèle : geler l'ancre sur rejet, sans logique stationnaire/mouvement,
**aggrave** les distances fantômes sur les trois corpus d'arrêt. Voir
`recalage_P4_anchor_only_invalide_2026_06_12.md`. P4 redevient un chantier unique
de machine stationnaire/mouvement.
