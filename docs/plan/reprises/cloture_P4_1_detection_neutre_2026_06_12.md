# Clôture P4.1 — détection stationnaire/mouvement neutre

P4.1 ajoute l'état machine stationnaire/mouvement et sa détection dans le filtre,
**sans changer l'accumulation**. La machine observe et transporte son état ; elle
ne gouverne pas encore les distances (gate activé en P4.2).

Documents amont :
- `docs/plan/reprises/design_P4_machine_stationnaire_mouvement_2026_06_12.md`
- `docs/plan/reprises/audit_corpus_M1_M2_avant_P4_machine_2026_06_12.md`

## 1. État du dépôt

Branche `main`, HEAD `05341d5` avant ce patch ; arbre propre. Corpus : 6 logs
(3 arrêt, 3 mouvement dont 1 référencé odomètre 6.80 km).

## 2. Ce qui a été ajouté

- `MachineState` : énumération `STATIONARY` / `MOVING`.
- `FilterState` v1 : champs `machineState`, `stationaryCenter`, `movingStreak`,
  `stationaryStreak` (défauts reproduisant l'absence d'effet : STATIONARY, centre
  nul, compteurs nuls).
- `FilterTuning` : constantes de détection `movementTriggerMeters` (15.0),
  `stillnessRadiusMeters` (3.0), `detectionHysteresisSamples` (3). Hypothèses,
  sans effet sur l'accumulation.
- `DistanceTripProgressEngine.apply()` : calcule l'état machine via
  `detectMachineState(...)` et le transporte dans `nextState`. Détection par
  déplacement net (pas par vitesse seule) avec hystérésis : STATIONARY → MOVING
  après 3 échantillons consécutifs à plus de 15 m du centre fixe ; MOVING →
  STATIONARY après 3 pas consécutifs sous 3 m.

## 3. Ce qui n'a volontairement PAS changé

- L'accumulation : `apply()` calcule le verdict et l'état métier comme en P4.a
  (`applyLocationSampleWithVerdict` sur `filterState.anchor`), et l'ancre avance
  toujours à chaque échantillon (`nextState.anchor = currentSample`), y compris
  sur rejet — le bug d'ancre n'est pas corrigé ici (c'est P4.2).
- `TripRuntime` : inchangé ; reste transporteur opaque de `FilterState` (il porte
  les nouveaux champs sans les lire).
- `FilterTuning` : les 4 seuils d'accumulation historiques sont intacts.
- JSONL, goldens, UI, runtime Android GPS : intacts.

## 4. Preuve de neutralité

Les champs machine sont **write-only** en P4.1 : calculés et transportés, jamais
lus par le chemin d'accumulation (`applyLocationSampleWithVerdict`) ni par le
runtime. Verdicts et totaux sont donc inchangés par construction.

Confirmation chiffrée par replay fidèle (portage validé bit-à-bit) — totaux
strictement identiques à P4.a / à l'audit :

| Fichier | Total P4.a | Total P4.1 |
|---|---|---|
| `sample_canape_synthetique` | 65.250 m | 65.250 m |
| `real_canape_20260611` | 4.433 m | 4.433 m |
| `real_canape_20260611_2351` | 45.031 m | 45.031 m |
| `real_urbain_voiture_…140454` | 2119.80 m | 2119.80 m |
| `real_urbain_voiture_…142640` | 1822.07 m | 1822.07 m |
| `real_route_voiture_…odom_6_80km` | 6680.77 m | 6680.77 m |

Le log route référencé reste à **6680.77 m** (−1.75 % vs odomètre 6800 m). Les
goldens pinnés (`TickLogReplayHarnessTest` : 29 / 310 / 560 / 65.25 m) restent
valides sans modification.

## 5. Tests

- `FilterStateMachineDetectionTest` (nouveau) : initialisation STATIONARY +
  centrage ; transition STATIONARY → MOVING après hystérésis ; transition
  MOVING → STATIONARY après immobilité ; non-bascule sous le seuil ; **neutralité**
  (état machine n'altère ni verdict ni état métier ; ancre avance toujours, y
  compris sur rejet).
- Tests existants conservés (non modifiés) : `DistanceTripProgressEngineTest`,
  `GpsAccumulationFilterContractTest`, `FilterTuningTest`, `TripRuntimeTest`,
  `TripRuntimeFilterStateTest`, replays — tous restent verts (accumulation
  inchangée).

## 6. Validation

La validation lourde (`:app:testDebugUnitTest`, `assembleDebug`, replays) est
assurée par GitHub Actions après push. À exécuter localement :

```bash
./gradlew :app:testDebugUnitTest --tests "fr.arsenal.rallyetripmeter.replay.*"
./gradlew :app:testDebugUnitTest --tests "fr.arsenal.rallyetripmeter.domain.progress.*"
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
git diff --check
git status --porcelain
```

## 7. Prochain chantier recommandé

**P4.2 — gate stationnaire actif** : exploiter `machineState` pour neutraliser
l'accumulation à l'arrêt (cible M1 ≤ 10 m / 15 min) tout en préservant le
mouvement (M2, route ≤ 2 % vs odomètre). Prérequis recommandé : capturer un log
de marche lente référencé pour borner la sous-accumulation avant de figer les
seuils de détection.

## 8. Conclusion

P4.1 ajoute l'état stationnaire/mouvement et sa détection sans changer
l'accumulation. Les replays restent bit-à-bit / numériquement identiques à P4.a.
P4.2 pourra activer le gate stationnaire avec une base observable et testée.
